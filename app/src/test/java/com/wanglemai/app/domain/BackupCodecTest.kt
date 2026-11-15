package com.wanglemai.app.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val now = 1_793_923_200_000L
    private val itemId = "550e8400-e29b-41d4-a716-446655440000"
    private val listId = "11111111-2222-3333-4444-555555555555"

    private fun list(
        id: String = listId,
        name: String = "工作",
        isDefault: Boolean = false
    ) = BackupList(id, "TODO", name, isDefault, now, now)

    private fun item(
        id: String = itemId,
        listId: String = this.listId,
        title: String = "取快递",
        completed: Boolean = false
    ) = BackupItem(id, listId, title, "", completed, false, 1024,
        now, now, if (completed) now else null)

    private fun document(
        lists: List<BackupList> = listOf(
            BackupList(DEFAULT_TODO, "TODO", "随手待办", true, now, now),
            list()
        ),
        items: List<BackupItem> = listOf(item())
    ) = BackupDocument(BACKUP_SCHEMA_VERSION, BACKUP_APP_ID, now, lists, items)

    @Test fun roundTripPreservesFields() {
        val decoded = BackupCodec.decode(BackupCodec.encode(document()))
        assertEquals(2, decoded.lists.size)
        assertEquals(1, decoded.items.size)
        assertEquals("取快递", decoded.items.single().title)
        assertFalse(decoded.items.single().completed)
        // Exports keep the kind field so pre-v0.4.0 builds can still import the file.
        assertEquals("TODO", JSONObject(BackupCodec.encode(document()))
            .getJSONArray("lists").getJSONObject(0).getString("kind"))
    }

    @Test fun unknownFieldsAreIgnored() {
        val json = JSONObject(BackupCodec.encode(document())).put("futureField", true)
        assertEquals(1, BackupCodec.decode(json.toString()).items.size)
    }

    @Test fun unknownVersionAndAppAreRejected() {
        val encoded = BackupCodec.encode(document())
        assertThrows(BackupException::class.java) {
            BackupCodec.decode(JSONObject(encoded).put("schemaVersion", 2).toString())
        }
        assertThrows(BackupException::class.java) {
            BackupCodec.decode(JSONObject(encoded).put("app", "other.app").toString())
        }
    }

    @Test fun duplicateListAndItemIdsAreRejected() {
        val duplicateLists = document(lists = listOf(
            BackupList(DEFAULT_TODO, "TODO", "随手待办", true, now, now),
            list(), list(name = "另一份")
        ))
        assertThrows(BackupException::class.java) {
            BackupCodec.decode(BackupCodec.encode(duplicateLists))
        }
        val duplicateItems = document(items = listOf(item(), item(title = "另一条")))
        assertThrows(BackupException::class.java) {
            BackupCodec.decode(BackupCodec.encode(duplicateItems))
        }
    }

    @Test fun defaultFlagsMustMatchBuiltInIds() {
        val bad = document(lists = listOf(
            BackupList(DEFAULT_TODO, "TODO", "随手待办", false, now, now)
        ), items = emptyList())
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(bad)) }
        val customDefault = document(lists = listOf(
            BackupList(DEFAULT_TODO, "TODO", "随手待办", true, now, now),
            list(isDefault = true)
        ), items = emptyList())
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(customDefault)) }
    }

    /** Legacy kind values survive decode so the merge step can convert them. */
    @Test fun legacyShoppingKindIsAccepted() {
        val json = JSONObject(BackupCodec.encode(document())).apply {
            getJSONArray("lists").getJSONObject(0).put("kind", "TODO")
        }
        assertEquals("TODO", BackupCodec.decode(json.toString()).lists.first().kind)
    }

    @Test fun unknownKindIsRejected() {
        val json = JSONObject(BackupCodec.encode(document())).apply {
            getJSONArray("lists").getJSONObject(1).put("kind", "ARCHIVE")
        }
        assertThrows(BackupException::class.java) { BackupCodec.decode(json.toString()) }
    }

    /** Pre-v0.4.0 backups carry quantity/unit on items; decode tolerates and drops them. */
    @Test fun legacyQuantityFieldsAreToleratedAndDropped() {
        val json = JSONObject(BackupCodec.encode(document())).apply {
            val entry = getJSONArray("items").getJSONObject(0)
            entry.put("quantityDecimal", "1e2")
            entry.put("unit", "盒")
        }
        val decoded = BackupCodec.decode(json.toString())
        assertEquals(1, decoded.items.size)
        assertEquals("取快递", decoded.items.single().title)
    }

    @Test fun legacyDefaultShoppingListMarkerIsValid() {
        val json = JSONObject(BackupCodec.encode(document())).apply {
            val lists = getJSONArray("lists")
            lists.getJSONObject(0).put("kind", "TODO")
            lists.put(JSONObject()
                .put("id", DEFAULT_SHOPPING)
                .put("kind", "SHOPPING")
                .put("name", "日常采购")
                .put("isDefault", true)
                .put("createdAt", now)
                .put("updatedAt", now))
        }
        assertEquals(3, BackupCodec.decode(json.toString()).lists.size)
    }

    @Test fun completionTimestampMustBeConsistent() {
        val bad = document(items = listOf(item(completed = true).copy(completedAt = null)))
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(bad)) }
        val restored = document(items = listOf(item(completed = false).copy(completedAt = now)))
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(restored)) }
    }

    @Test fun malformedItemIdsAreRejected() {
        assertThrows(BackupException::class.java) {
            BackupCodec.decode(BackupCodec.encode(document(items = listOf(item(id = "not-a-uuid")))))
        }
        assertThrows(BackupException::class.java) {
            BackupCodec.decode(BackupCodec.encode(document(items = listOf(item(id = DEFAULT_TODO)))))
        }
    }

    @Test fun itemListIdMustBeValidShape() {
        val bad = document(items = listOf(item(listId = "not-a-list-id")))
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(bad)) }
    }

    @Test fun listNameCannotExceedTwentyCodePoints() {
        val bad = document(lists = listOf(
            BackupList(DEFAULT_TODO, "TODO", "随手待办", true, now, now),
            list(name = "😀".repeat(21))
        ), items = emptyList())
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(bad)) }
    }

    @Test fun listNameCannotContainLineBreaks() {
        val bad = document(lists = listOf(
            BackupList(DEFAULT_TODO, "TODO", "随手待办", true, now, now),
            list(name = "第一行\n第二行")
        ), items = emptyList())
        assertThrows(BackupException::class.java) { BackupCodec.decode(BackupCodec.encode(bad)) }
    }

    @Test fun deeplyNestedJsonIsRejectedBeforeRecursiveParsing() {
        val depth = 5_000
        val json = buildString {
            append("{\"schemaVersion\":1,\"app\":\"" + BACKUP_APP_ID + "\",\"exportedAt\":1,")
            append("\"lists\":[],\"items\":[],\"junk\":")
            repeat(depth) { append('[') }
            repeat(depth) { append(']') }
            append('}')
        }
        assertThrows(BackupException::class.java) { BackupCodec.decode(json) }
    }

    @Test fun bracketsInsideStringsDoNotCountAsNesting() {
        val json = JSONObject(BackupCodec.encode(document())).put("future", "[[[[{{{")
        assertEquals(1, BackupCodec.decode(json.toString()).items.size)
    }

    @Test fun oversizedFileIsRejectedBeforeParsing() {
        assertThrows(BackupException::class.java) {
            BackupCodec.decode("x".repeat(BACKUP_MAX_BYTES + 1))
        }
    }

    @Test fun tooManyRecordsAreRejected() {
        val root = JSONObject(BackupCodec.encode(document()))
        root.put("items", JSONArray())
        for (i in 0..BACKUP_MAX_ITEMS) {
            root.getJSONArray("items").put(JSONObject()
                .put("id", String.format("%08d-2222-3333-4444-555555555555", i))
                .put("listId", DEFAULT_TODO)
                .put("title", "记录$i")
                .put("note", "")
                .put("quantityDecimal", JSONObject.NULL)
                .put("unit", JSONObject.NULL)
                .put("completed", false)
                .put("pinned", false)
                .put("sortKey", 1024)
                .put("createdAt", now)
                .put("updatedAt", now)
                .put("completedAt", JSONObject.NULL))
        }
        assertThrows(BackupException::class.java) { BackupCodec.decode(root.toString()) }
    }

    @Test fun utf8BomPrefixIsStrippedBeforeDecoding() {
        val decoded = BackupCodec.decode("\uFEFF" + BackupCodec.encode(document()))
        assertEquals(2, decoded.lists.size)
        assertEquals("取快递", decoded.items.single().title)
    }
}
