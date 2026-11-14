package com.wanglemai.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wanglemai.app.data.db.AppDatabase
import com.wanglemai.app.data.db.MIGRATION_1_2
import com.wanglemai.app.domain.DEFAULT_SHOPPING
import com.wanglemai.app.domain.DEFAULT_TODO
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test fun shoppingListsMergeIntoDefaultTodoListAndSchemaIsRebuilt() {
        val dbName = "migration-test-${System.currentTimeMillis()}"
        val shoppingId = "shopping-1111-2222-3333-444444444444"
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO lists (id, kind, name, normalizedName, isDefault, createdAt, updatedAt, version) " +
                "VALUES ('$DEFAULT_TODO', 'TODO', '随手待办', '随手待办', 1, 1, 1, 1)")
            db.execSQL("INSERT INTO lists (id, kind, name, normalizedName, isDefault, createdAt, updatedAt, version) " +
                "VALUES ('$DEFAULT_SHOPPING', 'SHOPPING', '日常采购', '日常采购', 1, 1, 1, 1)")
            db.execSQL("INSERT INTO lists (id, kind, name, normalizedName, isDefault, createdAt, updatedAt, version) " +
                "VALUES ('$shoppingId', 'SHOPPING', '周末采购', '周末采购', 0, 1, 1, 1)")
            db.execSQL("INSERT INTO items (id, listId, title, note, quantityDecimal, unit, completed, pinned, " +
                "sortKey, createdAt, updatedAt, completedAt, deletedAt, version) " +
                "VALUES ('todo-item', '$DEFAULT_TODO', '已有待办', '', NULL, NULL, 0, 0, 1024, 1, 1, NULL, NULL, 1)")
            db.execSQL("INSERT INTO items (id, listId, title, note, quantityDecimal, unit, completed, pinned, " +
                "sortKey, createdAt, updatedAt, completedAt, deletedAt, version) " +
                "VALUES ('shopping-item-1', '$DEFAULT_SHOPPING', '牛奶', '', '2', '盒', 0, 0, 1024, 1, 1, NULL, NULL, 1)")
            db.execSQL("INSERT INTO items (id, listId, title, note, quantityDecimal, unit, completed, pinned, " +
                "sortKey, createdAt, updatedAt, completedAt, deletedAt, version) " +
                "VALUES ('shopping-item-2', '$shoppingId', '面包', '', NULL, NULL, 1, 0, 1024, 2, 1, 5, NULL, 1)")
            db.execSQL("INSERT INTO drafts (draftKey, payloadJson, baseItemVersion, updatedAt) " +
                "VALUES ('create:$DEFAULT_SHOPPING', " +
                "'{\"listId\":\"$DEFAULT_SHOPPING\",\"title\":\"采购草稿\",\"note\":\"\",\"quantityDecimal\":\"1\",\"unit\":\"件\",\"pinned\":false,\"batchMode\":false}', " +
                "NULL, 1)")
            db.execSQL("INSERT INTO drafts (draftKey, payloadJson, baseItemVersion, updatedAt) " +
                "VALUES ('edit:shopping-item-1', " +
                "'{\"listId\":\"$DEFAULT_SHOPPING\",\"title\":\"编辑草稿\",\"note\":\"\",\"pinned\":true,\"batchMode\":false}', " +
                "1, 1)")
            db.execSQL("INSERT INTO widget_configs (appWidgetId, providerClass, listId, theme, density, updatedAt) " +
                "VALUES (42, 'test-provider', '$DEFAULT_SHOPPING', 'SYSTEM', 'COMFORTABLE', 1)")
            db.execSQL("INSERT INTO widget_undo (appWidgetId, commandId, itemId, previousCompleted, previousCompletedAt, expectedItemVersion) " +
                "VALUES (42, 'cmd-1', 'shopping-item-1', 0, NULL, 2)")
            db.execSQL("INSERT INTO preferences (`key`, `value`) VALUES ('last_kind', 'SHOPPING')")
            db.execSQL("INSERT INTO preferences (`key`, `value`) VALUES ('last_list_SHOPPING', '$DEFAULT_SHOPPING')")
        }

        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).use { db ->
            // Shopping lists are gone; the default todo list remains.
            db.query("SELECT id, name, isDefault FROM lists").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(DEFAULT_TODO, cursor.getString(0))
                assertEquals("随手待办", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertFalse(cursor.moveToNext())
            }
            // Records are appended to the default todo list after its existing sortKey.
            db.query("SELECT id, listId, sortKey FROM items ORDER BY sortKey").use { cursor ->
                val rows = mutableListOf<Triple<String, String, Long>>()
                while (cursor.moveToNext()) {
                    rows.add(Triple(cursor.getString(0), cursor.getString(1), cursor.getLong(2)))
                }
                assertEquals(listOf("todo-item", "shopping-item-1", "shopping-item-2"),
                    rows.map { it.first })
                assertTrue(rows.all { it.second == DEFAULT_TODO })
                assertEquals(1024L, rows[0].third)
                assertEquals(2048L, rows[1].third)
                assertEquals(3072L, rows[2].third)
            }
            // Widget bound to a doomed list is rebound; undo entries are cleared.
            db.query("SELECT listId FROM widget_configs WHERE appWidgetId=42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(DEFAULT_TODO, cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM widget_undo").use { cursor ->
                cursor.moveToFirst(); assertEquals(0, cursor.getInt(0))
            }
            // Create-draft moves to the default list with its content intact; edit-draft is re-pointed.
            db.query("SELECT payloadJson FROM drafts WHERE draftKey='create:$DEFAULT_TODO'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                val payload = cursor.getString(0)
                assertTrue(payload.contains("\"listId\":\"$DEFAULT_TODO\""))
                assertTrue(payload.contains("采购草稿"))
                assertFalse(payload.contains("quantityDecimal"))
            }
            db.query("SELECT payloadJson FROM drafts WHERE draftKey='edit:shopping-item-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0).contains("\"listId\":\"$DEFAULT_TODO\""))
            }
            db.query("SELECT COUNT(*) FROM drafts").use { cursor ->
                cursor.moveToFirst(); assertEquals(2, cursor.getInt(0))
            }
            // Legacy type-selection preferences are gone.
            db.query("SELECT COUNT(*) FROM preferences").use { cursor ->
                cursor.moveToFirst(); assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
