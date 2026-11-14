package com.wanglemai.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wanglemai.app.domain.DEFAULT_TODO
import com.wanglemai.app.domain.Draft

/**
 * v1 -> v2: the SHOPPING list kind and the item quantity/unit columns are removed. All legacy
 * shopping lists are deleted and their records are appended to the default todo list (the same
 * pattern as [AppDao] consumers use for list deletion); widgets bound to a doomed list are
 * rebound and their (transient) undo entries are cleared.
 *
 * Room keeps foreign key constraints off while migrations run (they are enabled afterwards in
 * onOpen), so the recreate-table steps below do not trigger implicit deletes on parent tables.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val shoppingIds = ArrayList<String>()
        db.query("SELECT id FROM lists WHERE kind='SHOPPING'").use { cursor ->
            while (cursor.moveToNext()) shoppingIds.add(cursor.getString(0))
        }
        if (shoppingIds.isNotEmpty()) {
            mergeShoppingItems(db, shoppingIds)
            rebindWidgets(db, shoppingIds)
            migrateDrafts(db, shoppingIds)
            db.execSQL("DELETE FROM lists WHERE kind='SHOPPING'")
        }

        // lists: drop the kind column; names are now unique across the single kind.
        db.execSQL("CREATE TABLE IF NOT EXISTS `lists_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`normalizedName` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO `lists_new` (`id`,`name`,`normalizedName`,`isDefault`,`createdAt`,`updatedAt`,`version`) " +
            "SELECT `id`,`name`,`normalizedName`,`isDefault`,`createdAt`,`updatedAt`,`version` FROM `lists`")
        db.execSQL("DROP TABLE `lists`")
        db.execSQL("ALTER TABLE `lists_new` RENAME TO `lists`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lists_normalizedName` ON `lists` (`normalizedName`)")

        // items: drop the quantity/unit columns. Undo entries are transient, so clear them first.
        db.execSQL("DELETE FROM `widget_undo`")
        db.execSQL("CREATE TABLE IF NOT EXISTS `items_new` (`id` TEXT NOT NULL, `listId` TEXT NOT NULL, " +
            "`title` TEXT NOT NULL, `note` TEXT NOT NULL, `completed` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, " +
            "`sortKey` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "`completedAt` INTEGER, `deletedAt` INTEGER, `version` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`listId`) REFERENCES `lists`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        db.execSQL("INSERT INTO `items_new` (`id`,`listId`,`title`,`note`,`completed`,`pinned`,`sortKey`," +
            "`createdAt`,`updatedAt`,`completedAt`,`deletedAt`,`version`) " +
            "SELECT `id`,`listId`,`title`,`note`,`completed`,`pinned`,`sortKey`,`createdAt`,`updatedAt`," +
            "`completedAt`,`deletedAt`,`version` FROM `items`")
        db.execSQL("DROP TABLE `items`")
        db.execSQL("ALTER TABLE `items_new` RENAME TO `items`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_listId_deletedAt_completed_pinned_sortKey` " +
            "ON `items` (`listId`, `deletedAt`, `completed`, `pinned`, `sortKey`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_listId_completedAt` ON `items` (`listId`, `completedAt`)")

        db.execSQL("DELETE FROM preferences WHERE key IN ('last_kind','last_list_SHOPPING')")
    }

    /** Appends every record from the doomed lists to the default todo list, soft-deleted ones included. */
    private fun mergeShoppingItems(db: SupportSQLiteDatabase, shoppingIds: List<String>) {
        val args = shoppingIds.toTypedArray()
        val placeholders = shoppingIds.joinToString(",") { "?" }
        val sortKey = db.query(
            "SELECT COALESCE(MAX(sortKey),0) FROM items WHERE listId=?", arrayOf(DEFAULT_TODO)
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
        var nextSortKey = sortKey
        db.query(
            "SELECT id FROM items WHERE listId IN ($placeholders) ORDER BY createdAt, id", args
        ).use { cursor ->
            while (cursor.moveToNext()) {
                nextSortKey += 1024
                db.execSQL(
                    "UPDATE items SET listId=?, sortKey=? WHERE id=?",
                    arrayOf(DEFAULT_TODO, nextSortKey, cursor.getString(0))
                )
            }
        }
    }

    private fun rebindWidgets(db: SupportSQLiteDatabase, shoppingIds: List<String>) {
        val args = arrayOf<Any?>(DEFAULT_TODO, *shoppingIds.toTypedArray())
        val placeholders = shoppingIds.joinToString(",") { "?" }
        db.execSQL("UPDATE widget_configs SET listId=?, updatedAt=0 WHERE listId IN ($placeholders)", args)
        db.execSQL("DELETE FROM widget_undo")
    }

    /** Mirrors the draft handling of deleting a list: move create-drafts, re-point edit-drafts. */
    private fun migrateDrafts(db: SupportSQLiteDatabase, shoppingIds: List<String>) {
        val doomedCreateKeys = shoppingIds.map { "create:$it" }.toHashSet()
        val existingKeys = HashSet<String>()
        db.query("SELECT draftKey FROM drafts").use { cursor ->
            while (cursor.moveToNext()) existingKeys.add(cursor.getString(0))
        }
        val targetKey = "create:$DEFAULT_TODO"

        db.query("SELECT draftKey, payloadJson, baseItemVersion, updatedAt FROM drafts").use { cursor ->
            val rowIndex = cursor.getColumnIndex("draftKey")
            val payloadIndex = cursor.getColumnIndex("payloadJson")
            val baseVersionIndex = cursor.getColumnIndex("baseItemVersion")
            val updatedIndex = cursor.getColumnIndex("updatedAt")
            while (cursor.moveToNext()) {
                val key = cursor.getString(rowIndex)
                val payload = cursor.getString(payloadIndex)
                val baseVersion = if (cursor.isNull(baseVersionIndex)) null else cursor.getLong(baseVersionIndex)
                val updatedAt = cursor.getLong(updatedIndex)
                val draft = DraftCodec.decode(DraftEntity(key, payload, baseVersion, updatedAt))
                when {
                    key in doomedCreateKeys -> {
                        db.execSQL("DELETE FROM drafts WHERE draftKey=?", arrayOf(key))
                        if (targetKey !in existingKeys && draft != null) {
                            existingKeys.add(targetKey)
                            saveDraft(db, draft.copy(draftKey = targetKey, listId = DEFAULT_TODO), updatedAt)
                        }
                    }
                    draft != null && draft.listId in shoppingIds -> {
                        saveDraft(db, draft.copy(listId = DEFAULT_TODO), updatedAt)
                    }
                }
            }
        }
    }

    private fun saveDraft(db: SupportSQLiteDatabase, draft: Draft, updatedAt: Long) {
        db.execSQL(
            "INSERT OR REPLACE INTO drafts (`draftKey`,`payloadJson`,`baseItemVersion`,`updatedAt`) VALUES (?,?,?,?)",
            arrayOf(draft.draftKey, DraftCodec.encode(draft), draft.baseItemVersion, updatedAt)
        )
    }
}
