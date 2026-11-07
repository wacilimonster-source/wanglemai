package com.wanglemai.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wanglemai.app.app
import kotlinx.coroutines.launch

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetPinCoordinator.ACTION_PINNED || intent.data?.host != "widget-pin") return
        val token = intent.data?.lastPathSegment ?: return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id <= AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        context.app.scope.launch {
            try {
                context.app.container.pinning.onPinned(token, id)
            } catch (error: Exception) {
                Log.w("WidgetPin", "Unable to confirm the pinned widget", error)
            } finally {
                pending.finish()
            }
        }
    }
}
