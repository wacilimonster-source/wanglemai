package com.wanglemai.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.wanglemai.app.app
import kotlinx.coroutines.launch

open class BaseListProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        update(context, ids)

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, options: Bundle
    ) = update(context, intArrayOf(id))

    private fun update(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        context.app.scope.launch {
            try {
                ids.forEach { id ->
                    runCatching { context.app.container.widgets.refresh(id) }
                        .onFailure { runCatching { context.app.container.widgets.showError(id) } }
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val pending = goAsync()
        context.app.scope.launch {
            try {
                ids.forEach { id -> runCatching { context.app.container.widgets.forget(id) } }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        val pending = goAsync()
        context.app.scope.launch {
            try {
                context.app.container.widgets.restore(oldIds, newIds)
            } finally {
                pending.finish()
            }
        }
    }
}

class ListSmallProvider : BaseListProvider()
class ListLargeProvider : BaseListProvider()
