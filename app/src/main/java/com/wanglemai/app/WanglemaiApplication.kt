package com.wanglemai.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.room.Room
import com.wanglemai.app.data.RoomBackupRepository
import com.wanglemai.app.data.RoomItemRepository
import com.wanglemai.app.data.db.AppDatabase
import com.wanglemai.app.data.update.UpdateCenter
import com.wanglemai.app.domain.MutationResult
import com.wanglemai.app.widget.WidgetUpdateCoordinator
import kotlinx.coroutines.*
import java.io.File

class WanglemaiApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val container by lazy { AppContainer(this) }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Widgets use explicit color resources; SYSTEM-themed instances must repaint when
        // the host changes night mode or font scale while this process is alive.
        scope.launch { runCatching { container.widgets.refreshAll() } }
    }
}
class AppContainer(context: Context) {
    val db = Room.databaseBuilder(context, AppDatabase::class.java, "wanglemai.db").build()
    val repository = RoomItemRepository(db)
    val backup = RoomBackupRepository(db)
    val widgets = WidgetUpdateCoordinator(context.applicationContext, db, repository)
    val updates = UpdateCenter(
        scope = (context.applicationContext as WanglemaiApplication).scope,
        updatesDir = File(context.filesDir, "updates")
    )
    suspend fun mutate(block: suspend () -> MutationResult): MutationResult {
        val result = block()
        if (result is MutationResult.Success) runCatching { widgets.refreshAll() }
        return result // A host refresh error must never turn a committed save into a duplicate retry.
    }
}
val Context.app: WanglemaiApplication get() = applicationContext as WanglemaiApplication
