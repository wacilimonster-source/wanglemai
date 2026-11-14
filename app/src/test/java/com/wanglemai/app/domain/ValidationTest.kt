package com.wanglemai.app.domain

import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test fun trimsTitle() {
        val v = Validation.normalize(ItemInput(DEFAULT_TODO, " 牛奶 "))
        assertEquals("牛奶", v.title)
    }
    @Test fun emojiLimitUsesCodePoints() {
        assertEquals(200, Validation.codePoints("😀".repeat(200)))
        Validation.normalize(ItemInput(DEFAULT_TODO, "😀".repeat(200)))
        assertThrows(IllegalArgumentException::class.java) { Validation.normalize(ItemInput(DEFAULT_TODO, "😀".repeat(201))) }
    }
    @Test fun rejectsBlankAndMultilineTitle() {
        for (title in listOf("  ", "牛奶\n鸡蛋")) assertThrows(IllegalArgumentException::class.java) {
            Validation.normalize(ItemInput(DEFAULT_TODO, title))
        }
    }
    @Test fun batchIgnoresBlankLinesAndKeepsOriginalLineNumbers() {
        val input = BatchInput(DEFAULT_TODO, " 第一条 \r\n\r\n第三条\n")
        val items = Validation.normalizeBatch(input)
        assertEquals(listOf("第一条", "第三条"), items.map { it.title })
        assertEquals(listOf(1, 3), Validation.parseBatchLines(input.text).map { it.first })
    }
    @Test fun batchReportsTheOriginalFailingLine() {
        val text = "第一条\n" + "😀".repeat(201)
        val error = assertThrows(ValidationException::class.java) {
            Validation.normalizeBatch(BatchInput(DEFAULT_TODO, text))
        }
        assertEquals(2, error.line)
        assertTrue(error.message.startsWith("第 2 行："))
    }
    @Test fun batchAcceptsFiftyAndRejectsFiftyOneLines() {
        val fifty = (1..50).joinToString("\n") { "记录$it" }
        assertEquals(50, Validation.normalizeBatch(BatchInput(DEFAULT_TODO, fifty)).size)
        val fiftyOne = (1..51).joinToString("\n") { "记录$it" }
        assertThrows(ValidationException::class.java) {
            Validation.normalizeBatch(BatchInput(DEFAULT_TODO, fiftyOne))
        }
    }
    @Test fun blankBatchHasHelpfulMessage() {
        val error = assertThrows(ValidationException::class.java) {
            Validation.normalizeBatch(BatchInput(DEFAULT_TODO, " \r\n \n"))
        }
        assertEquals("先写点要记的内容", error.message)
    }
    @Test fun noteLimitUsesCodePoints() {
        Validation.requireNote("😀".repeat(2000))
        assertThrows(ValidationException::class.java) { Validation.requireNote("😀".repeat(2001)) }
    }
    @Test fun listNameIsTrimmedAndLimitedToTwentyCodePoints() {
        assertEquals("工作", Validation.normalizeListName(" 工作 "))
        assertEquals(20, Validation.codePoints(Validation.normalizeListName("😀".repeat(20))))
        assertThrows(ValidationException::class.java) {
            Validation.normalizeListName("😀".repeat(21))
        }
        assertEquals("abc", Validation.listNameKey("ABC"))
    }
    @Test fun listNameRejectsLineBreaksAndControlCharacters() {
        for (name in listOf("工作\n清单", "工作\r清单", "工作\t清单", "工作\u0000清单", "工作\u2028清单")) {
            assertThrows(name, ValidationException::class.java) { Validation.normalizeListName(name) }
        }
    }
}
