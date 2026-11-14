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
            ListEntity(DEFAULT_TODO, "随手待办", "随手待办", true, time, time)
        ))
    }

    override suspend fun exportJson(): String = db.withTransaction {
        ensureDefaults()
        val document = BackupDocument(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            app = BACKUP_APP_ID,
            exportedAt = System.currentTimeMillis(),
            lists = dao.lists().map { list ->
                BackupList(list.id, "TODO", list.name, list.isDefault,
                    list.createdAt, list.updatedAt)
            },
            items = dao.backupItems().map { item ->
                BackupItem(item.id, item.listId, item.title, item.note,
                    item.completed, item.pinned, item.sortKey,
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

    /**
     * Legacy backups (pre-v0.4.0) may contain SHOPPING-kind lists; those lists are dropped and
     * their records merge into the default todo list, matching the on-device v1->v2 migration.
     */
    private fun legacyShoppingListIds(document: BackupDocument): Set<String> =
        document.lists.filter { it.kind == "SHOPPING" }.map { it.id }.toSet()

    private fun effectiveListId(item: BackupItem, legacyShopping: Set<String>): String =
        if (item.listId in legacyShopping) DEFAULT_TODO else item.listId

    private suspend fun previewLocked(document: BackupDocument): BackupPreview {
        val legacyShopping = legacyShoppingListIds(document)
        val importedLists = document.lists.filter { it.id !in legacyShopping }
        val localLists = dao.lists().associateBy { it.id }
        val knownListIds = localLists.keys + importedLists.map { it.id } + DEFAULT_TODO
        document.items.forEach { item ->
            if (effectiveListId(item, legacyShopping) !in knownListIds) {
                throw BackupException("记录引用的清单不存在")
            }
        }
        val localItemIds = dao.allItemIds().toHashSet()
        val newLists = importedLists.count { !localLists.containsKey(it.id) && it.id != DEFAULT_TODO }
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
        val legacyShopping = legacyShoppingListIds(document)
        val importedLists = document.lists.filter { it.id !in legacyShopping }
        val localLists = dao.lists().associateBy { it.id }
        val knownListIds = localLists.keys + importedLists.map { it.id } + DEFAULT_TODO
        document.items.forEach { item ->
            if (effectiveListId(item, legacyShopping) !in knownListIds) {
                throw BackupException("记录引用的清单不存在")
            }
        }

        var addedLists = 0
        var addedItems = 0
        var skipped = 0
        val existingNames = localLists.values.map { Validation.listNameKey(it.name) }.toMutableSet()

        importedLists.forEach { list ->
            if (dao.list(list.id) != null) {
                skipped++
                return@forEach
            }
            val baseName = Validation.normalizeListName(list.name)
            val name = uniqueListName(baseName, existingNames)
            dao.insertList(ListEntity(
                id = list.id,
                name = name,
                normalizedName = Validation.listNameKey(name),
                isDefault = list.isDefault,
                createdAt = list.createdAt,
                updatedAt = list.updatedAt,
                version = 1
            ))
            existingNames.add(Validation.listNameKey(name))
            addedLists++
        }

        val localItemIds = dao.allItemIds().toHashSet()
        document.items.forEach { item ->
            if (!localItemIds.add(item.id)) {
                skipped++
                return@forEach
            }
            val listId = effectiveListId(item, legacyShopping)
            val list = dao.list(listId) ?: throw BackupException("记录引用的清单不存在")
            val normalized = try {
                Validation.normalize(ItemInput(listId, item.title, item.note, item.pinned))
            } catch (e: ValidationException) {
                throw BackupException("记录字段无效：${e.message}")
            }
            dao.insertItem(ItemEntity(
                id = item.id,
                listId = list.id,
                title = normalized.title,
                note = normalized.note,
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
