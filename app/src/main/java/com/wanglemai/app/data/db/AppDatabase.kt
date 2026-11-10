package com.wanglemai.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class ListCountRow(
    @ColumnInfo(name = "listId") val listId: String,
    @ColumnInfo(name = "count") val count: Int
)

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLists(lists: List<ListEntity>)
    @Query("SELECT * FROM lists ORDER BY kind DESC, createdAt, id") fun observeLists(): Flow<List<ListEntity>>
    @Query("SELECT * FROM lists ORDER BY kind DESC, createdAt, id") suspend fun lists(): List<ListEntity>
    @Query("SELECT * FROM lists WHERE id=:id") suspend fun list(id: String): ListEntity?
    @Query("SELECT * FROM lists WHERE kind=:kind AND normalizedName=:normalizedName") suspend fun listByNormalizedName(kind: String, normalizedName: String): ListEntity?
    @Insert suspend fun insertList(list: ListEntity)
    @Query("UPDATE lists SET name=:name, normalizedName=:normalizedName, updatedAt=:time, version=version+1 WHERE id=:id AND version=:version")
    suspend fun renameList(id: String, version: Long, name: String, normalizedName: String, time: Long): Int
    @Query("DELETE FROM lists WHERE id=:id") suspend fun deleteList(id: String)

    @Query("SELECT * FROM items WHERE listId=:listId AND deletedAt IS NULL AND completed=:completed AND (:search='' OR instr(lower(title || char(10) || note),lower(:search))>0) ORDER BY CASE WHEN :completed=0 AND pinned=1 THEN 0 ELSE 1 END, CASE WHEN :completed=1 THEN completedAt ELSE 0 END DESC, CASE WHEN :completed=0 THEN sortKey ELSE 0 END, CASE WHEN :completed=0 THEN createdAt ELSE 0 END, id ASC")
    fun observeItems(listId: String, completed: Boolean, search: String): Flow<List<ItemEntity>>
    @Query("SELECT * FROM items WHERE id=:id AND deletedAt IS NULL") suspend fun item(id: String): ItemEntity?
    @Query("SELECT * FROM items WHERE id=:id") suspend fun anyItem(id: String): ItemEntity?
    @Query("SELECT id FROM items") suspend fun allItemIds(): List<String>
    @Query("SELECT * FROM items WHERE listId=:listId AND deletedAt IS NULL ORDER BY createdAt, id") suspend fun backupItems(listId: String): List<ItemEntity>
    @Query("SELECT * FROM items WHERE deletedAt IS NULL ORDER BY createdAt, id") suspend fun backupItems(): List<ItemEntity>
    @Query("SELECT COALESCE(MAX(sortKey),0) FROM items WHERE listId=:listId") suspend fun maxSortKey(listId: String): Long
    @Insert suspend fun insertItem(item: ItemEntity)
    @Insert suspend fun insertItems(items: List<ItemEntity>)
    @Query("UPDATE items SET listId=:listId,title=:title,note=:note,quantityDecimal=:quantity,unit=:unit,pinned=:pinned,updatedAt=:time,version=version+1 WHERE id=:id AND version=:version AND deletedAt IS NULL")
    suspend fun edit(id: String, version: Long, listId: String, title: String, note: String,
        quantity: String?, unit: String?, pinned: Boolean, time: Long): Int
    @Query("UPDATE items SET completed=:completed,completedAt=:completedAt,updatedAt=:time,version=version+1 WHERE id=:id AND version=:version AND deletedAt IS NULL")
    suspend fun complete(id: String, version: Long, completed: Boolean, completedAt: Long?, time: Long): Int
    @Query("UPDATE items SET deletedAt=:time,updatedAt=:time,version=version+1 WHERE id=:id AND version=:version AND deletedAt IS NULL")
    suspend fun delete(id: String, version: Long, time: Long): Int
    @Query("UPDATE items SET listId=:targetListId, sortKey=:sortKey, updatedAt=:time, version=version+1 WHERE id=:id")
    suspend fun moveItem(id: String, targetListId: String, sortKey: Long, time: Long): Int
    @Query("SELECT * FROM items WHERE listId=:listId ORDER BY createdAt, id") suspend fun allItemsInList(listId: String): List<ItemEntity>
    @Query("SELECT * FROM items WHERE listId=:listId AND deletedAt IS NULL AND completed=0 ORDER BY pinned DESC,sortKey,createdAt,id LIMIT :limit")
    suspend fun widgetItems(listId: String, limit: Int): List<ItemEntity>
    @Query("SELECT COUNT(*) FROM items WHERE listId=:listId AND completed=0 AND deletedAt IS NULL") suspend fun count(listId: String): Int
    @Query("SELECT COUNT(*) FROM items WHERE listId=:listId AND completed=1 AND deletedAt IS NULL") suspend fun countCompleted(listId: String): Int
    @Query("SELECT COUNT(*) FROM items WHERE listId=:listId AND completed=0 AND deletedAt IS NULL") fun observeActiveCount(listId: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM items WHERE listId=:listId AND completed=1 AND deletedAt IS NULL") fun observeCompletedCount(listId: String): Flow<Int>
    @Query("SELECT listId, COUNT(*) AS count FROM items WHERE deletedAt IS NULL GROUP BY listId") fun observeListCounts(): Flow<List<ListCountRow>>
    @Query("SELECT COUNT(*) FROM items WHERE listId=:listId AND deletedAt IS NULL") suspend fun itemCount(listId: String): Int

    @Query("SELECT * FROM applied_commands WHERE commandId=:id") suspend fun command(id: String): AppliedCommand?
    @Insert suspend fun insertCommand(command: AppliedCommand)

    @Query("SELECT * FROM widget_configs WHERE appWidgetId=:id") suspend fun config(id: Int): WidgetConfig?
    @Query("SELECT * FROM widget_configs ORDER BY appWidgetId") suspend fun configs(): List<WidgetConfig>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveConfig(config: WidgetConfig)
    @Query("DELETE FROM widget_configs WHERE appWidgetId=:id") suspend fun deleteConfig(id: Int)
    @Query("UPDATE widget_configs SET listId=:targetListId, updatedAt=:time WHERE listId=:sourceListId")
    suspend fun rebindConfigs(sourceListId: String, targetListId: String, time: Long)
    @Query("DELETE FROM widget_configs WHERE appWidgetId NOT IN (:validIds)")
    suspend fun deleteConfigsExcept(validIds: List<Int>)
    @Query("DELETE FROM widget_configs") suspend fun deleteAllConfigs()

    @Query("SELECT * FROM widget_undo WHERE appWidgetId=:id") suspend fun undo(id: Int): WidgetUndo?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveUndo(undo: WidgetUndo)
    @Query("DELETE FROM widget_undo WHERE itemId=:id") suspend fun clearItemUndo(id: String)
    @Query("DELETE FROM widget_undo WHERE itemId IN (:itemIds)") suspend fun clearItemUndos(itemIds: List<String>)
    @Query("DELETE FROM widget_undo WHERE appWidgetId=:id") suspend fun clearWidgetUndo(id: Int)
    @Query("DELETE FROM widget_undo WHERE appWidgetId IN (:ids)") suspend fun clearUndoForWidgets(ids: List<Int>)
    @Query("DELETE FROM widget_undo WHERE appWidgetId IN (SELECT appWidgetId FROM widget_configs WHERE listId=:listId)")
    suspend fun clearUndoForListWidgets(listId: String)

    @Query("SELECT * FROM drafts WHERE draftKey=:key") suspend fun draft(key: String): DraftEntity?
    @Query("SELECT * FROM drafts") suspend fun drafts(): List<DraftEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDraft(draft: DraftEntity)
    @Query("DELETE FROM drafts WHERE draftKey=:key") suspend fun deleteDraft(key: String)

    @Query("SELECT * FROM preferences WHERE key=:key") suspend fun preference(key: String): PreferenceEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePreference(preference: PreferenceEntity)
}

@Database(entities = [ListEntity::class, ItemEntity::class, WidgetConfig::class,
    WidgetUndo::class, AppliedCommand::class, DraftEntity::class, PreferenceEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { abstract fun dao(): AppDao }
