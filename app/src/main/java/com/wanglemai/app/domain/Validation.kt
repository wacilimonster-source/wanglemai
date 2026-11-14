package com.wanglemai.app.domain

import java.util.Locale

class ValidationException(
    override val message: String,
    val field: String? = null,
    val line: Int? = null
) : IllegalArgumentException(message)

object Validation {
    fun codePoints(value: String) = value.codePointCount(0, value.length)

    fun normalize(input: ItemInput): ItemInput {
        val title = normalizeTitle(input.title)
        requireNote(input.note)
        return input.copy(title = title)
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

    fun normalizeBatch(input: BatchInput): List<ItemInput> {
        requireNote(input.note)
        return parseBatchLines(input.text).map { (line, rawTitle) ->
            val title = normalizeTitle(rawTitle, line)
            ItemInput(input.listId, title, input.note, pinned = input.pinned)
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
