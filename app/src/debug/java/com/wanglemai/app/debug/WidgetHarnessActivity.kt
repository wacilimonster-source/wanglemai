package com.wanglemai.app.debug

import android.app.Activity
import android.appwidget.*
import android.content.ComponentName
import android.os.Bundle
import android.os.Build
import android.util.SizeF
import android.view.ViewGroup
import android.widget.LinearLayout
import com.wanglemai.app.R
import com.wanglemai.app.widget.ListLargeProvider
import com.wanglemai.app.widget.ListSmallProvider
import kotlin.math.roundToInt

/** Debug-only system AppWidgetHost for instrumentation; never a product entry point. */
class WidgetHarnessActivity : Activity() {
    private lateinit var host: AppWidgetHost
    val widgetIds = mutableListOf<Int>()
    var largeWidgetId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, 92139)
        val manager = AppWidgetManager.getInstance(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 64, 16, 16)
            setBackgroundResource(R.drawable.harness_wallpaper)
        }
        setContentView(root)
        val includeLarge = intent.getBooleanExtra("include_large", false)
        val smallCount = if (includeLarge) 1 else 2
        repeat(smallCount) {
            val id = host.allocateAppWidgetId()
            check(manager.bindAppWidgetIdIfAllowed(id,
                ComponentName(this, ListSmallProvider::class.java))) { "Test must grant appwidget bind first" }
            widgetIds += id
            root.addView(host.createView(this, id, manager.getAppWidgetInfo(id)),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    (if (includeLarge) 180 * resources.displayMetrics.density else
                        260 * resources.displayMetrics.density).toInt()))
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, if (includeLarge) 170 else 250)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300)
            })
        }
        if (includeLarge) {
            val id = host.allocateAppWidgetId()
            check(manager.bindAppWidgetIdIfAllowed(id,
                ComponentName(this, ListLargeProvider::class.java))) { "Test must grant appwidget bind first" }
            largeWidgetId = id
            root.addView(host.createView(this, id, manager.getAppWidgetInfo(id)),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    (390 * resources.displayMetrics.density).toInt()))
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 380)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320)
            })
        }
    }

    override fun onStart() { super.onStart(); host.startListening() }
    override fun onStop() { host.stopListening(); super.onStop() }

    fun resizeWidget(widgetId: Int, widthDp: Int, heightDp: Int) {
        val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val widget = (0 until root.childCount).map { root.getChildAt(it) }
            .filterIsInstance<AppWidgetHostView>().first { it.appWidgetId == widgetId }
        val density = resources.displayMetrics.density
        widget.layoutParams = LinearLayout.LayoutParams(
            (widthDp * density).roundToInt() + widget.paddingLeft + widget.paddingRight,
            (heightDp * density).roundToInt() + widget.paddingTop + widget.paddingBottom)
        val manager = AppWidgetManager.getInstance(this)
        manager.updateAppWidgetOptions(widgetId, Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            if (Build.VERSION.SDK_INT >= 31) {
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat()), SizeF(500f, 144f)))
            }
        })
    }

    override fun onDestroy() {
        // Keep bindings when the system reclaims this Activity behind a configuration
        // screen; deleting the host would also delete the widgets under test.
        if (isFinishing) host.deleteHost()
        super.onDestroy()
    }
}
