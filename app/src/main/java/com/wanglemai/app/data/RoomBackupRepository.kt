package com.wanglemai.app.data

import androidx.room.withTransaction
import com.wanglemai.app.data.db.AppDatabase
import com.wanglemai.app.data.db.ItemEntity
import com.wanglemai.app.data.db.ListEntity
import com.wanglemai.app.domain.*

class RoomBackupRepository(private val db: AppDatabase) : BackupRepository {
    private val dao = db.dao()

    private suspend fun ensureDefaults() {
        val time = System.currentTimeMillis()
        dao.insertLists(listOf(
            ListEntity(DEFAULT_TODO, "TODO", "随手待办", "随手待办", true, time, time),
            ListEntity(DEFAULT_SHOPPING, "SHOPPING", "日常采购", "日常采购", true, time, time)
        ))
    }

    override suspend fun exportJson(): String = db.withTransaction {
        ensureDefaults()
        val document = BackupDocument(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            app = BACKUP_APP_ID,
            exportedAt = System.currentTimeMillis(),
            lists = dao.lists().map { list ->
                BackupList(list.id, ListKind.valueOf(list.kind), list.name, list.isDefault,
                    list.createdAt, list.updatedAt)
            },
            items = dao.backupItems().map { item ->
                BackupItem(item.id, item.listId, item.title, item.note,
                    item.quantityDecimal, item.unit, item.completed, item.pinned, item.sortKey,
                    item.createdAt, item.updatedAt, item.completedAt)
            }
        )
        BackupCodec.encode(document)
    }

    override suspend fun validateAndPreview(json: String): BackupPreview {
        val document = BackupCodec.decode(json)
        return db.withTransaction {
            previewLocked(document)
        }
    }

    private suspend fun previewLocked(document: BackupDocument): BackupPreview {
        val localLists = dao.lists().associateBy { it.id }
        val localKinds = localLists.mapValues { ListKind.valueOf(it.value.kind) }.toMutableMap()
        val autoDefaults = HashSet<String>()
        if (localKinds.putIfAbsent(DEFAULT_TODO, ListKind.TODO) == null) autoDefaults += DEFAULT_TODO
        if (localKinds.putIfAbsent(DEFAULT_SHOPPING, ListKind.SHOPPING) == null) autoDefaults += DEFAULT_SHOPPING
        document.lists.forEach { list ->
            val existing = localKinds[list.id]
            if (existing != null && existing != list.kind) {
                throw BackupException("清单 ID 与本机类型冲突")
            }
            localKinds[list.id] = list.kind
        }
        document.items.forEach { item ->
            val kind = localKinds[item.listId] ?: throw BackupException("记录引用的清单不存在")
            requireTodoFields(kind, item)
        }
        val localItemIds = dao.allItemIds().toHashSet()
        val newLists = document.lists.count {
            !localLists.containsKey(it.id) && it.id !in autoDefaults
        }
        val newItems = document.items.count { it.id !in localItemIds }
        return BackupPreview(
            totalLists = document.lists.size,
            totalItems = document.items.size,
            newLists = newLists,
            newItems = newItems,
            skipped = document.lists.size + document.items.size - newLists - newItems
        )
    }

    override suspend fun merge(document: BackupDocument): BackupMergeReport = db.withTransaction {
        ensureDefaults()
        val localLists = dao.lists().associateBy { it.id }
        val kinds = localLists.mapValues { ListKind.valueOf(it.value.kind) }.toMutableMap()
        document.lists.forEach { list ->
            val existing = kinds[list.id]
            if (existing != null && existing != list.kind) {
                throw BackupException("清单 ID 与本机类型冲突")
            }
            kinds[list.id] = list.kind
        }
        document.items.forEach { item ->
            val kind = kinds[item.listId] ?: throw BackupException("记录引用的清单不存在")
            requireTodoFields(kind, item)
        }

        var addedLists = 0
        var addedItems = 0
        var skipped = 0
        val namesByKind = localLists.values.groupBy { ListKind.valueOf(it.kind) }
            .mapValues { (_, lists) -> lists.map { Validation.listNameKey(it.name) }.toMutableSet() }
            .toMutableMap()

        document.lists.forEach { list ->
            if (dao.list(list.id) != null) {
                skipped++
                return@forEach
            }
            val baseName = Validation.normalizeListName(list.name)
            val name = uniqueListName(baseName, namesByKind.getOrPut(list.kind) { mutableSetOf() })
            dao.insertList(ListEntity(
                id = list.id,
                kind = list.kind.name,
                name = name,
                normalizedName = Validation.listNameKey(name),
                isDefault = list.isDefault,
                createdAt = list.createdAt,
                updatedAt = list.updatedAt,
                version = 1
            ))
            namesByKind.getValue(list.kind).add(Validation.listNameKey(name))
            addedLists++
        }

        val localItemIds = dao.allItemIds().toHashSet()
        document.items.forEach { item ->
            if (!localItemIds.add(item.id)) {
                skipped++
                return@forEach
            }
            val list = dao.list(item.listId) ?: throw BackupException("记录引用的清单不存在")
            val kind = ListKind.valueOf(list.kind)
            val normalized = try {
                Validation.normalize(ItemInput(item.listId, item.title, item.note,
                    item.quantityDecimal.orEmpty(), item.unit.orEmpty(), item.pinned), kind)
            } catch (e: ValidationException) {
                throw BackupException("记录字段无效：${e.message}")
            }
            dao.insertItem(ItemEntity(
                id = item.id,
                listId = item.listId,
                title = normalized.title,
                note = normalized.note,
                quantityDecimal = normalized.quantity.ifEmpty { null },
                unit = normalized.unit.ifEmpty { null },
                completed = item.completed,
                pinned = item.pinned,
                sortKey = item.sortKey,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                completedAt = item.completedAt,
                deletedAt = null,
                version = 1
            ))
            addedItems++
        }
        BackupMergeReport(addedLists, addedItems, skipped)
    }

    private fun requireTodoFields(kind: ListKind, item: BackupItem) {
        if (kind == ListKind.TODO && (item.quantityDecimal != null || item.unit != null)) {
            throw BackupException("待办不能含购物数量")
        }
    }

    private fun uniqueListName(base: String, existing: MutableSet<String>): String {
        if (Validation.listNameKey(base) !in existing) return base
        var index = 2
        while (true) {
            val suffix = "（导入$index）"
            val keep = (20 - Validation.codePoints(suffix)).coerceAtLeast(0)
            val candidate = takeCodePoints(base, keep) + suffix
            if (Validation.listNameKey(candidate) !in existing) return candidate
            index++
        }
    }

    private fun takeCodePoints(value: String, limit: Int): String {
        var end = 0
        var count = 0
        while (end < value.length && count < limit) {
            end += Character.charCount(value.codePointAt(end))
            count++
        }
        return value.substring(0, end)
    }
}
