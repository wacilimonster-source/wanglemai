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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Widgets use explicit color resources; SYSTEM-themed instances must repaint when
        // the host changes night mode or font scale while this process is alive.
        scope.launch {
            try {
                container.widgets.refreshAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WanglemaiApp", "Failed to refresh widgets on config change", e)
            }
        }
    }
}
class AppContainer(context: Context) {
    val db = Room.databaseBuilder(context, AppDatabase::class.java, "wanglemai.db")
        .addMigrations(MIGRATION_1_2)
        .build()
    val repository = RoomItemRepository(db)
    val backup = RoomBackupRepository(db)
    val widgets = WidgetUpdateCoordinator(context.applicationContext, db, repository)
    val pinning = WidgetPinCoordinator(context, db, repository, widgets,
        (context.applicationContext as WanglemaiApplication).scope)
    val updates = UpdateCenter(
        scope = (context.applicationContext as WanglemaiApplication).scope,
        updatesDir = File(context.filesDir, "updates")
    )
    suspend fun mutate(block: suspend () -> MutationResult): MutationResult {
        val result = block()
        if (result is MutationResult.Success) {
            try {
                widgets.refreshAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WanglemaiApp", "Widget refresh failed after mutation", e)
            }
        }
        return result // A host refresh error must never turn a committed save into a duplicate retry.
    }
}
val Context.app: WanglemaiApplication get() = applicationContext as WanglemaiApplication
