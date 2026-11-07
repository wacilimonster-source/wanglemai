package com.wanglemai.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.wanglemai.app.data.db.AppDatabase
import com.wanglemai.app.data.db.WidgetConfig
import com.wanglemai.app.domain.DEFAULT_TODO
import com.wanglemai.app.domain.ItemRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class WidgetPinStatus { IDLE, REQUESTING, UNCONFIRMED, ADDED, UNSUPPORTED, ERROR }

data class WidgetPinState(
    val status: WidgetPinStatus = WidgetPinStatus.IDLE,
    val token: String? = null,
    val providerClass: String? = null,
    val widgetId: Int? = null
)

/** Confirms actual host instances, including launchers which omit the success callback. */
class WidgetPinCoordinator(
    context: Context,
    private val db: AppDatabase,
    private val repository: ItemRepository,
    private val widgets: WidgetUpdateCoordinator,
    private val scope: CoroutineScope
) {
    private val context = context.applicationContext
    private val manager = AppWidgetManager.getInstance(context)
    private val preferences = context.getSharedPreferences("widget_pin", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val providers = setOf(ListSmallProvider::class.java.name, ListLargeProvider::class.java.name)
    private val mutableState = MutableStateFlow(restore())
    val state = mutableState.asStateFlow()

    private fun restore(): WidgetPinState {
        val provider = preferences.getString("provider", null)?.takeIf { it in providers }
            ?: return WidgetPinState()
        val token = preferences.getString("token", null) ?: return WidgetPinState()
        val id = preferences.getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID)
            .takeIf { it > AppWidgetManager.INVALID_APPWIDGET_ID }
        return WidgetPinState(if (id != null) WidgetPinStatus.ADDED else WidgetPinStatus.UNCONFIRMED,
            token, provider, id)
    }

    fun request(provider: Class<out AppWidgetProvider>) {
        require(provider.name in providers)
        scope.launch {
            val token = UUID.randomUUID().toString()
            val accepted = mutex.withLock {
                if (mutableState.value.status == WidgetPinStatus.REQUESTING) return@launch
                // Before retrying, reconcile a late host update to avoid adding a duplicate.
                if (mutableState.value.status == WidgetPinStatus.UNCONFIRMED &&
                    mutableState.value.providerClass == provider.name && findNewWidgetLocked()) return@launch
                cancelCallbackLocked()
                preferences.edit(commit = true) { clear() }
                try {
                    if (!manager.isRequestPinAppWidgetSupported) {
                        mutableState.value = WidgetPinState(WidgetPinStatus.UNSUPPORTED, providerClass = provider.name)
                        return@withLock false
                    }
                    val component = ComponentName(context, provider)
                    val before = manager.getAppWidgetIds(component).map { it.toString() }.toSet()
                    // Persist on the app's IO scope before handing control to the launcher.
                    preferences.edit(commit = true) {
                        putString("token", token)
                        putString("provider", provider.name)
                        putStringSet("before", before)
                    }
                    mutableState.value = WidgetPinState(WidgetPinStatus.REQUESTING, token, provider.name)
                    val flags = PendingIntent.FLAG_ONE_SHOT or
                        if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    val callback = PendingIntent.getBroadcast(context, 0, callbackIntent(token), flags)
                    val supported = manager.requestPinAppWidget(component, null, callback)
                    if (!supported) mutableState.value = mutableState.value.copy(status = WidgetPinStatus.UNSUPPORTED)
                    supported
                } catch (error: RuntimeException) {
                    Log.w("WidgetPin", "Unable to request a widget", error)
                    val pendingToken = preferences.getString("token", null)?.takeIf { it == token }
                    mutableState.value = WidgetPinState(WidgetPinStatus.ERROR, pendingToken, provider.name)
                    false
                }
            }
            if (!accepted) return@launch
            repeat(10) {
                delay(1000)
                refresh()
                if (mutableState.value.token != token || mutableState.value.status == WidgetPinStatus.ADDED) return@launch
            }
            mutex.withLock {
                if (mutableState.value.token == token && mutableState.value.status == WidgetPinStatus.REQUESTING) {
                    mutableState.value = mutableState.value.copy(status = WidgetPinStatus.UNCONFIRMED)
                }
            }
        }
    }

    suspend fun refresh() = mutex.withLock {
        try {
            val current = mutableState.value
            if (current.status == WidgetPinStatus.ADDED) {
                val info = current.widgetId?.let(manager::getAppWidgetInfo)
                if (info?.provider != ComponentName(context.packageName, current.providerClass.orEmpty())) clearLocked()
            } else if (current.token != null) {
                findNewWidgetLocked()
            }
        } catch (error: RuntimeException) {
            Log.w("WidgetPin", "Unable to reconcile the launcher result", error)
            mutableState.value = mutableState.value.copy(status = WidgetPinStatus.UNCONFIRMED)
        }
    }

    suspend fun onPinned(token: String, widgetId: Int) = mutex.withLock {
        if (token == mutableState.value.token) confirmLocked(widgetId)
    }

    fun dismiss() { scope.launch { mutex.withLock { clearLocked() } } }

    private suspend fun findNewWidgetLocked(): Boolean {
        val provider = mutableState.value.providerClass?.takeIf { it in providers } ?: return false
        val before = preferences.getStringSet("before", emptySet()).orEmpty()
        val id = manager.getAppWidgetIds(ComponentName(context.packageName, provider))
            .firstOrNull { it.toString() !in before } ?: return false
        return confirmLocked(id)
    }

    private suspend fun confirmLocked(id: Int): Boolean {
        val current = mutableState.value
        val provider = current.providerClass?.takeIf { it in providers } ?: return false
        if (current.token == null || id <= AppWidgetManager.INVALID_APPWIDGET_ID ||
            id.toString() in preferences.getStringSet("before", emptySet()).orEmpty()) return false
        val info = manager.getAppWidgetInfo(id) ?: return false
        if (info.provider != ComponentName(context.packageName, provider)) return false
        preferences.edit(commit = true) { putInt("widget_id", id) }
        mutableState.value = current.copy(status = WidgetPinStatus.ADDED, widgetId = id)
        runCatching {
            repository.initialize()
            db.withTransaction {
                if (db.dao().config(id) == null) db.dao().saveConfig(WidgetConfig(id, provider, DEFAULT_TODO))
            }
            widgets.refresh(id)
        }.onFailure { Log.w("WidgetPin", "Widget exists; initial configuration could not be refreshed", it) }
        return true
    }

    private fun callbackIntent(token: String) = Intent(context, WidgetPinReceiver::class.java)
        .setAction(ACTION_PINNED).setData("wanglemai://widget-pin/$token".toUri())

    private fun cancelCallbackLocked() {
        val token = mutableState.value.token ?: return
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_ONE_SHOT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(context, 0, callbackIntent(token), flags)?.cancel()
    }

    private fun clearLocked() {
        cancelCallbackLocked()
        preferences.edit(commit = true) { clear() }
        mutableState.value = WidgetPinState()
    }

    companion object { const val ACTION_PINNED = "com.wanglemai.app.WIDGET_PINNED" }
}
