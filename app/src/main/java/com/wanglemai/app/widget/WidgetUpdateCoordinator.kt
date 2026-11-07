package com.wanglemai.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import com.wanglemai.app.R
import com.wanglemai.app.data.RoomItemRepository
import com.wanglemai.app.data.db.AppDatabase
import com.wanglemai.app.data.db.WidgetConfig
import com.wanglemai.app.domain.*
import com.wanglemai.app.ui.editor.QuickEntryActivity
import com.wanglemai.app.ui.main.MainActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WidgetUpdateCoordinator(
    private val context: Context,
    private val db: AppDatabase,
    private val repository: RoomItemRepository
) {
    private val mutex = Mutex()
    private val manager = AppWidgetManager.getInstance(context)
    private val renderer = WidgetRenderer(context)
    private val transientMessages = mutableMapOf<Int, String>()
    private val providers = listOf(ListSmallProvider::class.java, ListLargeProvider::class.java)

    suspend fun refreshAll() {
        providers.forEach { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).forEach { refresh(it) }
        }
    }

    suspend fun refresh(widgetId: Int) = mutex.withLock {
        transientMessages.remove(widgetId)
        renderLocked(widgetId)
    }

    suspend fun showError(widgetId: Int) = mutex.withLock {
        renderErrorLocked(widgetId)
    }

    /** Footer feedback for a conflict or rejected action; cleared by the next normal refresh. */
    suspend fun showMessage(widgetId: Int, message: String) = mutex.withLock {
        transientMessages[widgetId] = message
        renderLocked(widgetId)
    }

    suspend fun forget(widgetId: Int) = mutex.withLock {
        transientMessages.remove(widgetId)
        db.dao().deleteConfig(widgetId)
    }

    suspend fun restore(oldIds: IntArray, newIds: IntArray) {
        val dao = db.dao()
        db.withTransaction {
            oldIds.zip(newIds).forEach { (oldId, newId) ->
                dao.config(oldId)?.let { config ->
                    dao.clearWidgetUndo(newId)
                    dao.saveConfig(config.copy(appWidgetId = newId, updatedAt = System.currentTimeMillis()))
                }
                dao.deleteConfig(oldId)
            }
        }
        oldIds.forEach { transientMessages.remove(it) }
        newIds.forEach { runCatching { refresh(it) } }
    }

    /** Drops configs whose host instances disappeared, then repaints every surviving instance. */
    suspend fun syncInstances() {
        val valid = HashSet<Int>()
        providers.forEach { manager.getAppWidgetIds(ComponentName(context, it)).forEach(valid::add) }
        mutex.withLock {
            val dao = db.dao()
            dao.configs().forEach { config ->
                if (config.appWidgetId !in valid) dao.deleteConfig(config.appWidgetId)
            }
            transientMessages.keys.retainAll(valid)
        }
        refreshAll()
    }

    private suspend fun renderLocked(widgetId: Int) {
        val info = manager.getAppWidgetInfo(widgetId) ?: return
        if (info.provider?.packageName != context.packageName) return
        val dao = db.dao()
        val config = dao.config(widgetId)
        val listId = config?.listId ?: DEFAULT_TODO
        val list = dao.list(listId) ?: return renderErrorLocked(widgetId)
        val large = info.provider.className == ListLargeProvider::class.java.name
        val density = widgetDensityOf(config?.density)
        val dark = isDark(themeModeOf(config?.theme))
        val sizes = WidgetSizes.forWidget(context, widgetId, large)
        val rows = dao.widgetItems(list.id, 12)
        val total = dao.count(list.id)
        val undoEntity = if (config != null) dao.undo(widgetId) else null
        val undo = undoEntity?.token()
        val add = activity(widgetId, "create", QuickEntryActivity.createIntent(context, list.id, widgetId))
        val openList = activity(widgetId, "list", Intent(context, MainActivity::class.java)
            .putExtra("list_id", list.id))
        val retry = action(widgetId, "refresh")
        val intents = WidgetIntents(
            add = add,
            openList = openList,
            retry = retry,
            complete = { item -> action(widgetId, "complete", item.id, item.version) },
            edit = { item -> activity(widgetId, "edit/${item.id}",
                QuickEntryActivity.editIntent(context, item.id, widgetId)) },
            undo = undoEntity?.let { stored ->
                action(widgetId, "undo", commandId = stored.commandId)
            }
        )
        val layouts = sizes.associateWith { size ->
            renderer.render(
                list = TaskList(list.id, list.name, ListKind.valueOf(list.kind), list.isDefault),
                rows = rows.map { it.model() },
                totalActive = total,
                undo = undo,
                footerMessage = transientMessages[widgetId],
                dark = dark,
                layout = WidgetLayout.resolve(context, size, density),
                intents = intents
            )
        }
        val views = if (Build.VERSION.SDK_INT >= 31 && layouts.size > 1)
            android.widget.RemoteViews(layouts) else layouts.values.first()
        manager.updateAppWidget(widgetId, views)
    }

    private suspend fun renderErrorLocked(widgetId: Int) {
        val info = manager.getAppWidgetInfo(widgetId) ?: return
        if (info.provider?.packageName != context.packageName) return
        val dao = db.dao()
        val config = dao.config(widgetId)
        val listId = config?.listId ?: DEFAULT_TODO
        val add = activity(widgetId, "create", QuickEntryActivity.createIntent(context, listId, widgetId))
        val large = info.provider.className == ListLargeProvider::class.java.name
        val density = widgetDensityOf(config?.density)
        val layouts = WidgetSizes.forWidget(context, widgetId, large).associateWith { size ->
            renderer.error(
                dark = isDark(themeModeOf(config?.theme)),
                layout = WidgetLayout.resolve(context, size, density),
                add = add,
                retry = action(widgetId, "refresh")
            )
        }
        val views = if (Build.VERSION.SDK_INT >= 31 && layouts.size > 1)
            android.widget.RemoteViews(layouts) else layouts.values.first()
        manager.updateAppWidget(widgetId, views)
    }

    private fun isDark(mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun activity(widgetId: Int, route: String, intent: Intent): PendingIntent {
        intent.data = Uri.parse("wanglemai://widget/$widgetId/$route")
        intent.putExtra("origin_widget_id", widgetId)
        return PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(
        widgetId: Int,
        action: String,
        itemId: String = "",
        version: Long = 0,
        commandId: String = ""
    ): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java)
            .setAction("${context.packageName}.$action")
            .setData(Uri.parse("wanglemai://widget/$widgetId/$action/$itemId?v=$version&command=$commandId"))
            .putExtra("widget_id", widgetId)
            .putExtra("item_id", itemId)
            .putExtra("version", version)
            .putExtra("command_id", commandId)
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
