package com.wanglemai.app.domain

const val BACKUP_SCHEMA_VERSION = 1
const val BACKUP_APP_ID = "com.wanglemai.app"
const val BACKUP_MAX_BYTES = 5 * 1024 * 1024
const val BACKUP_MAX_LISTS = 100
const val BACKUP_MAX_ITEMS = 10000

/** Legacy v0.3.x default shopping list id; referenced only when importing old backups. */
const val DEFAULT_SHOPPING = "list-shopping-default"

/** Legacy backups before v0.4.0 carried `kind: "TODO" | "SHOPPING"`; kept only for import conversion. */
data class BackupList(
    val id: String,
    val kind: String?,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class BackupItem(
    val id: String,
    val listId: String,
    val title: String,
    val note: String,
    val completed: Boolean,
    val pinned: Boolean,
    val sortKey: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
)

data class BackupDocument(
    val schemaVersion: Int,
    val app: String,
    val exportedAt: Long,
    val lists: List<BackupList>,
    val items: List<BackupItem>
)

data class BackupPreview(
    val totalLists: Int,
    val totalItems: Int,
    val newLists: Int,
    val newItems: Int,
    val skipped: Int
)

data class BackupMergeReport(
    val addedLists: Int,
    val addedItems: Int,
    val skipped: Int
)

class BackupException(override val message: String) : Exception(message)
