package com.wanglemai.app.data

import androidx.room.withTransaction
import com.wanglemai.app.data.db.*
import com.wanglemai.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.util.UUID

class RoomItemRepository(private val db: AppDatabase) : ItemRepository {
    private val dao = db.dao()

    override suspend fun initialize() {
        ensureDefaults()
    }

    private suspend fun ensureDefaults() {
        val time = System.currentTimeMillis()
        dao.insertLists(listOf(
            ListEntity(DEFAULT_TODO, "TODO", "随手待办", "随手待办", true, time, time),
            ListEntity(DEFAULT_SHOPPING, "SHOPPING", "日常采购", "日常采购", true, time, time)
        ))
    }

    override fun observeLists() = dao.observeLists().map { rows -> rows.map { it.model() } }
    override fun observeListCounts() = dao.observeListCounts().map { rows -> rows.associate { it.listId to it.count } }
    override fun observeActiveCount(listId: String) = dao.observeActiveCount(listId)
    override fun observeItems(listId: String, completed: Boolean, search: String) =
        dao.observeItems(listId, completed, search).map { rows -> rows.map { it.model() } }

    override suspend fun lists() = dao.lists().map { it.model() }
    override suspend fun list(id: String) = dao.list(id)?.model()
    override suspend fun getItem(id: String) = dao.item(id)?.model()

    private suspend fun mutate(block: suspend () -> MutationResult): MutationResult = try {
        initialize()
        db.withTransaction { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ValidationException) {
        MutationResult.ValidationError(e.message, e.field, e.line)
    } catch (e: IllegalArgumentException) {
        MutationResult.ValidationError(e.message ?: "输入无效")
    } catch (_: Exception) {
        MutationResult.StorageError
    }

    private fun requireCommandId(commandId: String) {
        if (runCatching { UUID.fromString(commandId) }.isFailure) {
            throw ValidationException("提交标识无效")
        }
    }

    private suspend fun replayCommand(commandId: String): MutationResult.Success? {
        val stored = dao.command(commandId) ?: return null
        val ids = JSONArray(stored.resultIdsJson)
        return MutationResult.Success((0 until ids.length()).map(ids::getString))
    }

    private suspend fun nextSortKey(listId: String): Long {
        val max = dao.maxSortKey(listId)
        if (max > Long.MAX_VALUE - 1024) throw ValidationException("清单排序已满，请新建清单")
        return max + 1024
    }

    override suspend fun create(input: ItemInput, commandId: String) = mutate {
        requireCommandId(commandId)
        replayCommand(commandId)?.let { return@mutate it }
        val list = dao.list(input.listId) ?: return@mutate MutationResult.NotFound
        val normalized = Validation.normalize(input, ListKind.valueOf(list.kind))
        val id = UUID.randomUUID().toString()
        val time = System.currentTimeMillis()
        dao.insertItem(ItemEntity(id, normalized.listId, normalized.title, normalized.note,
            normalized.quantity.ifEmpty { null }, normalized.unit.ifEmpty { null }, false,
            normalized.pinned, nextSortKey(normalized.listId), time, time))
        dao.insertCommand(AppliedCommand(commandId, JSONArray(listOf(id)).toString(), time))
        MutationResult.Success(listOf(id))
    }

    override suspend fun createBatch(input: BatchInput, commandId: String) = mutate {
        requireCommandId(commandId)
        replayCommand(commandId)?.let { return@mutate it }
        val list = dao.list(input.listId) ?: return@mutate MutationResult.NotFound
        val normalized = Validation.normalizeBatch(input, ListKind.valueOf(list.kind))
        val time = System.currentTimeMillis()
        var sortKey = dao.maxSortKey(input.listId)
        val entities = normalized.map { item ->
            if (sortKey > Long.MAX_VALUE - 1024) throw ValidationException("清单排序已满，请新建清单")
            sortKey += 1024
            ItemEntity(UUID.randomUUID().toString(), item.listId, item.title, item.note, null, null,
                false, item.pinned, sortKey, time, time)
        }
        dao.insertItems(entities)
        val ids = entities.map { it.id }
        dao.insertCommand(AppliedCommand(commandId, JSONArray(ids).toString(), time))
        MutationResult.Success(ids)
    }

    override suspend fun update(id: String, expectedVersion: Long, input: ItemInput) = mutate {
        val old = dao.item(id) ?: return@mutate MutationResult.NotFound
        if (old.version != expectedVersion) return@mutate MutationResult.Conflict
        val list = dao.list(input.listId) ?: return@mutate MutationResult.NotFound
        val normalized = Validation.normalize(input, ListKind.valueOf(list.kind))
        if (dao.edit(id, expectedVersion, normalized.listId, normalized.title, normalized.note,
                normalized.quantity.ifEmpty { null }, normalized.unit.ifEmpty { null },
                normalized.pinned, System.currentTimeMillis()) == 0) {
            return@mutate MutationResult.Conflict
        }
        dao.clearItemUndo(id)
        MutationResult.Success(listOf(id))
    }

    override suspend fun setCompleted(
        id: String, expectedVersion: Long, completed: Boolean, originWidgetId: Int?
    ) = mutate {
        val old = dao.item(id) ?: return@mutate MutationResult.NotFound
        if (originWidgetId != null && dao.config(originWidgetId)?.listId != old.listId) {
            return@mutate MutationResult.Conflict
        }
        if (old.completed == completed) return@mutate MutationResult.Success()
        if (old.version != expectedVersion) return@mutate MutationResult.Conflict
        val time = System.currentTimeMillis()
        if (dao.complete(id, expectedVersion, completed, if (completed) time else null, time) == 0) {
            return@mutate MutationResult.Conflict
        }
        dao.clearItemUndo(id)
        val token = UndoToken(id, old.completed, old.completedAt, old.version + 1)
        if (originWidgetId != null && dao.config(originWidgetId) != null) {
            dao.saveUndo(WidgetUndo(originWidgetId, UUID.randomUUID().toString(), id,
                old.completed, old.completedAt, old.version + 1))
        }
        MutationResult.Success(listOf(id), token)
    }

    private suspend fun undoInside(token: UndoToken): MutationResult {
        val old = dao.item(token.itemId) ?: return MutationResult.NotFound
        if (old.version != token.expectedVersion) return MutationResult.Conflict
        if (dao.complete(old.id, old.version, token.completed, token.completedAt,
                System.currentTimeMillis()) == 0) return MutationResult.Conflict
        dao.clearItemUndo(old.id)
        return MutationResult.Success(listOf(old.id))
    }

    override suspend fun undoCompletion(token: UndoToken) = mutate { undoInside(token) }

    override suspend fun undoWidgetCompletion(widgetId: Int, commandId: String) = mutate {
        val undo = dao.undo(widgetId) ?: return@mutate MutationResult.Conflict
        if (undo.commandId != commandId) return@mutate MutationResult.Conflict
        val result = undoInside(undo.token())
        dao.clearWidgetUndo(widgetId)
        result
    }

    override suspend fun delete(id: String, expectedVersion: Long) = mutate {
        dao.item(id) ?: return@mutate MutationResult.NotFound
        if (dao.delete(id, expectedVersion, System.currentTimeMillis()) == 0) {
            return@mutate MutationResult.Conflict
        }
        dao.clearItemUndo(id)
        MutationResult.Success(listOf(id))
    }

    override suspend fun createList(kind: ListKind, name: String) = mutate {
        val normalizedName = Validation.normalizeListName(name)
        val key = Validation.listNameKey(normalizedName)
        if (dao.listByNormalizedName(kind.name, key) != null) {
            return@mutate MutationResult.ValidationError("这个名字已经用过了", "name")
        }
        val time = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        dao.insertList(ListEntity(id, kind.name, normalizedName, key, false, time, time))
        MutationResult.Success(itemIds = listOf(id), message = "已创建清单")
    }

    override suspend fun renameList(id: String, expectedVersion: Long, name: String) = mutate {
        val list = dao.list(id) ?: return@mutate MutationResult.NotFound
        if (list.version != expectedVersion) return@mutate MutationResult.Conflict
        val normalizedName = Validation.normalizeListName(name)
        val key = Validation.listNameKey(normalizedName)
        val duplicate = dao.listByNormalizedName(list.kind, key)
        if (duplicate != null && duplicate.id != id) {
            return@mutate MutationResult.ValidationError("这个名字已经用过了", "name")
        }
        if (dao.renameList(id, expectedVersion, normalizedName, key, System.currentTimeMillis()) == 0) {
            return@mutate MutationResult.Conflict
        }
        MutationResult.Success(message = "清单已改名")
    }

    override suspend fun deleteListAndMoveToDefault(id: String, expectedVersion: Long) = mutate {
        val source = dao.list(id) ?: return@mutate MutationResult.NotFound
        if (source.isDefault) return@mutate MutationResult.ValidationError("默认清单不可删除", "list")
        if (source.version != expectedVersion) return@mutate MutationResult.Conflict
        val target = dao.lists().firstOrNull { it.kind == source.kind && it.isDefault }
            ?: return@mutate MutationResult.StorageError
        val time = System.currentTimeMillis()
        val items = dao.allItemsInList(source.id)
        val affectedWidgets = dao.configs().filter { it.listId == source.id }.map { it.appWidgetId }
        var sortKey = dao.maxSortKey(target.id)
        items.forEach { item ->
            if (sortKey > Long.MAX_VALUE - 1024) throw ValidationException("清单排序已满")
            sortKey += 1024
            dao.moveItem(item.id, target.id, sortKey, time)
        }
        if (items.isNotEmpty()) {
            dao.clearItemUndos(items.map { it.id })
        }
        dao.rebindConfigs(source.id, target.id, time)
        if (affectedWidgets.isNotEmpty()) dao.clearUndoForWidgets(affectedWidgets)

        val drafts = dao.drafts()
        drafts.forEach { entity ->
            val draft = DraftCodec.decode(entity) ?: return@forEach
            when {
                entity.draftKey == "create:${source.id}" -> {
                    val targetKey = "create:${target.id}"
                    val moved = draft.copy(draftKey = targetKey, listId = target.id)
                    if (dao.draft(targetKey) == null) dao.saveDraft(DraftEntity(
                        targetKey, DraftCodec.encode(moved), null, entity.updatedAt))
                    dao.deleteDraft(entity.draftKey)
                }
                entity.draftKey.startsWith("edit:") && draft.listId == source.id -> {
                    dao.saveDraft(DraftEntity(entity.draftKey,
                        DraftCodec.encode(draft.copy(listId = target.id)),
                        entity.baseItemVersion, entity.updatedAt))
                }
            }
        }
        dao.deleteList(source.id)
        MutationResult.Success(
            itemIds = items.map { it.id },
            movedCount = items.count { it.deletedAt == null },
            message = "记录已移到「${target.name}」"
        )
    }

    override suspend fun getDraft(draftKey: String) = dao.draft(draftKey)?.let(DraftCodec::decode)

    override suspend fun saveDraft(draft: Draft) {
        dao.saveDraft(DraftEntity(draft.draftKey, DraftCodec.encode(draft),
            draft.baseItemVersion, System.currentTimeMillis()))
    }

    override suspend fun deleteDraft(draftKey: String) = dao.deleteDraft(draftKey)

    override suspend fun getPreference(key: String) = dao.preference(key)?.value

    override suspend fun setPreference(key: String, value: String) {
        dao.savePreference(PreferenceEntity(key, value))
    }
}
