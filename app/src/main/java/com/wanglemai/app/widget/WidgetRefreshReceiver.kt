package com.wanglemai.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wanglemai.app.WanglemaiApplication
import kotlinx.coroutines.launch

/** Repaints every widget after boot or an APK replace, when the launcher may have stale data. */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as? WanglemaiApplication ?: return
        val pending = goAsync()
        app.scope.launch {
            try {
                app.container.widgets.refreshAll()
            } catch (_: Exception) {
                // A host failure must not crash the boot/replace broadcast.
            } finally {
                pending.finish()
            }
        }
    }
}
