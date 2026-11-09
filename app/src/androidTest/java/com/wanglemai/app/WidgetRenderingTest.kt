package com.wanglemai.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.wanglemai.app.data.db.WidgetConfig
import com.wanglemai.app.debug.WidgetHarnessActivity
import com.wanglemai.app.domain.Item
import com.wanglemai.app.domain.ItemInput
import com.wanglemai.app.domain.ListKind
import com.wanglemai.app.domain.MutationResult
import com.wanglemai.app.domain.TaskList
import com.wanglemai.app.domain.UndoToken
import com.wanglemai.app.domain.WidgetDensity
import com.wanglemai.app.domain.ThemeMode
import com.wanglemai.app.widget.WidgetIntents
import com.wanglemai.app.widget.WidgetLayout
import com.wanglemai.app.widget.WidgetRenderer
import com.wanglemai.app.widget.WidgetSizes
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val baseContext = instrumentation.targetContext
    private val list = TaskList("render-test", "随手待办", ListKind.SHOPPING)
    private val rows = (1..12).map { index ->
        Item("item-$index", list.id, "事项 $index", "", "2", "盒", false,
            index == 1, index.toLong(), 0, 0, null, 1)
    }

    private fun context(scale: Float = 1f): Context = baseContext.createConfigurationContext(
        Configuration(baseContext.resources.configuration).apply { fontScale = scale })

    private fun render(context: Context, size: SizeF, compact: Boolean, dark: Boolean = false): View {
        val layout = WidgetLayout.resolve(context, size, if (compact) WidgetDensity.COMPACT else WidgetDensity.COMFORTABLE)
        val views = WidgetRenderer(context).render(list, rows, rows.size, null, null, dark, layout)
        val root = views.apply(context, FrameLayout(context))
        measure(context, size, root)
        return root
    }

    private fun measure(context: Context, size: SizeF, root: View) {
        val density = context.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((size.width * density).roundToInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((size.height * density).roundToInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    private fun assertGeometry(context: Context, root: View, touchDp: Int = 48) {
        val body = root.findViewById<View>(R.id.widget_body)
        val items = root.findViewById<LinearLayout>(R.id.widget_rows)
        val touchSize = (touchDp * context.resources.displayMetrics.density).roundToInt()
        for (index in 0 until items.childCount) {
            val row = items.getChildAt(index)
            assertTrue("Row $index clipped below content area", row.bottom <= body.height)
            val check = row.findViewById<View>(R.id.row_check)
            val title = row.findViewById<TextView>(R.id.row_title)
            assertTrue("Completion touch width", check.width >= touchSize)
            assertTrue("Completion touch height", check.height >= touchSize)
            assertTrue("Completion overlaps edit", check.right <= title.left)
            assertTrue("Body text clipped vertically",
                title.height >= title.paint.fontMetrics.bottom - title.paint.fontMetrics.top)
        }
        val footer = root.findViewById<View>(R.id.widget_footer)
        if (footer.visibility == View.VISIBLE) {
            val stack = root.findViewById<View>(R.id.widget_stack)
            assertTrue("Footer clipped", footer.bottom <= stack.height - stack.paddingBottom)
        }
    }

    @Test fun actualRemoteViewsFitReferenceSizesAndCapacityBoundaries() {
        instrumentation.runOnMainSync {
            val context = context()
            val expected = mapOf(110 to (1 to 1), 144 to (1 to 1), 163 to (1 to 1), 164 to (1 to 2),
                170 to (1 to 2), 179 to (1 to 2), 180 to (2 to 2),
                200 to (2 to 2), 211 to (2 to 2), 212 to (2 to 3),
                220 to (2 to 3), 231 to (2 to 3), 232 to (3 to 3),
                275 to (3 to 4), 276 to (3 to 4), 320 to (3 to 5), 380 to (5 to 6))
            expected.forEach { (height, counts) ->
                for (compact in listOf(false, true)) {
                    val root = render(context, SizeF(320f, height.toFloat()), compact)
                    val count = root.findViewById<LinearLayout>(R.id.widget_rows).childCount
                    assertEquals("H=$height compact=$compact", if (compact) counts.second else counts.first, count)
                    assertGeometry(context, root)
                }
            }
        }
    }

    @Test fun largeFontsAndNarrowWidthsKeepFullRowsAndSeparateTouchTargets() {
        instrumentation.runOnMainSync {
            for (scale in listOf(1f, 1.3f, 2f)) {
                val context = context(scale)
                for (width in listOf(180f, 240f, 320f)) {
                    for (height in listOf(144f, 170f, 220f, 320f)) {
                        for (compact in listOf(false, true)) {
                            val root = render(context, SizeF(width, height), compact)
                            assertGeometry(context, root)
                            val first = root.findViewById<LinearLayout>(R.id.widget_rows).getChildAt(0)
                            if (width < 240 && first != null) {
                                assertEquals(View.GONE, first.findViewById<View>(R.id.row_quantity).visibility)
                            }
                            if (scale >= 1.3f || width < 240) {
                                assertEquals(View.GONE, root.findViewById<View>(R.id.widget_count).visibility)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun compactFeedbackReusesHeaderAndRetainsAddAndAccessibleDetails() {
        instrumentation.runOnMainSync {
            val context = context()
            val layout = WidgetLayout.resolve(context, SizeF(180f, 170f), WidgetDensity.COMPACT)
            val pending = PendingIntent.getActivity(context, 0, Intent(context, com.wanglemai.app.ui.main.MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val intents = WidgetIntents(pending, pending, pending, { pending }, { pending }, pending)
            val renderer = WidgetRenderer(context)
            val undo = UndoToken("item-1", false, null, 1)
            val views = renderer.render(list, rows, 120, undo, null, false, layout, intents)
            val root = views.apply(context, FrameLayout(context))
            measure(context, SizeF(180f, 170f), root)
            assertEquals("已完成 · 撤销", root.findViewById<TextView>(R.id.widget_title).text.toString())
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_header_add).visibility)
            assertEquals(View.GONE, root.findViewById<View>(R.id.widget_footer).visibility)
            assertTrue(root.findViewById<View>(R.id.row_title).contentDescription.contains("数量 2 盒"))
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.row_pin).visibility)
            assertGeometry(context, root)
            val error = renderer.error(true, layout, pending, pending).apply(context, FrameLayout(context))
            measure(context, SizeF(180f, 170f), error)
            assertTrue(error.findViewById<View>(R.id.widget_open).hasOnClickListeners())
            assertTrue(error.findViewById<View>(R.id.widget_header_add).hasOnClickListeners())
        }
    }

    @Test fun captureNativeDesignReference() {
        instrumentation.runOnMainSync {
            val context = context()
            for (compact in listOf(false, true)) {
                for (dark in listOf(false, true)) {
                    val root = render(context, SizeF(320f, 320f), compact, dark)
                    val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(image)
                    canvas.drawColor(Color.rgb(173, 189, 174))
                    root.draw(canvas)
                    File(baseContext.getExternalFilesDir(null), "widget-refined-$compact-$dark.png")
                        .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    image.recycle()
                }
            }
        }
    }

    @Test fun ultraCompactFitsMoreRowsAndScalesTextWithoutClipping() {
        instrumentation.runOnMainSync {
            val expected = mapOf(110 to 1, 144 to 2, 170 to 3, 180 to 3, 220 to 4, 320 to 7)
            for (scale in listOf(1f, 1.3f, 2f)) {
                val context = context(scale)
                for ((height, count) in expected) {
                    val size = SizeF(300f, height.toFloat())
                    val layout = WidgetLayout.resolve(context, size, WidgetDensity.ULTRA_COMPACT)
                    val root = WidgetRenderer(context).render(list, rows, rows.size, null, null, false, layout)
                        .apply(context, FrameLayout(context))
                    measure(context, size, root)
                    assertGeometry(context, root, 36)
                    assertEquals(View.GONE, root.findViewById<View>(R.id.widget_footer).visibility)
                    if (scale == 1f) {
                        assertEquals("Ultra H=$height", count,
                            root.findViewById<LinearLayout>(R.id.widget_rows).childCount)
                    }
                }
            }
        }
    }

    @Test fun providerMetadataAllowsTwoRowsOnCompactLauncherGrids() {
        val manager = AppWidgetManager.getInstance(baseContext)
        val providers = manager.installedProviders.filter {
            it.provider.packageName == baseContext.packageName
        }
        assertEquals(2, providers.size)
        val density = baseContext.resources.displayMetrics.density
        for (provider in providers) {
            assertEquals(110, (provider.minHeight / density).roundToInt())
            assertEquals(56, (provider.minResizeHeight / density).roundToInt())
            // A 70dp grid cell with 16dp host padding previously rounded 144dp up to 3 rows.
            val hostPadding = android.appwidget.AppWidgetHostView.getDefaultPaddingForWidget(
                baseContext, provider.provider, null)
            val requestedDp = (provider.minResizeHeight + hostPadding.top + hostPadding.bottom) / density
            assertTrue(kotlin.math.ceil(requestedDp / 70f) <= 2)
        }
    }

    @Test fun normalRefreshClearsFeedbackAndReplacesErrorLayout() = runBlocking {
        val container = baseContext.app.container
        container.repository.initialize()
        val device = UiDevice.getInstance(instrumentation)
        val userId = android.os.Process.myUid() / 100000
        device.executeShellCommand("appwidget grantbind --package com.wanglemai.app --user $userId")
        var scenario: ActivityScenario<WidgetHarnessActivity>? = null
        var widgetId = -1
        val message = "刷新前的临时提示"
        try {
            scenario = ActivityScenario.launch(Intent(baseContext, WidgetHarnessActivity::class.java))
            scenario.onActivity { widgetId = it.widgetIds.first() }
            container.widgets.refresh(widgetId)
            container.widgets.showMessage(widgetId, message)
            assertTrue(device.wait(Until.hasObject(By.descContains(message)), 10_000))
            container.widgets.refresh(widgetId)
            assertTrue(device.wait(Until.gone(By.descContains(message)), 10_000))

            container.widgets.showError(widgetId)
            assertTrue(device.wait(Until.hasObject(By.text("暂时无法读取 · 点此重试")), 10_000))
            container.widgets.refresh(widgetId)
            assertTrue(device.wait(Until.gone(By.text("暂时无法读取 · 点此重试")), 10_000))
        } finally {
            scenario?.close()
            device.executeShellCommand("appwidget revokebind --package com.wanglemai.app --user $userId")
            if (widgetId > 0) container.widgets.forget(widgetId)
        }
    }

    @Test fun resizingRealHostSelectsResponsiveLayoutsAndCompactCapacity() = runBlocking {
        val container = baseContext.app.container
        container.repository.initialize()
        val listId = (container.repository.createList(ListKind.TODO, "尺寸验证")
            as MutationResult.Success).itemIds.single()
        val prefix = "尺寸事项${System.currentTimeMillis()}"
        repeat(8) { index ->
            container.repository.create(ItemInput(listId, "$prefix-$index"), UUID.randomUUID().toString())
        }
        val device = UiDevice.getInstance(instrumentation)
        val userId = android.os.Process.myUid() / 100000
        device.executeShellCommand("appwidget grantbind --package com.wanglemai.app --user $userId")
        var scenario: ActivityScenario<WidgetHarnessActivity>? = null
        var widgetId = -1
        try {
            scenario = ActivityScenario.launch(Intent(baseContext, WidgetHarnessActivity::class.java))
            scenario.onActivity { widgetId = it.widgetIds.first() }
            AppWidgetManager.getInstance(baseContext).updateAppWidgetOptions(widgetId, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf<SizeF>())
            })
            val fallback = WidgetSizes.forWidget(baseContext, widgetId, false).single()
            assertEquals(320f, fallback.width, 0f)
            assertEquals(180f, fallback.height, 0f)
            val expected = listOf(170 to (1 to 2), 180 to (2 to 2), 220 to (2 to 3), 320 to (3 to 5))
            for ((height, counts) in expected) {
                for (compact in listOf(false, true)) {
                    container.db.dao().saveConfig(WidgetConfig(widgetId,
                        "com.wanglemai.app.widget.ListSmallProvider", listId, ThemeMode.LIGHT.name,
                        (if (compact) WidgetDensity.COMPACT else WidgetDensity.COMFORTABLE).name, 0))
                    scenario.onActivity { it.resizeWidget(widgetId, 300, height) }
                    container.widgets.refresh(widgetId)
                    val expectedCount = if (compact) counts.second else counts.first
                    val deadline = System.currentTimeMillis() + 10_000
                    while (device.findObjects(By.textStartsWith(prefix)).size != expectedCount &&
                        System.currentTimeMillis() < deadline) delay(100)
                    assertEquals("Host H=$height compact=$compact", expectedCount,
                        device.findObjects(By.textStartsWith(prefix)).size)
                }
            }
        } finally {
            scenario?.close()
            device.executeShellCommand("appwidget revokebind --package com.wanglemai.app --user $userId")
            if (widgetId > 0) container.db.dao().deleteConfig(widgetId)
            container.db.dao().widgetItems(listId, 12).forEach {
                container.repository.delete(it.id, it.version)
            }
            container.repository.list(listId)?.let { container.repository.deleteListAndMoveToDefault(it.id, it.version) }
        }
    }
}
