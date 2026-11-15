package com.wanglemai.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.room.Room
import com.wanglemai.app.data.RoomBackupRepository
import com.wanglemai.app.data.RoomItemRepository
import com.wanglemai.app.data.db.AppDatabase
import com.wanglemai.app.data.db.MIGRATION_1_2
import com.wanglemai.app.data.update.UpdateCenter
import com.wanglemai.app.domain.MutationResult
import com.wanglemai.app.widget.WidgetUpdateCoordinator
import com.wanglemai.app.widget.WidgetPinCoordinator
import kotlinx.coroutines.*
import java.io.File

class WanglemaiApplication : Application() {
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e("WanglemaiApp", "Uncaught application coroutine failure", error)
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    val container by lazy { AppContainer(this) }

    // Widget appearance depends only on night mode, density, and font scale;
    // other configuration changes must not trigger a full widget re-render.
    private var widgetConfigBits = 0

    override fun onCreate() {
        super.onCreate()
        widgetConfigBits = widgetConfigBitsOf(resources.configuration)
        // Warm up the container off the main thread so the first frame does not
        // pay for the Room build, preference reads, and binder calls.
        scope.launch { runCatching { container } }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Widgets use explicit color resources; SYSTEM-themed instances must repaint when
        // the host changes night mode or font scale while this process is alive.
        val bits = widgetConfigBitsOf(newConfig)
        if (bits == widgetConfigBits) return
        widgetConfigBits = bits
        scope.launch {
            try {
                container.widgets.requestRefreshAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WanglemaiApp", "Failed to refresh widgets on config change", e)
            }
        }
    }

    private fun widgetConfigBitsOf(config: Configuration): Int =
        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) or
            (config.densityDpi shl 8) or
            ((config.fontScale * 100).toInt() shl 20)
}
class AppContainer(context: Context) {
    val db = Room.databaseBuilder(context, AppDatabase::class.java, "wanglemai.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    val repository = RoomItemRepository(db)
    val backup = RoomBackupRepository(db)
    val widgets = WidgetUpdateCoordinator(context.applicationContext, db, repository,
        (context.applicationContext as WanglemaiApplication).scope)
    val pinning = WidgetPinCoordinator(context, db, repository, widgets,
        (context.applicationContext as WanglemaiApplication).scope)
    val updates = UpdateCenter(
        scope = (context.applicationContext as WanglemaiApplication).scope,
        updatesDir = File(context.filesDir, "updates")
    )

    /**
     * Commits [block] and, on success, schedules a widget refresh on the app scope.
     * Callers that must guarantee the widgets are repainted before proceeding
     * (broadcast receivers inside their goAsync window) pass [refreshWidgets] = false
     * and await [WidgetUpdateCoordinator.refreshAll] themselves.
     */
    suspend fun mutate(refreshWidgets: Boolean = true, block: suspend () -> MutationResult): MutationResult {
        val result = block()
        if (result is MutationResult.Success && refreshWidgets) {
            widgets.requestRefreshAll() // A refresh failure here only logs inside the coordinator; never a duplicate retry.
        }
        return result
    }
}
val Context.app: WanglemaiApplication get() = applicationContext as WanglemaiApplication
