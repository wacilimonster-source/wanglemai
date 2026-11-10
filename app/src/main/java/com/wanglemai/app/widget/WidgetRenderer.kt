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
    val checkIcon: Int
)

/** The preview and launcher both use these RemoteViews, including their pixel budgets. */
class WidgetRenderer(private val context: Context) {
    fun render(
        list: TaskList,
        rows: List<Item>,
        totalActive: Int,
        undo: UndoToken?,
        footerMessage: String?,
        dark: Boolean,
        layout: WidgetLayout,
        intents: WidgetIntents? = null,
        completedCount: Int = 0
    ): RemoteViews {
        val palette = palette(dark)
        val views = content(layout, dark, palette)
        views.setTextViewText(R.id.widget_title, list.name)
        views.setContentDescription(R.id.widget_open, "打开${list.name}，共${totalActive}项")
        val count = if (totalActive > 99) "99+" else totalActive.toString()
        views.setTextViewText(R.id.widget_count, "${count}项" + if (layout.full) "" else " ›")
        bind(views, R.id.widget_open, intents?.openList)
        bind(views, R.id.widget_add, intents?.add)
        bind(views, R.id.widget_header_add, intents?.add)
        val addLabel = "新增一条${list.name}记录"
        views.setContentDescription(R.id.widget_add, addLabel)
        views.setContentDescription(R.id.widget_header_add, addLabel)

        val visibleRows = rows.take(layout.capacity)
        val emptyText = when {
            totalActive > 0 -> "空间较小 · 打开查看全部"
            completedCount > 0 && list.kind == ListKind.SHOPPING -> "都买齐了 · 再记一条"
            completedCount > 0 -> "都完成了 · 再记一条"
            list.kind == ListKind.SHOPPING -> "还没要买的，记一条"
            else -> "清单空空，想到就记"
        }
        showEmpty(views, layout, visibleRows.isEmpty(), emptyText)
        bind(views, R.id.widget_empty, intents?.openList)
        views.removeAllViews(R.id.widget_rows)
        visibleRows.forEach { item ->
            val row = RemoteViews(context.packageName,
                when {
                    layout.ultraCompact -> R.layout.widget_row_ultra
                    layout.compact -> R.layout.widget_row_compact
                    else -> R.layout.widget_row
                })
            row.setViewPadding(R.id.row_root, 0, 0, layout.horizontalPadding, 0)
            row.setInt(R.id.row_title, "setHeight", layout.rowHeight)
            row.setTextViewText(R.id.row_title, item.title)
            row.setTextColor(R.id.row_title, palette.text)
            row.setImageViewResource(R.id.row_check_icon, palette.checkIcon)
            row.setInt(R.id.row_pin, "setColorFilter", palette.secondary)
            row.setViewVisibility(R.id.row_pin, if (item.pinned) View.VISIBLE else View.GONE)
            val quantity = listOfNotNull(item.quantityDecimal, item.unit).joinToString(" ")
            row.setTextViewText(R.id.row_quantity, quantity)
            row.setTextColor(R.id.row_quantity, palette.secondary)
            row.setInt(R.id.row_quantity, "setMaxWidth", layout.quantityWidth)
            row.setViewVisibility(R.id.row_quantity,
                if (quantity.isNotEmpty() && layout.quantityWidth > 0) View.VISIBLE else View.GONE)
            val actionLabel = if (list.kind == ListKind.SHOPPING) "已购买" else "已完成"
            row.setContentDescription(R.id.row_check, "标记『${item.title}』为$actionLabel")
            val description = item.title + (if (item.pinned) "，已置顶" else "") +
                (if (quantity.isNotEmpty()) "，数量 $quantity" else "")
            row.setContentDescription(R.id.row_title, description)
            bind(row, R.id.row_check, intents?.complete?.invoke(item))
            bind(row, R.id.row_title, intents?.edit?.invoke(item))
            views.addView(R.id.widget_rows, row)
        }

        val feedbackId = if (layout.full) R.id.widget_more else R.id.widget_title
        val feedbackClickId = if (layout.full) R.id.widget_more else R.id.widget_open
        when {
            footerMessage != null -> {
                views.setTextViewText(feedbackId, "未完成，点此查看")
                views.setTextColor(feedbackId, palette.error)
                views.setContentDescription(feedbackClickId,
                    "${list.name}，$footerMessage，点按打开应用")
                bind(views, feedbackClickId, intents?.openList)
            }
            undo != null && intents?.undo != null -> {
                views.setTextViewText(feedbackId, "已完成 · 撤销")
                views.setTextColor(feedbackId, palette.primary)
                views.setContentDescription(feedbackClickId, "${list.name}，撤销最近一次完成操作")
                bind(views, feedbackClickId, intents.undo)
            }
            else -> {
                views.setTextViewText(R.id.widget_more, "查看全部 ›")
                views.setContentDescription(R.id.widget_more, "查看全部${list.name}记录")
                bind(views, R.id.widget_more, intents?.openList)
            }
        }
        if (!layout.full && (footerMessage != null || undo != null && intents?.undo != null)) {
            views.setViewVisibility(R.id.widget_count, View.GONE)
        }
        return views
    }

    fun error(dark: Boolean, layout: WidgetLayout, add: PendingIntent, retry: PendingIntent): RemoteViews {
        val palette = palette(dark)
        val views = content(layout, dark, palette)
        views.setTextViewText(R.id.widget_title, "忘了没 · 重试")
        views.setContentDescription(R.id.widget_open, "暂时无法读取，点此重试")
        views.setViewVisibility(R.id.widget_count, View.GONE)
        showEmpty(views, layout, true, "暂时无法读取 · 点此重试")
        views.setContentDescription(R.id.widget_empty, "暂时无法读取，点此重试")
        bind(views, R.id.widget_open, retry)
        bind(views, R.id.widget_empty, retry)
        bind(views, R.id.widget_header_add, add)
        bind(views, R.id.widget_add, add)
        views.setContentDescription(R.id.widget_header_add, "新增记录")
        views.setTextViewText(R.id.widget_more, "重试")
        views.setTextColor(R.id.widget_more, palette.error)
        bind(views, R.id.widget_more, retry)
        return views
    }

    private fun content(layout: WidgetLayout, dark: Boolean, palette: WidgetPalette): RemoteViews {
        val views = RemoteViews(context.packageName,
            if (layout.ultraCompact) R.layout.widget_content_ultra else R.layout.widget_content)
        views.setInt(android.R.id.background, "setBackgroundResource", 0)
        views.setImageViewBitmap(android.R.id.background, WidgetGlass.background(context, layout, dark))
        views.setViewPadding(R.id.widget_stack, 0, layout.verticalPadding, 0, layout.verticalPadding)
        views.setViewPadding(R.id.widget_header, layout.horizontalPadding, 0, layout.horizontalPadding, 0)
        views.setViewPadding(R.id.widget_footer, layout.horizontalPadding, 0, layout.horizontalPadding, 0)
        views.setInt(R.id.widget_title, "setHeight", layout.controlHeight)
        views.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP,
            if (layout.ultraCompact) 14f else 16f)
        views.setInt(R.id.widget_add, "setHeight", layout.controlHeight)
        views.setInt(R.id.widget_more, "setHeight", layout.controlHeight)
        views.setInt(R.id.widget_gap, "setHeight", layout.contentGap)
        views.setInt(R.id.widget_more, "setMaxWidth",
            ((layout.widthDp * context.resources.displayMetrics.density -
                layout.horizontalPadding * 2) / 2).toInt())
        views.setViewVisibility(R.id.widget_footer, if (layout.full) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_header_add, if (layout.full) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_count, if (layout.showCount) View.VISIBLE else View.GONE)
        views.setTextColor(R.id.widget_title, palette.text)
        views.setTextColor(R.id.widget_count, palette.secondary)
        views.setTextColor(R.id.widget_empty, palette.secondary)
        views.setTextColor(R.id.widget_add, palette.primary)
        views.setTextColor(R.id.widget_more, palette.primary)
        views.setInt(R.id.widget_add_icon, "setColorFilter", palette.primary)
        return views
    }

    private fun showEmpty(views: RemoteViews, layout: WidgetLayout, empty: Boolean, text: String) {
        views.setViewVisibility(R.id.widget_empty,
            if (empty && layout.contentHeight >= layout.controlHeight) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_empty, text)
        views.setViewPadding(R.id.widget_empty, layout.horizontalPadding, 0, layout.horizontalPadding, 0)
    }

    private fun bind(views: RemoteViews, id: Int, intent: PendingIntent?) {
        if (intent != null) views.setOnClickPendingIntent(id, intent)
    }

    private fun palette(dark: Boolean) = if (dark) WidgetPalette(
        text = context.getColor(R.color.widget_dark_text),
        secondary = context.getColor(R.color.widget_dark_secondary),
        primary = context.getColor(R.color.widget_dark_primary),
        error = context.getColor(R.color.widget_dark_error),
        checkIcon = R.drawable.widget_check_ring_dark
    ) else WidgetPalette(
        text = context.getColor(R.color.widget_light_text),
        secondary = context.getColor(R.color.widget_light_secondary),
        primary = context.getColor(R.color.widget_light_primary),
        error = context.getColor(R.color.widget_light_error),
        checkIcon = R.drawable.widget_check_ring_light
    )

}
