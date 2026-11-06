package com.wanglemai.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wanglemai.app.app
import com.wanglemai.app.domain.MutationResult
import com.wanglemai.app.domain.message
import kotlinx.coroutines.launch

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) ?: return
        if (info.provider?.packageName != context.packageName) return
        val action = intent.action ?: return
        if (!action.startsWith("${context.packageName}.")) return
        val pending = goAsync()
        context.app.scope.launch {
            try {
                val container = context.app.container
                when (action) {
                    "${context.packageName}.complete" -> {
                        val result = container.mutate {
                            container.repository.setCompleted(
                                intent.getStringExtra("item_id").orEmpty(),
                                intent.getLongExtra("version", -1),
                                true,
                                widgetId
                            )
                        }
                        if (result is MutationResult.Success) {
                            container.widgets.refresh(widgetId)
                        } else {
                            if (result is MutationResult.Conflict || result is MutationResult.NotFound) {
                                runCatching { container.db.dao().clearWidgetUndo(widgetId) }
                            }
                            container.widgets.showMessage(widgetId, widgetMessage(result))
                        }
                    }
                    "${context.packageName}.undo" -> {
                        val result = container.mutate {
                            container.repository.undoWidgetCompletion(
                                widgetId, intent.getStringExtra("command_id").orEmpty()
                            )
                        }
                        if (result is MutationResult.Success) {
                            container.widgets.refresh(widgetId)
                        } else {
                            container.widgets.showMessage(widgetId, widgetMessage(result))
                        }
                    }
                    "${context.packageName}.refresh" -> {
                        container.widgets.refresh(widgetId)
                    }
                }
            } catch (e: Exception) {
                Log.e("WidgetAction", "Widget action failed", e)
                runCatching { context.app.container.widgets.showError(widgetId) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun widgetMessage(result: MutationResult): String = when (result) {
        MutationResult.Conflict -> "记录已变化，请到 App 查看"
        MutationResult.NotFound -> "记录已不存在"
        else -> result.message()
    }
}
