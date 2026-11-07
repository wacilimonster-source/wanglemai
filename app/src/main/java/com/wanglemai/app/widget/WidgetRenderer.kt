package com.wanglemai.app.widget

import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.wanglemai.app.R
import com.wanglemai.app.domain.Item
import com.wanglemai.app.domain.ListKind
import com.wanglemai.app.domain.TaskList
import com.wanglemai.app.domain.UndoToken

data class WidgetIntents(
    val add: PendingIntent,
    val openList: PendingIntent,
    val retry: PendingIntent,
    val complete: (Item) -> PendingIntent,
    val edit: (Item) -> PendingIntent,
    val undo: PendingIntent?
)

private data class WidgetPalette(
    val text: Int,
    val secondary: Int,
    val primary: Int,
    val error: Int,
    val background: Int,
    val checkIcon: Int
)

/** Pure RemoteViews rendering; all data is resolved before this class is called. */
class WidgetRenderer(private val context: Context) {

    fun render(
        list: TaskList,
        rows: List<Item>,
        totalActive: Int,
        undo: UndoToken?,
        footerMessage: String?,
        dark: Boolean,
        compact: Boolean,
        intents: WidgetIntents
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_content)
        val palette = palette(dark)
        applyTheme(views, palette)
        views.setTextViewText(R.id.widget_title, list.name)
        views.setContentDescription(R.id.widget_title, "打开${list.name}")
        views.setTextViewText(R.id.widget_count, "$totalActive 项")
        views.setOnClickPendingIntent(R.id.widget_title, intents.openList)

        val empty = rows.isEmpty()
        views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)
        if (empty) {
            if (totalActive > 0) {
                // Too small for even one row: keep title + footer entries, never force a clipped row.
                views.setTextViewText(R.id.widget_empty, "空间较小 · 打开查看全部")
                views.setContentDescription(R.id.widget_empty, "空间较小，点此打开${list.name}")
                views.setOnClickPendingIntent(R.id.widget_empty, intents.openList)
            } else {
                views.setTextViewText(R.id.widget_empty,
                    "还没有${kindLabel(list.kind)}，想到就记下来。")
            }
        }
        views.removeAllViews(R.id.widget_rows)
        rows.forEach { item ->
            val row = RemoteViews(context.packageName,
                if (compact) R.layout.widget_row_compact else R.layout.widget_row)
            row.setTextViewText(R.id.row_title, (if (item.pinned) "• " else "") + item.title)
            row.setTextColor(R.id.row_title, palette.text)
            row.setImageViewResource(R.id.row_check_icon, palette.checkIcon)
            row.setTextColor(R.id.row_quantity, palette.secondary)
            val quantity = listOfNotNull(item.quantityDecimal, item.unit).joinToString(" ")
            row.setTextViewText(R.id.row_quantity, quantity)
            row.setViewVisibility(R.id.row_quantity, if (quantity.isEmpty()) View.GONE else View.VISIBLE)
            val actionLabel = if (list.kind == ListKind.SHOPPING) "已购买" else "已完成"
            row.setContentDescription(R.id.row_check, "标记『${item.title}』为$actionLabel")
            row.setContentDescription(R.id.row_title,
                if (quantity.isEmpty()) item.title else "${item.title}，数量 $quantity")
            row.setContentDescription(R.id.row_quantity, "")
            row.setOnClickPendingIntent(R.id.row_check, intents.complete(item))
            row.setOnClickPendingIntent(R.id.row_title, intents.edit(item))
            views.addView(R.id.widget_rows, row)
        }

        views.setTextViewText(R.id.widget_add, "＋ 记一条")
        views.setContentDescription(R.id.widget_add, "新增一条${list.name}记录")
        views.setOnClickPendingIntent(R.id.widget_add, intents.add)
        when {
            footerMessage != null -> {
                views.setTextViewText(R.id.widget_more, footerMessage)
                views.setTextColor(R.id.widget_more, palette.error)
                views.setContentDescription(R.id.widget_more, "$footerMessage，点按打开应用")
                views.setOnClickPendingIntent(R.id.widget_more, intents.openList)
            }
            undo != null && intents.undo != null -> {
                views.setTextViewText(R.id.widget_more, "已完成 · 撤销")
                views.setTextColor(R.id.widget_more, palette.primary)
                views.setContentDescription(R.id.widget_more, "撤销最近一次完成操作")
                views.setOnClickPendingIntent(R.id.widget_more, intents.undo)
            }
            else -> {
                views.setTextViewText(R.id.widget_more, "查看全部 ›")
                views.setTextColor(R.id.widget_more, palette.primary)
                views.setContentDescription(R.id.widget_more, "查看全部${list.name}记录")
                views.setOnClickPendingIntent(R.id.widget_more, intents.openList)
            }
        }
        return views
    }

    fun error(
        dark: Boolean,
        add: PendingIntent,
        retry: PendingIntent
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_content)
        val palette = palette(dark)
        applyTheme(views, palette)
        views.setTextViewText(R.id.widget_title, "忘了没")
        views.setTextViewText(R.id.widget_count, "")
        views.setTextViewText(R.id.widget_empty, "暂时无法读取 · 点此重试")
        views.setContentDescription(R.id.widget_empty, "暂时无法读取，点此重试")
        views.setOnClickPendingIntent(R.id.widget_empty, retry)
        views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        views.removeAllViews(R.id.widget_rows)
        views.setTextViewText(R.id.widget_add, "＋ 记一条")
        views.setOnClickPendingIntent(R.id.widget_add, add)
        views.setTextViewText(R.id.widget_more, "重试")
        views.setTextColor(R.id.widget_more, palette.error)
        views.setOnClickPendingIntent(R.id.widget_more, retry)
        return views
    }

    private fun applyTheme(views: RemoteViews, palette: WidgetPalette) {
        views.setInt(R.id.widget_root, "setBackgroundResource", palette.background)
        views.setTextColor(R.id.widget_title, palette.text)
        views.setTextColor(R.id.widget_count, palette.secondary)
        views.setTextColor(R.id.widget_empty, palette.secondary)
        views.setTextColor(R.id.widget_add, palette.primary)
        views.setTextColor(R.id.widget_more, palette.primary)
    }

    private fun palette(dark: Boolean) = if (dark) WidgetPalette(
        text = context.getColor(R.color.widget_dark_text),
        secondary = context.getColor(R.color.widget_dark_secondary),
        primary = context.getColor(R.color.widget_dark_primary),
        error = context.getColor(R.color.widget_dark_error),
        background = R.drawable.widget_background_dark,
        checkIcon = R.drawable.widget_check_ring_dark
    ) else WidgetPalette(
        text = context.getColor(R.color.widget_light_text),
        secondary = context.getColor(R.color.widget_light_secondary),
        primary = context.getColor(R.color.widget_light_primary),
        error = context.getColor(R.color.widget_light_error),
        background = R.drawable.widget_background_light,
        checkIcon = R.drawable.widget_check_ring_light
    )

    private fun kindLabel(kind: ListKind) = if (kind == ListKind.SHOPPING) "待买" else "待办"
}
