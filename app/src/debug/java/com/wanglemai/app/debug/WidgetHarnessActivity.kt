package com.wanglemai.app.debug

import android.app.Activity
import android.appwidget.*
import android.content.ComponentName
import android.os.Bundle
import android.widget.LinearLayout
import com.wanglemai.app.R
import com.wanglemai.app.widget.ListLargeProvider
import com.wanglemai.app.widget.ListSmallProvider

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
    override fun onDestroy() {
        // Keep bindings when the system reclaims this Activity behind a configuration
        // screen; deleting the host would also delete the widgets under test.
        if (isFinishing) host.deleteHost()
        super.onDestroy()
    }
}
