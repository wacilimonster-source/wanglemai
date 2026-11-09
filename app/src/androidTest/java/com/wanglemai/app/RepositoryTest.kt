package com.wanglemai.app

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wanglemai.app.data.RoomBackupRepository
import com.wanglemai.app.data.RoomItemRepository
import com.wanglemai.app.data.db.*
import com.wanglemai.app.domain.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomItemRepository
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repo = RoomItemRepository(db); repo.initialize()
    }
    @After fun close() { db.close() }
    private suspend fun create(title: String = "牛奶"): Item {
        val result = repo.create(ItemInput(DEFAULT_SHOPPING, title, quantity = "2", unit = "盒"), UUID.randomUUID().toString()) as MutationResult.Success
        return repo.getItem(result.itemIds.single())!!
    }
    private suspend fun createIn(listId: String, title: String): Item {
        val result = repo.create(ItemInput(listId, title), UUID.randomUUID().toString())
            as MutationResult.Success
        return repo.getItem(result.itemIds.single())!!
    }
    private suspend fun newList(kind: ListKind, name: String): String {
        val result = repo.createList(kind, name) as MutationResult.Success
        return result.itemIds.single()
    }
    private fun newDatabase() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()

    @Test fun defaultInitializationIsIdempotent() = runBlocking {
        repo.initialize(); repo.initialize(); assertEquals(2, db.dao().lists().size)
    }
    @Test fun duplicateCommandCreatesOnlyOneItem() = runBlocking {
        val command = UUID.randomUUID().toString()
        val first = repo.create(ItemInput(DEFAULT_TODO, "取快递"), command)
        val second = repo.create(ItemInput(DEFAULT_TODO, "取快递"), command)
        assertEquals(first, second); assertEquals(1, db.dao().count(DEFAULT_TODO))
    }
    @Test fun staleUpdateCannotOverwriteNewText() = runBlocking {
        val item = create()
        assertTrue(repo.update(item.id, item.version, ItemInput(DEFAULT_SHOPPING, "鲜牛奶")) is MutationResult.Success)
        assertEquals(MutationResult.Conflict, repo.update(item.id, item.version, ItemInput(DEFAULT_SHOPPING, "旧内容")))
        assertEquals("鲜牛奶", repo.getItem(item.id)!!.title)
    }
    @Test fun completionReplayDoesNotToggleAndUndoHasVersionGuard() = runBlocking {
        val item = create()
        val result = repo.setCompleted(item.id, item.version, true) as MutationResult.Success
        assertTrue(repo.setCompleted(item.id, item.version, true) is MutationResult.Success)
        assertTrue(repo.getItem(item.id)!!.completed)
        assertTrue(repo.undoCompletion(result.undo!!) is MutationResult.Success)
        assertFalse(repo.getItem(item.id)!!.completed)
        assertEquals(MutationResult.Conflict, repo.undoCompletion(result.undo!!))
    }
    @Test fun widgetUndoIsClearedAfterRecordEdited() = runBlocking {
        val item = create(); val dao = db.dao()
        dao.saveConfig(WidgetConfig(101, "test-provider", DEFAULT_SHOPPING))
        repo.setCompleted(item.id, item.version, true, 101)
        val token = dao.undo(101)!!
        val fresh = repo.getItem(item.id)!!
        repo.update(fresh.id, fresh.version, ItemInput(DEFAULT_SHOPPING, "新内容"))
        assertNull(dao.undo(101)); assertEquals(MutationResult.Conflict, repo.undoWidgetCompletion(101, token.commandId))
    }
    @Test fun searchTreatsSqlWildcardsLiterally() = runBlocking {
        create("100%_完成"); create("普通记录")
        assertEquals(1, repo.observeItems(DEFAULT_SHOPPING, false, "%_").first().size)
    }
    @Test fun deletionIsSoftAndCannotBeEdited() = runBlocking {
        val item = create(); assertTrue(repo.delete(item.id, item.version) is MutationResult.Success)
        assertNull(repo.getItem(item.id)); assertEquals(0, db.dao().count(DEFAULT_SHOPPING))
        assertEquals(MutationResult.NotFound, repo.update(item.id, item.version, ItemInput(DEFAULT_SHOPPING, "旧文本")))
    }
    @Test fun recordsSurviveClosingAndReopeningDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "framework-persistence-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val first = RoomItemRepository(disk); first.initialize()
            val created = first.create(ItemInput(DEFAULT_TODO, "持久化验证"), UUID.randomUUID().toString()) as MutationResult.Success
            disk.close()
            disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals("持久化验证", RoomItemRepository(disk).getItem(created.itemIds.single())!!.title)
        } finally { disk.close(); context.deleteDatabase(name) }
    }

    @Test fun batchAcceptsLastRepresentableSortKey() = runBlocking {
        val item = createIn(DEFAULT_TODO, "边界记录")
        db.dao().moveItem(item.id, DEFAULT_TODO, Long.MAX_VALUE - 1024, 0)
        val result = repo.createBatch(BatchInput(DEFAULT_TODO, "最后一条"),
            UUID.randomUUID().toString()) as MutationResult.Success
        assertEquals(Long.MAX_VALUE, repo.getItem(result.itemIds.single())!!.sortKey)
    }

    @Test fun batchRejectsOverflowWithoutWritingItemsOrCommand() = runBlocking {
        val item = createIn(DEFAULT_TODO, "边界记录")
        val command = UUID.randomUUID().toString()
        // The first entry fits exactly. The second must reject the entire batch.
        db.dao().moveItem(item.id, DEFAULT_TODO, Long.MAX_VALUE - 1024, 0)
        val result = repo.createBatch(BatchInput(DEFAULT_TODO, "第一条\n第二条"), command)
        assertTrue(result is MutationResult.ValidationError)
        assertEquals(1, db.dao().count(DEFAULT_TODO))
        assertNull(db.dao().command(command))

        // MAX - 1023 is already unsafe for even a single entry.
        db.dao().moveItem(item.id, DEFAULT_TODO, Long.MAX_VALUE - 1023, 0)
        assertTrue(repo.createBatch(BatchInput(DEFAULT_TODO, "溢出记录"), command)
            is MutationResult.ValidationError)
        assertEquals(1, db.dao().count(DEFAULT_TODO))
        assertNull(db.dao().command(command))
    }

    @Test fun batchIsAtomicAndCommandReplayIsIdempotent() = runBlocking {
        val command = UUID.randomUUID().toString()
        val input = BatchInput(DEFAULT_TODO, " 苹果 \r\n\r\n香蕉\n", note = "周末买", pinned = true)
        val first = repo.createBatch(input, command) as MutationResult.Success
        assertEquals(2, first.itemIds.size)
        assertEquals(first, repo.createBatch(input, command))
        val items = repo.observeItems(DEFAULT_TODO, false, "").first()
        assertEquals(listOf("苹果", "香蕉"), items.sortedBy { it.sortKey }.map { it.title })
        assertTrue(items.all { it.note == "周末买" && it.pinned })
        assertEquals(listOf(1024L, 2048L), items.sortedBy { it.sortKey }.map { it.sortKey })

        val invalid = repo.createBatch(BatchInput(DEFAULT_TODO, "第一条\n" + "😀".repeat(201)),
            UUID.randomUUID().toString()) as MutationResult.ValidationError
        assertEquals(2, invalid.line)
        assertEquals(2, repo.observeItems(DEFAULT_TODO, false, "").first().size)

        val tooMany = repo.createBatch(BatchInput(DEFAULT_TODO,
            (1..51).joinToString("\n") { "记录$it" }), UUID.randomUUID().toString())
        assertTrue(tooMany is MutationResult.ValidationError)
        assertEquals(2, repo.observeItems(DEFAULT_TODO, false, "").first().size)
    }

    @Test fun listNamesAreUniquePerKindAndDefaultsCannotBeDeleted() = runBlocking {
        assertTrue(repo.deleteListAndMoveToDefault(DEFAULT_TODO, 1) is MutationResult.ValidationError)
        val work = newList(ListKind.TODO, " 工作 ")
        assertEquals("工作", repo.list(work)!!.name)
        assertTrue(repo.createList(ListKind.TODO, "工作") is MutationResult.ValidationError)
        assertTrue(repo.createList(ListKind.TODO, "工作  ") is MutationResult.ValidationError)
        val read = newList(ListKind.TODO, "ABC")
        assertTrue(repo.createList(ListKind.TODO, "abc") is MutationResult.ValidationError)
        assertTrue(repo.renameList(read, repo.list(read)!!.version, "工作") is MutationResult.ValidationError)
        assertTrue(repo.renameList(read, 99, "阅读") is MutationResult.Conflict)
        assertTrue(repo.createList(ListKind.SHOPPING, "工作") is MutationResult.Success)
        assertTrue(repo.createList(ListKind.TODO, "😀".repeat(21)) is MutationResult.ValidationError)

        val defaultVersion = repo.list(DEFAULT_TODO)!!.version
        assertTrue(repo.renameList(DEFAULT_TODO, defaultVersion, "我的待办") is MutationResult.Success)
        assertEquals("我的待办", repo.list(DEFAULT_TODO)!!.name)
    }

    @Test fun deletingListMovesItemsAndWidgetsAndDrafts() = runBlocking {
        val dao = db.dao()
        val custom = newList(ListKind.TODO, "工作")
        val active = createIn(custom, "写周报")
        val completed = createIn(custom, "回邮件")
        val softDeleted = createIn(custom, "旧记录")
        val widgetBound = createIn(custom, "整理文件")
        repo.setCompleted(completed.id, completed.version, true)
        repo.delete(softDeleted.id, softDeleted.version)
        dao.saveConfig(WidgetConfig(201, "test-provider", custom))
        repo.setCompleted(widgetBound.id, widgetBound.version, true, 201)
        assertNotNull(dao.undo(201))
        repo.saveDraft(Draft("create:$custom", custom, "草稿", "", "", "", false, false, null))
        repo.saveDraft(Draft("edit:${active.id}", custom, "编辑草稿", "", "", "", false, false, active.version))

        assertTrue(repo.renameList(custom, 1, "家务") is MutationResult.Success)
        val source = repo.list(custom)!!
        assertEquals(2, source.version)
        val result = repo.deleteListAndMoveToDefault(custom, source.version) as MutationResult.Success
        assertEquals(3, result.movedCount)
        assertNull(repo.list(custom))

        val moved = dao.allItemsInList(DEFAULT_TODO)
        assertEquals(4, moved.size)
        assertTrue(moved.all { it.listId == DEFAULT_TODO })
        assertEquals(4, moved.map { it.id }.toSet().size)
        assertTrue(moved.map { it.sortKey }.zipWithNext().all { (a, b) -> a < b })
        assertNotNull(moved.firstOrNull { it.id == softDeleted.id })
        assertTrue(moved.first { it.id == active.id }.version > active.version)

        val config = dao.config(201)!!
        assertEquals(DEFAULT_TODO, config.listId)
        assertNull(dao.undo(201))

        val movedCreateDraft = repo.getDraft("create:$DEFAULT_TODO")
        assertNotNull(movedCreateDraft)
        assertEquals(DEFAULT_TODO, movedCreateDraft!!.listId)
        assertNull(repo.getDraft("create:$custom"))
        val movedEditDraft = repo.getDraft("edit:${active.id}")
        assertNotNull(movedEditDraft)
        assertEquals(DEFAULT_TODO, movedEditDraft!!.listId)
        assertEquals(active.version, movedEditDraft.baseItemVersion)
    }

    @Test fun pinnedAndRestoredItemsKeepFixedOrder() = runBlocking {
        val listId = newList(ListKind.TODO, "排序")
        val a = createIn(listId, "A")
        val b = createIn(listId, "B")
        val c = createIn(listId, "C")
        fun titles(items: List<Item>) = items.map { it.title }
        assertEquals(listOf("A", "B", "C"),
            titles(repo.observeItems(listId, false, "").first()))
        val pinned = repo.getItem(b.id)!!
        assertTrue(repo.update(pinned.id, pinned.version,
            ItemInput(listId, pinned.title, pinned = true)) is MutationResult.Success)
        assertEquals(listOf("B", "A", "C"),
            titles(repo.observeItems(listId, false, "").first()))

        repo.setCompleted(a.id, repo.getItem(a.id)!!.version, true)
        delay(20)
        repo.setCompleted(c.id, repo.getItem(c.id)!!.version, true)
        assertEquals(listOf("C", "A"),
            titles(repo.observeItems(listId, true, "").first()))

        val restoredA = repo.setCompleted(a.id, repo.getItem(a.id)!!.version, false)
            as MutationResult.Success
        assertNotNull(restoredA.undo)
        assertEquals(listOf("B", "A"),
            titles(repo.observeItems(listId, false, "").first()))

        val d = repo.create(ItemInput(listId, "D"), UUID.randomUUID().toString())
            as MutationResult.Success
        assertEquals(listOf("B", "A", "D"),
            titles(repo.observeItems(listId, false, "").first()))
        assertTrue(repo.getItem(d.itemIds.single())!!.sortKey >
            repo.getItem(c.id)!!.sortKey)
    }

    @Test fun draftsAndPreferencesRoundTrip() = runBlocking {
        val draft = Draft("create:$DEFAULT_SHOPPING", DEFAULT_SHOPPING, "牛奶", "备注",
            "2", "盒", true, true, null)
        repo.saveDraft(draft)
        assertEquals(draft, repo.getDraft(draft.draftKey))
        val item = create()
        val editDraft = Draft("edit:${item.id}", DEFAULT_SHOPPING, "编辑", "", "", "",
            false, false, item.version)
        repo.saveDraft(editDraft)
        assertEquals(editDraft, repo.getDraft(editDraft.draftKey))
        repo.deleteDraft(draft.draftKey)
        assertNull(repo.getDraft(draft.draftKey))

        repo.setPreference(PREF_THEME, ThemeMode.DARK.name)
        assertEquals("DARK", repo.getPreference(PREF_THEME))
        repo.setPreference(PREF_THEME, ThemeMode.LIGHT.name)
        assertEquals("LIGHT", repo.getPreference(PREF_THEME))
    }

    @Test fun backupExportAndImportIsIdempotentAndRenamesConflicts() = runBlocking {
        val custom = newList(ListKind.TODO, "工作")
        createIn(custom, "写周报")
        create("牛奶")
        val backup = RoomBackupRepository(db)
        val json = backup.exportJson()
        val document = BackupCodec.decode(json)
        assertEquals(3, document.lists.size)
        assertEquals(2, document.items.size)

        val previewSame = backup.validateAndPreview(json)
        assertEquals(0, previewSame.newLists)
        assertEquals(0, previewSame.newItems)
        val sameReport = backup.merge(document)
        assertEquals(0, sameReport.addedLists)
        assertEquals(0, sameReport.addedItems)
        assertEquals(3, db.dao().lists().size)
        assertEquals(2, db.dao().backupItems().size)

        val db2 = newDatabase()
        try {
            val repo2 = RoomItemRepository(db2)
            repo2.initialize()
            repo2.createList(ListKind.TODO, "工作")
            val backup2 = RoomBackupRepository(db2)
            val preview2 = backup2.validateAndPreview(json)
            assertEquals(1, preview2.newLists)
            assertEquals(2, preview2.newItems)
            val report = backup2.merge(BackupCodec.decode(json))
            assertEquals(1, report.addedLists)
            assertEquals(2, report.addedItems)
            assertEquals(2, report.skipped)
            val importedList = db2.dao().list(custom)!!
            assertTrue(importedList.name.endsWith("（导入2）"))
            assertEquals("写周报", repo2.observeItems(custom, false, "").first().single().title)
            assertEquals(1, db2.dao().item(repo2.observeItems(custom, false, "").first().single().id)!!.version)
        } finally { db2.close() }
    }

    @Test fun backupNameSuffixNeverExceedsTwentyCodePoints() = runBlocking {
        val longName = "一二三四五六七八九十一二三四五六七八九十"
        assertEquals(20, Validation.codePoints(longName))
        newList(ListKind.TODO, longName)
        val otherId = UUID.randomUUID().toString()
        val document = BackupDocument(
            BACKUP_SCHEMA_VERSION, BACKUP_APP_ID, System.currentTimeMillis(),
            lists = listOf(
                BackupList(DEFAULT_TODO, ListKind.TODO, "随手待办", true, 1, 1),
                BackupList(DEFAULT_SHOPPING, ListKind.SHOPPING, "日常采购", true, 1, 1),
                BackupList(otherId, ListKind.TODO, longName, false, 1, 1)
            ), items = emptyList()
        )
        RoomBackupRepository(db).merge(document)
        val imported = db.dao().list(otherId)!!
        assertEquals(20, Validation.codePoints(imported.name))
        assertTrue(imported.name.endsWith("（导入2）"))
    }

    @Test fun backupRejectsKindConflictAndDanglingReferencesWithoutWriting() = runBlocking {
        val custom = newList(ListKind.TODO, "工作")
        createIn(custom, "写周报")
        val backup = RoomBackupRepository(db)
        val json = JSONObject(backup.exportJson())
        val lists = json.getJSONArray("lists")
        for (i in 0 until lists.length()) {
            val list = lists.getJSONObject(i)
            if (list.getString("id") == custom) list.put("kind", "SHOPPING")
        }
        val conflicting = BackupCodec.decode(json.toString())
        val listsBefore = db.dao().lists().size
        val itemsBefore = db.dao().allItemIds().size
        assertTrue(runCatching { backup.validateAndPreview(json.toString()) }.exceptionOrNull() is BackupException)
        assertTrue(runCatching { backup.merge(conflicting) }.exceptionOrNull() is BackupException)
        assertEquals(listsBefore, db.dao().lists().size)
        assertEquals(itemsBefore, db.dao().allItemIds().size)

        val newListId = UUID.randomUUID().toString()
        val dangling = BackupDocument(
            BACKUP_SCHEMA_VERSION, BACKUP_APP_ID, System.currentTimeMillis(),
            lists = listOf(
                BackupList(DEFAULT_TODO, ListKind.TODO, "随手待办", true, 1, 1),
                BackupList(DEFAULT_SHOPPING, ListKind.SHOPPING, "日常采购", true, 1, 1),
                BackupList(newListId, ListKind.TODO, "新清单", false, 1, 1)
            ),
            items = listOf(
                BackupItem(UUID.randomUUID().toString(), newListId, "合法记录", "", null, null,
                    false, false, 1024, 1, 1, null),
                BackupItem(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "悬空记录", "",
                    null, null, false, false, 2048, 1, 1, null)
            )
        )
        assertTrue(runCatching { backup.merge(dangling) }.exceptionOrNull() is BackupException)
        assertNull(db.dao().list(newListId))
        assertEquals(listsBefore, db.dao().lists().size)
        assertEquals(itemsBefore, db.dao().allItemIds().size)
    }
}
