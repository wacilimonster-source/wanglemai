package com.wanglemai.app

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.wanglemai.app.domain.DEFAULT_TODO
import com.wanglemai.app.ui.main.MainActivity
import com.wanglemai.app.widget.ListLargeProvider
import com.wanglemai.app.widget.ListSmallProvider
import com.wanglemai.app.widget.WidgetPinCoordinator
import com.wanglemai.app.widget.WidgetPinStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WidgetPinTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val container get() = context.app.container

    private suspend fun await(block: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!block() && System.currentTimeMillis() < deadline) delay(100)
        assertTrue("Widget pin condition timed out", block())
    }

    private fun tap(text: String) {
        val node = device.wait(Until.findObject(By.text(text)), 10_000) ?: error("Missing $text")
        node.click()
    }

    @Test fun inAppRequestConfirmsAdditionAndOpensConfiguration(): Unit = runBlocking {
        container.pinning.dismiss()
        await { container.pinning.state.value.status == WidgetPinStatus.IDLE }
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            tap("设置")
            tap("添加标准清单")
            if (device.hasObject(By.text("已开启，继续添加"))) tap("已开启，继续添加")
            await { container.pinning.state.value.status == WidgetPinStatus.REQUESTING }
            device.waitForIdle()
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "pin-launcher.xml"))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "pin-launcher.png"))
            assertNotEquals(context.packageName, device.currentPackageName)
            device.pressBack()
            await { container.pinning.state.value.status == WidgetPinStatus.UNCONFIRMED }
            tap("添加标准清单")
            if (device.hasObject(By.text("已开启，继续添加"))) tap("已开启，继续添加")
            tap("Add to home screen")
            await { container.pinning.state.value.status == WidgetPinStatus.ADDED }
            val id = container.pinning.state.value.widgetId!!
            assertNotNull(AppWidgetManager.getInstance(context).getAppWidgetInfo(id))
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            tap("配置这个小部件")
            assertNotNull(device.wait(Until.findObject(By.text("这张卡片，记什么？")), 10_000))
            device.pressBack()
            val launcher = device.executeShellCommand(
                "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                .lineSequence().last { it.contains("/") }.substringBefore("/").trim()
            await { device.currentPackageName == launcher }
            device.waitForIdle()
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "pin-added.xml"))
            device.takeScreenshot(File(context.getExternalFilesDir(null), "pin-added.png"))
            // The real launcher owns this sample and retains it for desktop visual inspection.
        } finally {
            device.pressBack()
            container.pinning.dismiss()
            await { container.pinning.state.value.status == WidgetPinStatus.IDLE }
            scenario.close()
        }
    }

    @Test fun callbackRejectsOldOrWrongProviderAndHostFallbackFindsNewInstance(): Unit = runBlocking {
        val userId = android.os.Process.myUid() / 100000
        device.executeShellCommand("appwidget grantbind --package com.wanglemai.app --user $userId")
        val host = AppWidgetHost(context, 93812)
        val manager = AppWidgetManager.getInstance(context)
        val ids = mutableListOf<Int>()
        fun bind(large: Boolean): Int {
            val id = host.allocateAppWidgetId()
            ids += id
            val provider = if (large) ListLargeProvider::class.java else ListSmallProvider::class.java
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, provider)))
            return id
        }
        container.pinning.dismiss()
        await { container.pinning.state.value.status == WidgetPinStatus.IDLE }
        val oldSmall = bind(false)
        val wrongProvider = bind(true)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            assertNotNull(device.wait(Until.findObject(By.text("＋ 记一条")), 10_000))
            container.pinning.request(ListSmallProvider::class.java)
            await { container.pinning.state.value.status == WidgetPinStatus.REQUESTING }
            val token = container.pinning.state.value.token!!
            val pendingRestore = WidgetPinCoordinator(context, container.db, container.repository,
                container.widgets, context.app.scope)
            assertEquals(WidgetPinStatus.UNCONFIRMED, pendingRestore.state.value.status)
            assertNull(pendingRestore.state.value.widgetId)
            container.pinning.onPinned("wrong-token", wrongProvider)
            container.pinning.onPinned(token, AppWidgetManager.INVALID_APPWIDGET_ID)
            container.pinning.onPinned(token, oldSmall)
            container.pinning.onPinned(token, wrongProvider)
            assertNotEquals(WidgetPinStatus.ADDED, container.pinning.state.value.status)
            val newId = bind(false)
            // No success callback is delivered for this new host instance.
            container.pinning.refresh()
            await { container.pinning.state.value.status == WidgetPinStatus.ADDED }
            assertEquals(newId, container.pinning.state.value.widgetId)
            assertEquals(DEFAULT_TODO, container.db.dao().config(newId)?.listId)
            val restored = WidgetPinCoordinator(context, container.db, container.repository,
                container.widgets, context.app.scope)
            restored.refresh()
            assertEquals(newId, restored.state.value.widgetId)
            host.deleteAppWidgetId(newId)
            container.pinning.refresh()
            assertEquals(WidgetPinStatus.IDLE, container.pinning.state.value.status)
        } finally {
            device.pressBack()
            container.pinning.dismiss()
            host.deleteHost()
            ids.forEach { container.db.dao().deleteConfig(it) }
            scenario.close()
            device.executeShellCommand("appwidget revokebind --package com.wanglemai.app --user $userId")
        }
    }

    @Test fun permissionHelpFallsBackToAppDetailsAndManualGuideReturnsHome(): Unit = runBlocking {
        container.pinning.dismiss()
        await { container.pinning.state.value.status == WidgetPinStatus.IDLE }
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            tap("设置")
            tap("添加无响应？")
            tap("打开权限设置")
            assertNotNull(device.wait(Until.findObject(By.pkg("com.android.settings").text("App info")), 10_000))
            device.waitForIdle()
            device.pressBack()
            tap("知道了")
            assertEquals(WidgetPinStatus.IDLE, container.pinning.state.value.status)
            tap("从系统桌面添加")
            tap("返回桌面")
            val launcher = device.executeShellCommand(
                "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
                .lineSequence().last { it.contains("/") }.substringBefore("/").trim()
            await { device.currentPackageName == launcher }
        } finally { scenario.close() }
    }
}
