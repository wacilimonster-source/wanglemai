package com.wanglemai.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.wanglemai.app.R
import kotlin.math.ceil

/** A dp-resolution background keeps multi-size RemoteViews well within bitmap memory limits. */
object WidgetGlass {
    fun background(context: Context, layout: WidgetLayout, dark: Boolean): Bitmap {
        val width = ceil(layout.widthDp).toInt().coerceIn(1, 1024)
        val height = ceil(layout.heightDp).toInt().coerceIn(1, 1024)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = (context.resources.getDimension(R.dimen.widget_corner_radius) /
            context.resources.displayMetrics.density).coerceAtMost(minOf(width, height) / 2f)
        val bounds = RectF(.5f, .5f, width - .5f, height - .5f)
        paint.color = Color.parseColor(if (dark) "#E6312920" else "#EBFFF8EA")
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
            if (dark) 0x0AFFE9C9 else 0x14FFFFFF, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.parseColor(if (dark) "#24FFE9C9" else "#5CFFFFFF")
        canvas.drawRoundRect(bounds, radius, radius, paint)
        val top = Path().apply {
            arcTo(RectF(.5f, .5f, radius * 2 + .5f, radius * 2 + .5f), 180f, 90f)
            lineTo(width - radius - .5f, .5f)
            arcTo(RectF(width - radius * 2 - .5f, .5f, width - .5f, radius * 2 + .5f), 270f, 90f)
        }
        paint.color = Color.parseColor(if (dark) "#38FFE9C9" else "#8FFFFFFF")
        canvas.drawPath(top, paint)
        paint.color = Color.parseColor(if (dark) "#1F000000" else "#0F55412C")
        canvas.drawLine(radius, height - .5f, width - radius, height - .5f, paint)
        return bitmap
    }
}
