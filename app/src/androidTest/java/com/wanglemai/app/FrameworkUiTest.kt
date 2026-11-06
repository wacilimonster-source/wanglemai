package com.wanglemai.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.wanglemai.app.data.db.WidgetConfig
import com.wanglemai.app.debug.WidgetHarnessActivity
import com.wanglemai.app.domain.*
import com.wanglemai.app.ui.main.MainActivity
import com.wanglemai.app.widget.WidgetConfigureActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FrameworkUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val container get() = context.app.container

    private fun find(text: String): UiObject2 = device.wait(Until.findObject(By.text(text)), 10_000)
        ?: error("Cannot find UI text: $text")
    private fun focusedEditor(): UiObject2 =
        device.wait(Until.findObject(By.clazz("android.widget.EditText").focused(true)), 10_000)
            ?: error("Cannot find focused editor field")
    private fun tap(text: String) {
        var stale: StaleObjectException? = null
        repeat(4) {
            try {
                find(text).click()
                return
            } catch (e: StaleObjectException) {
                stale = e
                Thread.sleep(250)
            }
        }
        throw stale ?: IllegalStateException("Cannot tap UI text: $text")
    }
    private fun awaitCondition(block: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) { if (block()) return; Thread.sleep(100) }
        assertTrue("Condition timed out", block())
    }
    private suspend fun awaitRepo(block: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (block()) return
            delay(100)
        }
        assertTrue("Repository condition timed out", block())
    }
    private suspend fun resetSelection() {
        container.repository.initialize()
        container.repository.setPreference(PREF_LAST_KIND, ListKind.TODO.name)
        container.repository.setPreference(PREF_LAST_LIST_TODO, DEFAULT_TODO)
        container.repository.setPreference(PREF_LAST_LIST_SHOPPING, DEFAULT_SHOPPING)
    }
    private fun awaitEditorClosed() {
        awaitCondition {
            device.findObjects(By.text("想到，就记一条。")).isEmpty() &&
                device.findObjects(By.text("编辑这一条")).isEmpty()
        }
    }
    private suspend fun deleteBySearch(listId: String, query: String) {
        (container.repository.observeItems(listId, false, query).first() +
            container.repository.observeItems(listId, true, query).first())
            .forEach { container.repository.delete(it.id, it.version) }
    }
    private suspend fun deleteAllItems(listId: String) {
        repeat(2) {
            container.repository.observeItems(listId, false, "").first()
                .forEach { container.repository.delete(it.id, it.version) }
            container.repository.observeItems(listId, true, "").first()
                .forEach { container.repository.delete(it.id, it.version) }
        }
    }

    @Test fun appCanCreateEditAndPersistRecord() = runBlocking {
        resetSelection()
        val title = "基础框架验证${System.currentTimeMillis()}"
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            tap("＋ 记一条")
            find("想到，就记一条。")
            focusedEditor().text = title
            device.wait(Until.findObject(By.text(title)), 10_000)
            tap("保存"); tap(title); find("编辑这一条")
            focusedEditor().text = "$title 已修改"
            tap("保存修改"); find("$title 已修改")
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "framework-main.png"))
            val item = container.repository.observeItems(DEFAULT_TODO, false, title).first().single()
            assertEquals("$title 已修改", container.repository.getItem(item.id)!!.title)
            container.mutate { container.repository.delete(item.id, item.version) }
        } finally { scenario.close() }
        Unit
    }

    @Test fun batchEntryAndDraftRestore() = runBlocking {
        resetSelection()
        container.repository.deleteDraft("create:$DEFAULT_TODO")
        val stamp = System.currentTimeMillis()
        val first = "BatchA$stamp"
        val second = "BatchB$stamp"
        val draftTitle = "草稿$stamp"
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            tap("＋ 记一条")
            find("想到，就记一条。")
            tap("每行生成一条")
            find("每行一条")
            val titleField = focusedEditor()
            titleField.click()
            device.executeShellCommand("input text $first")
            device.executeShellCommand("input keyevent 66")
            device.executeShellCommand("input text $second")
            find("将生成 2 条")
            tap("保存")
            awaitRepo {
                container.repository.observeItems(DEFAULT_TODO, false, first).first().size == 1 &&
                    container.repository.observeItems(DEFAULT_TODO, false, second).first().size == 1
            }
            tap("＋ 记一条")
            find("想到，就记一条。")
            focusedEditor().text = draftTitle
            Thread.sleep(700)
            tap("取消")
            awaitRepo {
                container.repository.getDraft("create:$DEFAULT_TODO")?.title == draftTitle
            }
            tap("＋ 记一条")
            find("已恢复上次草稿")
            tap("丢弃")
            awaitRepo { container.repository.getDraft("create:$DEFAULT_TODO") == null }
            tap("取消")
            awaitEditorClosed()
        } finally {
            scenario.close()
            deleteBySearch(DEFAULT_TODO, first)
            deleteBySearch(DEFAULT_TODO, second)
            container.repository.deleteDraft("create:$DEFAULT_TODO")
        }
        Unit
    }

    @Test fun manageListsCreateRenameDeleteAndMigrate() = runBlocking {
        resetSelection()
        val stamp = System.currentTimeMillis() % 1_000_000
        val listName = "自动化$stamp"
        val renamed = "改名$stamp"
        val itemTitle = "迁移记录$stamp"
        var createdListId: String? = null
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            tap("管理清单")
            tap("＋ 新建待办 清单")
            focusedEditor().text = listName
            tap("保存")
            find(listName)
            createdListId = container.repository.lists().first { it.name == listName }.id
            tap("← 返回清单")

            tap("＋ 记一条")
            find("想到，就记一条。")
            focusedEditor().text = itemTitle
            tap("保存")
            find(itemTitle)

            tap("管理清单")
            find("＋ 新建待办 清单")
            device.findObjects(By.text("改名")).maxBy { it.visibleBounds.centerY() }.click()
            focusedEditor().text = renamed
            tap("保存")
            find(renamed)
            device.findObjects(By.text("删除")).filter { it.isEnabled }
                .maxBy { it.visibleBounds.centerY() }.click()
            find("删除「$renamed」？")
            tap("删除并移动")
            awaitCondition { device.findObjects(By.text(renamed)).isEmpty() }
            tap("← 返回清单")

            val migrated = container.repository.observeItems(DEFAULT_TODO, false, itemTitle).first().single()
            assertEquals(DEFAULT_TODO, migrated.listId)
            container.repository.delete(migrated.id, migrated.version)
        } finally {
            scenario.close()
            val listId = createdListId
            if (listId != null) {
                val list = container.repository.list(listId)
                if (list != null) {
                    deleteAllItems(listId)
                    container.repository.deleteListAndMoveToDefault(list.id, list.version)
                }
            }
            deleteBySearch(DEFAULT_TODO, itemTitle)
        }
        Unit
    }

    @Test fun saveAndContinueKeepsTheListAndClearsFields() = runBlocking {
        resetSelection()
        val stamp = System.currentTimeMillis()
        val first = "继续一$stamp"
        val second = "继续二$stamp"
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            tap("＋ 记一条")
            find("想到，就记一条。")
            val field = focusedEditor()
            field.text = first
            tap("保存并继续")
            awaitRepo { container.repository.observeItems(DEFAULT_TODO, false, first).first().size == 1 }
            find("想到，就记一条。")
            val nextField = focusedEditor()
            assertEquals("", nextField.text)
            nextField.text = second
            tap("保存")
            awaitRepo { container.repository.observeItems(DEFAULT_TODO, false, second).first().size == 1 }
            awaitEditorClosed()
        } finally {
            scenario.close()
            deleteBySearch(DEFAULT_TODO, first)
            deleteBySearch(DEFAULT_TODO, second)
        }
        Unit
    }

    @Test fun movingShoppingItemToTodoAsksBeforeClearingQuantity() = runBlocking {
        val stamp = System.currentTimeMillis()
        val title = "移动验证$stamp"
        val created = container.repository.create(ItemInput(DEFAULT_SHOPPING, title,
            note = "保留备注", quantity = "2", unit = "盒"), UUID.randomUUID().toString())
            as MutationResult.Success
        val itemId = created.itemIds.single()
        val scenario = ActivityScenario.launch<com.wanglemai.app.ui.editor.QuickEntryActivity>(
            com.wanglemai.app.ui.editor.QuickEntryActivity.editIntent(context, itemId))
        try {
            find("编辑这一条")
            val picker = device.findObject(By.textContains("待买 · 日常采购"))
            assertNotNull(picker)
            picker.click()
            tap("待办 · 随手待办")
            device.wait(Until.findObject(By.text("待办 · 随手待办  ▾")), 10_000)
            tap("保存修改")
            find("移到待办清单？")
            tap("移动并保存")
            awaitRepo {
                val item = container.repository.getItem(itemId)
                item != null && item.listId == DEFAULT_TODO && item.quantityDecimal == null &&
                    item.unit == null && item.note == "保留备注"
            }
            awaitEditorClosed()
        } finally {
            scenario.close()
            container.repository.getItem(itemId)?.let {
                container.repository.delete(it.id, it.version)
            }
        }
        Unit
    }

    @Test fun staleDraftSaveShowsConflictAndCanReloadLatest() = runBlocking {
        val stamp = System.currentTimeMillis()
        val title = "冲突记录$stamp"
        val draftTitle = "我的草稿$stamp"
        val externalTitle = "外部新内容$stamp"
        val created = container.repository.create(ItemInput(DEFAULT_TODO, title),
            UUID.randomUUID().toString()) as MutationResult.Success
        val itemId = created.itemIds.single()
        val scenario = ActivityScenario.launch<com.wanglemai.app.ui.editor.QuickEntryActivity>(
            com.wanglemai.app.ui.editor.QuickEntryActivity.editIntent(context, itemId))
        try {
            find("编辑这一条")
            val field = focusedEditor()
            field.text = draftTitle
            Thread.sleep(600)
            val latest = container.repository.getItem(itemId)!!
            container.repository.update(latest.id, latest.version,
                ItemInput(DEFAULT_TODO, externalTitle))
            tap("保存修改")
            find("这条记录已在别处更新，你的草稿已保留。")
            assertNotNull(device.findObject(By.text(draftTitle)))
            tap("载入最新记录")
            awaitCondition { device.findObjects(By.text(externalTitle)).isNotEmpty() }
            tap("取消")
            awaitRepo { container.repository.getDraft("edit:$itemId") == null }
            awaitEditorClosed()
        } finally {
            scenario.close()
            container.repository.getItem(itemId)?.let {
                container.repository.delete(it.id, it.version)
            }
        }
        Unit
    }

    @Test fun validationErrorKeepsInput() = runBlocking {
        resetSelection()
        container.repository.deleteDraft("create:$DEFAULT_TODO")
        val tooLong = "a".repeat(201)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            tap("＋ 记一条")
            find("想到，就记一条。")
            val field = focusedEditor()
            field.text = tooLong
            tap("保存")
            find("标题最多 200 字符")
            assertNotNull(device.findObject(By.text(tooLong)))
            tap("取消")
            awaitEditorClosed()
        } finally {
            scenario.close()
            container.repository.deleteDraft("create:$DEFAULT_TODO")
        }
        Unit
    }

    @Test fun backHidesImeBeforeKeepingDraft() = runBlocking {
        resetSelection()
        val stamp = System.currentTimeMillis()
        val title = "返回草稿$stamp"
        container.repository.deleteDraft("create:$DEFAULT_TODO")
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            tap("＋ 记一条")
            find("想到，就记一条。")
            focusedEditor().text = title
            Thread.sleep(700)
            device.pressBack()
            Thread.sleep(500)
            assertNotNull("First Back must hide IME, not close the editor",
                device.findObject(By.text("想到，就记一条。")))
            device.pressBack()
            awaitRepo { container.repository.getDraft("create:$DEFAULT_TODO")?.title == title }
            find("＋ 记一条")
            tap("＋ 记一条")
            find("已恢复上次草稿")
            tap("丢弃")
            awaitRepo { container.repository.getDraft("create:$DEFAULT_TODO") == null }
            tap("取消")
            awaitEditorClosed()
            assertTrue(container.repository.observeItems(DEFAULT_TODO, false, title).first().isEmpty())
        } finally {
            scenario.close()
            container.repository.deleteDraft("create:$DEFAULT_TODO")
        }
        Unit
    }

    @Test fun settingsThemeAndLiteralSearch() = runBlocking {
        resetSelection()
        val stamp = System.currentTimeMillis()
        val special = "100%_主题$stamp"
        val ordinary = "普通记录$stamp"
        val specialId = (container.repository.create(ItemInput(DEFAULT_TODO, special),
            UUID.randomUUID().toString()) as MutationResult.Success).itemIds.single()
        val ordinaryId = (container.repository.create(ItemInput(DEFAULT_TODO, ordinary),
            UUID.randomUUID().toString()) as MutationResult.Success).itemIds.single()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            tap("设置")
            tap("浅色")
            awaitRepo { container.repository.getPreference(PREF_THEME) == ThemeMode.LIGHT.name }
            tap("深色")
            awaitRepo { container.repository.getPreference(PREF_THEME) == ThemeMode.DARK.name }
            tap("待办")
            val search = device.wait(Until.findObject(By.clazz("android.widget.EditText").enabled(true)), 10_000)
                ?: error("Cannot find search field")
            search.text = "%_"
            find(special)
            awaitCondition { device.findObjects(By.text(ordinary)).isEmpty() }
            tap("待买")
            tap("待办")
            find("搜索这份清单…")
        } finally {
            scenario.close()
            listOf(specialId, ordinaryId).forEach {
                container.repository.getItem(it)?.let { item ->
                    container.repository.delete(item.id, item.version)
                }
            }
            container.repository.setPreference(PREF_THEME, ThemeMode.SYSTEM.name)
        }
        Unit
    }

    @Test fun initialWidgetConfigureCancelLeavesNoConfig() = runBlocking {
        container.repository.initialize()
        val userId = android.os.Process.myUid() / 100000
        device.executeShellCommand("appwidget grantbind --package com.wanglemai.app --user $userId")
        var scenario: ActivityScenario<WidgetHarnessActivity>? = null
        var id = -1
        try {
            scenario = ActivityScenario.launch(Intent(context, WidgetHarnessActivity::class.java))
            scenario.onActivity { id = it.widgetIds.first() }
            container.db.dao().deleteConfig(id)
            context.startActivity(Intent(context, WidgetConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            find("这张卡片，记什么？")
            tap("取消")
            Thread.sleep(800)
            assertNull(container.db.dao().config(id))
        } finally {
            runCatching { scenario?.close() }
            runCatching {
                device.executeShellCommand("appwidget revokebind --package com.wanglemai.app --user $userId")
            }
            if (id > 0) runCatching { container.db.dao().deleteConfig(id) }
        }
        Unit
    }

    @Test fun smallAndLargeWidgetsKeepIndependentConfigurations() = runBlocking {
        resetSelection()
        val stamp = System.currentTimeMillis()
        val customName = "大清单$stamp"
        val customList = (container.repository.createList(ListKind.TODO, customName)
            as MutationResult.Success).itemIds.single()
        val todoTitles = (1..4).map { "大条目$stamp-$it" }
        todoTitles.forEach {
            container.repository.create(ItemInput(customList, it), UUID.randomUUID().toString())
        }
        val shoppingTitle = "采购条目$stamp"
        val shopping = (container.repository.create(
            ItemInput(DEFAULT_SHOPPING, shoppingTitle, quantity = "1", unit = "件", pinned = true),
            UUID.randomUUID().toString()) as MutationResult.Success).itemIds.single()

        val userId = android.os.Process.myUid() / 100000
        device.executeShellCommand("appwidget grantbind --package com.wanglemai.app --user $userId")
        var scenario: ActivityScenario<WidgetHarnessActivity>? = null
        var smallId = -1
        var largeId = -1
        try {
            scenario = ActivityScenario.launch(Intent(context, WidgetHarnessActivity::class.java)
                .putExtra("include_large", true))
            scenario.onActivity {
                smallId = it.widgetIds.single()
                largeId = it.largeWidgetId ?: -1
            }
            assertTrue(smallId > 0 && largeId > 0)
            val dao = container.db.dao()
            dao.saveConfig(WidgetConfig(smallId, "com.wanglemai.app.widget.ListSmallProvider",
                DEFAULT_SHOPPING, ThemeMode.DARK.name, WidgetDensity.COMPACT.name))
            dao.saveConfig(WidgetConfig(largeId, "com.wanglemai.app.widget.ListLargeProvider",
                customList, ThemeMode.LIGHT.name, WidgetDensity.COMFORTABLE.name))
            container.widgets.refresh(smallId)
            container.widgets.refresh(largeId)

            awaitCondition { device.findObjects(By.text("• $shoppingTitle")).isNotEmpty() }
            assertNotNull(device.findObject(By.text(customName)))
            awaitCondition { device.findObjects(By.text(todoTitles[3])).isNotEmpty() }
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "widget-translucent.png"))

            val shoppingDescription = "标记『$shoppingTitle』为已购买"
            assertNotNull(device.findObject(By.desc(shoppingDescription)))
            device.findObject(By.desc(shoppingDescription)).click()
            awaitRepo { container.repository.getItem(shopping)!!.completed }
            awaitCondition { device.findObjects(By.text("已完成 · 撤销")).size == 1 }
            tap("已完成 · 撤销")
            awaitRepo { !container.repository.getItem(shopping)!!.completed }
            awaitCondition { device.findObjects(By.text("已完成 · 撤销")).isEmpty() }

            context.startActivity(Intent(context, WidgetConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, smallId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            find("这张卡片，记什么？")
            tap(customName)
            tap("浅色")
            tap("舒适")
            tap("保存并添加")
            awaitRepo {
                val config = container.db.dao().config(smallId)
                config?.listId == customList && config.theme == ThemeMode.LIGHT.name &&
                    config.density == WidgetDensity.COMFORTABLE.name
            }
            val largeConfig = container.db.dao().config(largeId)
            assertNotNull("small=$smallId large=$largeId configs=${container.db.dao().configs()}",
                largeConfig)
            assertEquals(customList, largeConfig!!.listId)
            assertEquals(ThemeMode.LIGHT.name, largeConfig.theme)
            assertEquals(WidgetDensity.COMFORTABLE.name, largeConfig.density)
        } finally {
            runCatching { scenario?.close() }
            runCatching {
                device.executeShellCommand("appwidget revokebind --package com.wanglemai.app --user $userId")
            }
            runCatching { container.db.dao().deleteConfig(smallId) }
            runCatching { container.db.dao().deleteConfig(largeId) }
            runCatching { deleteAllItems(customList) }
            runCatching { deleteBySearch(DEFAULT_SHOPPING, shoppingTitle) }
            runCatching {
                container.repository.list(customList)?.let {
                    container.repository.deleteListAndMoveToDefault(it.id, it.version)
                }
            }
        }
        Unit
    }

    @Test fun widgetKeepsTitleOneRowAndAddEntryAtCurrentFontScale() = runBlocking {
        container.repository.initialize()
        val stamp = System.currentTimeMillis()
        val listName = "字号$stamp"
        val listId = (container.repository.createList(ListKind.TODO, listName)
            as MutationResult.Success).itemIds.single()
        val title = "字号验证$stamp"
        container.repository.create(ItemInput(listId, title), UUID.randomUUID().toString())
        val userId = android.os.Process.myUid() / 100000
        device.executeShellCommand("appwidget grantbind --package com.wanglemai.app --user $userId")
        var scenario: ActivityScenario<WidgetHarnessActivity>? = null
        var ids: List<Int> = emptyList()
        try {
            scenario = ActivityScenario.launch(Intent(context, WidgetHarnessActivity::class.java)
                .putExtra("include_large", true))
            scenario.onActivity {
                ids = it.widgetIds + listOfNotNull(it.largeWidgetId)
            }
            val dao = container.db.dao()
            ids.forEach { dao.saveConfig(WidgetConfig(it,
                if (it == ids.last()) "com.wanglemai.app.widget.ListLargeProvider"
                else "com.wanglemai.app.widget.ListSmallProvider", listId)) }
            ids.forEach { container.widgets.refresh(it) }
            awaitCondition { device.findObjects(By.text(title)).size == 2 }
            assertNotNull(device.findObject(By.text(listName)))
            assertNotNull(device.findObject(By.text("＋ 记一条")))
            assertNotNull(device.findObject(By.text("查看全部 ›")))
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "widget-font-scale.png"))
        } finally {
            runCatching { scenario?.close() }
            runCatching {
                device.executeShellCommand("appwidget revokebind --package com.wanglemai.app --user $userId")
            }
            ids.forEach { runCatching { container.db.dao().deleteConfig(it) } }
            runCatching { deleteAllItems(listId) }
            runCatching {
                container.repository.list(listId)?.let {
                    container.repository.deleteListAndMoveToDefault(it.id, it.version)
                }
            }
        }
        Unit
    }

    @Test fun realWidgetHostUpdatesTwoInstancesAndRunsPendingIntents() = runBlocking {
        container.repository.initialize()
        val stamp = System.currentTimeMillis()
        val listName = "组件清单$stamp"
        val listId = (container.repository.createList(ListKind.TODO, listName)
            as MutationResult.Success).itemIds.single()
        val title = "小部件验证$stamp"
        val created = container.repository.create(ItemInput(listId, title),
            java.util.UUID.randomUUID().toString()) as MutationResult.Success
        val itemId = created.itemIds.single()
        // The appwidget CLI on this emulator requires a numeric user; "current" silently misses owner grants.
        val userId = android.os.Process.myUid() / 100000
        device.executeShellCommand("appwidget grantbind --package com.wanglemai.app --user $userId")
        var scenario: ActivityScenario<WidgetHarnessActivity>? = null
        var ids: List<Int> = emptyList()
        try {
            scenario = ActivityScenario.launch(Intent(context, WidgetHarnessActivity::class.java))
            scenario.onActivity { ids = it.widgetIds.toList() }
            for (id in ids) container.db.dao().saveConfig(WidgetConfig(id, "com.wanglemai.app.widget.ListSmallProvider", listId))
            for (id in ids) container.widgets.refresh(id)
            awaitCondition { device.findObjects(By.text(title)).size == 2 }
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "framework-widget-host.png"))
            val completion = device.findObject(By.desc("标记『$title』为已完成"))
            assertNotNull(completion); completion.click()
            awaitCondition { runBlocking { container.repository.getItem(itemId)!!.completed } }
            tap("已完成 · 撤销")
            awaitCondition { runBlocking { !container.repository.getItem(itemId)!!.completed } }
            awaitCondition { device.findObjects(By.text(title)).size == 2 }
            tap("＋ 记一条"); find("想到，就记一条。")
            focusedEditor().text = "$title 快速新增"
            tap("保存")
            awaitCondition { device.findObjects(By.text("$title 快速新增")).size == 2 }
            val extra = container.repository.observeItems(listId, false, "$title 快速新增").first().single()
            container.mutate { container.repository.delete(extra.id, extra.version) }
        } finally {
            scenario?.close()
            device.executeShellCommand("appwidget revokebind --package com.wanglemai.app --user $userId")
            ids.forEach { runCatching { container.db.dao().deleteConfig(it) } }
            container.repository.getItem(itemId)?.let { container.repository.delete(it.id, it.version) }
            deleteAllItems(listId)
            container.repository.list(listId)?.let {
                container.repository.deleteListAndMoveToDefault(it.id, it.version)
            }
        }
        Unit
    }
}
