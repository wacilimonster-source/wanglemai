package com.wanglemai.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import com.wanglemai.app.domain.WidgetDensity
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pixel budgets shared by the desktop renderer and the configuration preview. */
data class WidgetLayout(
    val widthDp: Float,
    val heightDp: Float,
    val density: WidgetDensity,
    val full: Boolean,
    val horizontalPadding: Int,
    val verticalPadding: Int,
    val controlHeight: Int,
    val rowHeight: Int,
    val contentGap: Int,
    val contentHeight: Int,
    val capacity: Int,
    val quantityWidth: Int,
    val showCount: Boolean
) {
    val compact: Boolean get() = density != WidgetDensity.COMFORTABLE
    val ultraCompact: Boolean get() = density == WidgetDensity.ULTRA_COMPACT

    companion object {
        fun resolve(context: Context, size: SizeF, density: WidgetDensity): WidgetLayout {
            val compact = density != WidgetDensity.COMFORTABLE
            val ultra = density == WidgetDensity.ULTRA_COMPACT
            val metrics = context.resources.displayMetrics
            fun dp(value: Int) = (value * metrics.density).roundToInt()
            fun textHeight(sp: Float): Int {
                val paint = Paint().apply {
                    textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
                }
                val font = paint.fontMetrics
                return ceil(font.bottom - font.top).toInt() + dp(if (ultra) 8 else 16)
            }
            val touch = dp(if (ultra) 36 else 48)
            val control = maxOf(touch, textHeight(if (ultra) 14f else 16f))
            // Round the repeating row down, so a fractional-density screen does not lose
            // a complete row through accumulated rounding at exact dp boundaries.
            val rowDp = when (density) {
                WidgetDensity.COMFORTABLE -> 52
                WidgetDensity.COMPACT -> 48
                WidgetDensity.ULTRA_COMPACT -> 36
            }
            val row = maxOf((rowDp * metrics.density).toInt(), textHeight(14f), touch)
            val height = (size.height * metrics.density).roundToInt()
            val firstRow = if (height >= control + row) row else 0
            val headerGap = minOf(dp(if (ultra) 0 else 4),
                (height - control - firstRow).coerceAtLeast(0))
            val padding = minOf(dp(if (ultra) 4 else if (compact) 8 else 12),
                ((height - control - firstRow - headerGap) / 2).coerceAtLeast(0))
            // Reintroduce the footer only when doing so cannot reduce the visible row count.
            val full = !compact && size.height >= 276 &&
                control - dp(4) < row &&
                height >= padding * 2 + control * 2 + row * 3
            val gap = if (full) 0 else headerGap
            val contentHeight = (height - padding * 2 - control * (if (full) 2 else 1) - gap)
                .coerceAtLeast(0)
            val quantityDp = when {
                size.width < 240 -> 0
                size.width < 280 -> 48
                compact -> 56
                else -> 64
            }
            return WidgetLayout(size.width, size.height, density, full,
                dp(if (ultra) 8 else if (compact) 12 else 16), padding, control, row, gap, contentHeight,
                (contentHeight / row).coerceIn(0, 12), dp(quantityDp),
                size.width >= 240 && context.resources.configuration.fontScale < 1.3f)
        }
    }
}

object WidgetSizes {
    fun forWidget(context: Context, widgetId: Int, large: Boolean): List<SizeF> {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            val valid = sizes.orEmpty().filter { it.width.isFinite() && it.height.isFinite() &&
                it.width > 0 && it.height > 0 }.distinct().take(16)
            if (valid.isNotEmpty()) return valid
        }
        val landscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
            else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
        val heightKey = if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        // Some launchers send explicit zero sizes before the configuration activity opens.
        fun positive(key: String, fallback: Int) = options.getInt(key, 0).takeIf { it > 0 } ?: fallback
        val minWidth = positive(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
        val minHeight = positive(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, if (large) 320 else 180)
        return listOf(SizeF(positive(widthKey, minWidth).toFloat(), positive(heightKey, minHeight).toFloat()))
    }
}
