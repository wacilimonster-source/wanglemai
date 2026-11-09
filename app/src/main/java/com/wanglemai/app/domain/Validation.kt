package com.wanglemai.app.domain

import java.math.BigDecimal
import java.util.Locale

class ValidationException(
    override val message: String,
    val field: String? = null,
    val line: Int? = null
) : IllegalArgumentException(message)

object Validation {
    fun codePoints(value: String) = value.codePointCount(0, value.length)

    fun normalize(input: ItemInput, kind: ListKind): ItemInput {
        val title = normalizeTitle(input.title)
        requireNote(input.note)
        if (kind == ListKind.TODO) return input.copy(title = title, quantity = "", unit = "")
        val (quantity, unit) = normalizeQuantity(input.quantity, input.unit)
        return input.copy(title = title, quantity = quantity, unit = unit)
    }

    fun normalizeTitle(raw: String, line: Int? = null): String {
        val title = raw.trim()
        fun fail(message: String): Nothing {
            throw ValidationException(
                if (line == null) message else "第 $line 行：$message",
                field = "title",
                line = line
            )
        }
        if (title.isEmpty()) fail("先写点要记的内容")
        if (codePoints(title) > 200) fail("标题最多 200 字符")
        if (title.contains('\n') || title.contains('\r')) fail("单条标题不能换行")
        return title
    }

    fun requireNote(note: String, line: Int? = null) {
        if (codePoints(note) > 2000) throw ValidationException(
            if (line == null) "备注最多 2000 字符" else "第 $line 行：备注最多 2000 字符",
            field = "note",
            line = line
        )
    }

    fun normalizeQuantity(rawQuantity: String, rawUnit: String): Pair<String, String> {
        val quantity = rawQuantity.trim()
        val unit = rawUnit.trim()
        if (codePoints(unit) > 12) throw ValidationException("单位最多 12 字符", field = "unit")
        if (quantity.isEmpty() && unit.isNotEmpty()) {
            throw ValidationException("填写单位时，请同时填写数量", field = "unit")
        }
        if (quantity.isEmpty()) return "" to ""
        if (!Regex("(?:0|[1-9][0-9]{0,3})(?:\\.[0-9]{1,2})?").matches(quantity)) {
            throw ValidationException("数量需为 0.01～9999.99，最多两位小数", field = "quantity")
        }
        val decimal = BigDecimal(quantity)
        if (decimal < BigDecimal("0.01") || decimal > BigDecimal("9999.99")) {
            throw ValidationException("数量需为 0.01～9999.99", field = "quantity")
        }
        return decimal.stripTrailingZeros().toPlainString() to unit
    }

    /** Splits on CRLF/LF, trims, ignores blank lines, and keeps the original 1-based line number. */
    fun parseBatchLines(text: String): List<Pair<Int, String>> {
        val lines = Regex("\\r\\n|\\n|\\r").split(text)
            .mapIndexedNotNull { index, raw ->
                raw.trim().takeIf { it.isNotEmpty() }?.let { index + 1 to it }
            }
        if (lines.isEmpty()) throw ValidationException("先写点要记的内容", field = "title")
        if (lines.size > 50) throw ValidationException("一次最多生成 50 条", field = "title")
        return lines
    }

    fun normalizeBatch(input: BatchInput, kind: ListKind): List<ItemInput> {
        requireNote(input.note)
        return parseBatchLines(input.text).map { (line, rawTitle) ->
            val title = normalizeTitle(rawTitle, line)
            if (kind == ListKind.TODO) {
                ItemInput(input.listId, title, input.note, pinned = input.pinned)
            } else {
                ItemInput(input.listId, title, input.note, "", "", input.pinned)
            }
        }
    }

    fun normalizeListName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty() || codePoints(name) > 20) {
            throw ValidationException("名称需要 1～20 字符", field = "name")
        }
        if (name.any { it == '\n' || it == '\r' || it == '\u2028' || it == '\u2029' || it.isISOControl() }) {
            throw ValidationException("名称不能包含换行或控制字符", field = "name")
        }
        return name
    }

    fun listNameKey(name: String): String = name.lowercase(Locale.ROOT)
}
