package com.wanglemai.app.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Strict schema-1 backup reader/writer. Unknown fields are ignored; unknown versions/types are rejected. */
object BackupCodec {
    private const val MAX_SAFE_INTEGER = 9007199254740991L
    private val UUID_PATTERN = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    fun encode(document: BackupDocument): String = try {
        JSONObject()
            .put("schemaVersion", document.schemaVersion)
            .put("app", document.app)
            .put("exportedAt", document.exportedAt)
            .put("lists", JSONArray().apply {
                document.lists.forEach { list ->
                    put(JSONObject()
                        .put("id", list.id)
                        .put("kind", list.kind.name)
                        .put("name", list.name)
                        .put("isDefault", list.isDefault)
                        .put("createdAt", list.createdAt)
                        .put("updatedAt", list.updatedAt))
                }
            })
            .put("items", JSONArray().apply {
                document.items.forEach { item ->
                    put(JSONObject()
                        .put("id", item.id)
                        .put("listId", item.listId)
                        .put("title", item.title)
                        .put("note", item.note)
                        .put("quantityDecimal", item.quantityDecimal ?: JSONObject.NULL)
                        .put("unit", item.unit ?: JSONObject.NULL)
                        .put("completed", item.completed)
                        .put("pinned", item.pinned)
                        .put("sortKey", item.sortKey)
                        .put("createdAt", item.createdAt)
                        .put("updatedAt", item.updatedAt)
                        .put("completedAt", item.completedAt ?: JSONObject.NULL))
                }
            })
            .toString(2)
    } catch (e: JSONException) {
        throw BackupException("备份生成失败")
    }

    fun decode(json: String): BackupDocument {
        if (json.toByteArray(Charsets.UTF_8).size > BACKUP_MAX_BYTES) {
            throw BackupException("备份文件超过 5MB")
        }
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            throw BackupException("备份格式或版本不支持")
        }
        if (!root.has("schemaVersion")) throw BackupException("备份格式或版本不支持")
        if (number(root, "schemaVersion", integral = true) != BACKUP_SCHEMA_VERSION.toLong()) {
            throw BackupException("备份版本不支持")
        }
        if (string(root, "app") != BACKUP_APP_ID) throw BackupException("备份来源不支持")
        val exportedAt = safeLong(root, "exportedAt")

        val listArray = array(root, "lists")
        val itemArray = array(root, "items")
        if (listArray.length() > BACKUP_MAX_LISTS || itemArray.length() > BACKUP_MAX_ITEMS) {
            throw BackupException("最多支持 100 个清单、10000 条记录")
        }

        val listIds = HashSet<String>()
        val lists = ArrayList<BackupList>(listArray.length())
        for (i in 0 until listArray.length()) {
            val obj = objectAt(listArray, i, "清单字段无效或 ID 重复")
            val id = string(obj, "id")
            if (!isListId(id) || !listIds.add(id)) throw BackupException("清单字段无效或 ID 重复")
            val kind = try {
                ListKind.valueOf(string(obj, "kind"))
            } catch (_: IllegalArgumentException) {
                throw BackupException("清单字段无效或 ID 重复")
            }
            val name = try {
                Validation.normalizeListName(string(obj, "name"))
            } catch (_: ValidationException) {
                throw BackupException("清单字段无效或 ID 重复")
            }
            val isDefault = bool(obj, "isDefault")
            val expectedDefault = when (id) {
                DEFAULT_TODO -> kind == ListKind.TODO
                DEFAULT_SHOPPING -> kind == ListKind.SHOPPING
                else -> false
            }
            if (isDefault != expectedDefault) throw BackupException("默认清单标记无效")
            lists += BackupList(
                id = id, kind = kind, name = name, isDefault = isDefault,
                createdAt = safeLong(obj, "createdAt"), updatedAt = safeLong(obj, "updatedAt")
            )
        }

        val documentKinds = lists.associate { it.id to it.kind }
        val itemIds = HashSet<String>()
        val items = ArrayList<BackupItem>(itemArray.length())
        for (i in 0 until itemArray.length()) {
            val obj = objectAt(itemArray, i, "记录字段无效或 ID 重复")
            val id = string(obj, "id")
            if (!UUID_PATTERN.matches(id) || id == DEFAULT_TODO || id == DEFAULT_SHOPPING || !itemIds.add(id)) {
                throw BackupException("记录字段无效或 ID 重复")
            }
            val listId = string(obj, "listId")
            if (!isListId(listId)) throw BackupException("记录字段无效或 ID 重复")
            val title = try {
                Validation.normalizeTitle(string(obj, "title"))
            } catch (e: ValidationException) {
                throw BackupException("记录字段无效：${e.message}")
            }
            val note = string(obj, "note")
            try {
                Validation.requireNote(note)
            } catch (e: ValidationException) {
                throw BackupException("记录字段无效：${e.message}")
            }
            val quantity = nullableString(obj, "quantityDecimal")
            val unit = nullableString(obj, "unit")
            try {
                Validation.normalizeQuantity(quantity.orEmpty(), unit.orEmpty())
            } catch (e: ValidationException) {
                throw BackupException(e.message)
            }
            val completed = bool(obj, "completed")
            val completedAt = nullableLong(obj, "completedAt")
            if (completed && completedAt == null) throw BackupException("完成时间无效")
            if (!completed && completedAt != null) throw BackupException("完成时间无效")
            val kind = documentKinds[listId]
            if (kind == ListKind.TODO && (quantity != null || unit != null)) {
                throw BackupException("待办不能含购物数量")
            }
            items += BackupItem(
                id = id, listId = listId, title = title, note = note,
                quantityDecimal = quantity, unit = unit, completed = completed,
                pinned = bool(obj, "pinned"), sortKey = safeLong(obj, "sortKey"),
                createdAt = safeLong(obj, "createdAt"), updatedAt = safeLong(obj, "updatedAt"),
                completedAt = completedAt
            )
        }
        return BackupDocument(BACKUP_SCHEMA_VERSION, BACKUP_APP_ID, exportedAt, lists, items)
    }

    fun isListId(id: String): Boolean =
        id == DEFAULT_TODO || id == DEFAULT_SHOPPING || UUID_PATTERN.matches(id)

    fun isUuid(id: String): Boolean = UUID_PATTERN.matches(id)

    private fun objectAt(array: JSONArray, index: Int, message: String): JSONObject =
        array.opt(index) as? JSONObject ?: throw BackupException(message)

    private fun array(obj: JSONObject, name: String): JSONArray {
        if (!obj.has(name)) throw BackupException("备份格式或版本不支持")
        return obj.opt(name) as? JSONArray ?: throw BackupException("备份格式或版本不支持")
    }

    private fun string(obj: JSONObject, name: String): String {
        if (!obj.has(name)) throw BackupException("备份字段缺失或类型无效")
        return obj.opt(name) as? String ?: throw BackupException("备份字段缺失或类型无效")
    }

    private fun nullableString(obj: JSONObject, name: String): String? {
        if (!obj.has(name)) throw BackupException("备份字段缺失或类型无效")
        if (obj.isNull(name)) return null
        return obj.opt(name) as? String ?: throw BackupException("备份字段缺失或类型无效")
    }

    private fun bool(obj: JSONObject, name: String): Boolean {
        if (!obj.has(name)) throw BackupException("备份字段缺失或类型无效")
        return obj.opt(name) as? Boolean ?: throw BackupException("备份字段缺失或类型无效")
    }

    private fun number(obj: JSONObject, name: String, integral: Boolean): Long {
        if (!obj.has(name) || obj.isNull(name)) throw BackupException("备份字段缺失或类型无效")
        val value = obj.opt(name) as? Number ?: throw BackupException("备份字段缺失或类型无效")
        val double = value.toDouble()
        if (!double.isFinite() || double < 0 || double > MAX_SAFE_INTEGER.toDouble()) {
            throw BackupException("时间或排序无效")
        }
        if (integral && double % 1.0 != 0.0) throw BackupException("备份字段缺失或类型无效")
        return value.toLong()
    }

    private fun safeLong(obj: JSONObject, name: String): Long {
        val value = number(obj, name, integral = true)
        if (value < 0) throw BackupException("时间或排序无效")
        return value
    }

    private fun nullableLong(obj: JSONObject, name: String): Long? {
        if (!obj.has(name)) throw BackupException("备份字段缺失或类型无效")
        if (obj.isNull(name)) return null
        return safeLong(obj, name)
    }
}
