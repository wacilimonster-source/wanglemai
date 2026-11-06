package com.wanglemai.app.data.db

import com.wanglemai.app.domain.Draft
import org.json.JSONException
import org.json.JSONObject

object DraftCodec {
    fun encode(draft: Draft): String = JSONObject()
        .put("listId", draft.listId)
        .put("title", draft.title)
        .put("note", draft.note)
        .put("quantityDecimal", draft.quantity)
        .put("unit", draft.unit)
        .put("pinned", draft.pinned)
        .put("batchMode", draft.batch)
        .toString()

    fun decode(entity: DraftEntity): Draft? {
        return try {
            val json = JSONObject(entity.payloadJson)
            val listId = json.optString("listId").takeIf { it.isNotEmpty() }
            if (listId == null) null else Draft(
                draftKey = entity.draftKey,
                listId = listId,
                title = json.optString("title"),
                note = json.optString("note"),
                quantity = json.optString("quantityDecimal"),
                unit = json.optString("unit"),
                pinned = json.optBoolean("pinned", false),
                batch = json.optBoolean("batchMode", false),
                baseItemVersion = entity.baseItemVersion
            )
        } catch (_: JSONException) {
            null
        }
    }
}
