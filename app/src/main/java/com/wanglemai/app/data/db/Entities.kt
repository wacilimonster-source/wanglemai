package com.wanglemai.app.data.db

import androidx.room.*
import com.wanglemai.app.domain.*

@Entity(tableName = "lists", indices = [Index(value = ["kind", "normalizedName"], unique = true)])
data class ListEntity(@PrimaryKey val id: String, val kind: String, val name: String,
    val normalizedName: String, val isDefault: Boolean, val createdAt: Long,
    val updatedAt: Long, val version: Long = 1) {
    fun model() = TaskList(id, name, ListKind.valueOf(kind), isDefault, createdAt, updatedAt, version)
}
@Entity(tableName = "items", foreignKeys = [ForeignKey(entity = ListEntity::class,
    parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["listId", "deletedAt", "completed", "pinned", "sortKey"]), Index(value = ["listId", "completedAt"])])
data class ItemEntity(@PrimaryKey val id: String, val listId: String, val title: String,
    val note: String, val quantityDecimal: String?, val unit: String?, val completed: Boolean,
    val pinned: Boolean, val sortKey: Long, val createdAt: Long, val updatedAt: Long,
    val completedAt: Long? = null, val deletedAt: Long? = null, val version: Long = 1) {
    fun model() = Item(id, listId, title, note, quantityDecimal, unit, completed, pinned,
        sortKey, createdAt, updatedAt, completedAt, version)
}
@Entity(tableName = "widget_configs", foreignKeys = [ForeignKey(entity = ListEntity::class,
    parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("listId")])
data class WidgetConfig(@PrimaryKey val appWidgetId: Int, val providerClass: String,
    val listId: String, val theme: String = "SYSTEM", val density: String = "COMFORTABLE",
    val updatedAt: Long = System.currentTimeMillis())
@Entity(tableName = "widget_undo", foreignKeys = [
    ForeignKey(entity = WidgetConfig::class, parentColumns = ["appWidgetId"], childColumns = ["appWidgetId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId"), Index(value = ["commandId"], unique = true)])
data class WidgetUndo(@PrimaryKey val appWidgetId: Int, val commandId: String, val itemId: String,
    val previousCompleted: Boolean, val previousCompletedAt: Long?, val expectedItemVersion: Long) {
    fun token() = UndoToken(itemId, previousCompleted, previousCompletedAt, expectedItemVersion)
}
@Entity(tableName = "applied_commands")
data class AppliedCommand(@PrimaryKey val commandId: String, val resultIdsJson: String, val createdAt: Long)
// Reserved for the next milestone; currently no draft/preferences UI is claimed.
@Entity(tableName = "drafts")
data class DraftEntity(@PrimaryKey val draftKey: String, val payloadJson: String, val baseItemVersion: Long?, val updatedAt: Long)
@Entity(tableName = "preferences")
data class PreferenceEntity(@PrimaryKey val key: String, val value: String)
