package com.wanglemai.app.domain

import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    @Test fun trimsTitleAndNormalizesDecimalExactly() {
        val v = Validation.normalize(ItemInput(DEFAULT_SHOPPING, " 牛奶 ", quantity = "2.00", unit = " 盒 "), ListKind.SHOPPING)
        assertEquals("牛奶", v.title); assertEquals("2", v.quantity); assertEquals("盒", v.unit)
    }
    @Test fun emojiLimitUsesCodePoints() {
        assertEquals(200, Validation.codePoints("😀".repeat(200)))
        Validation.normalize(ItemInput(DEFAULT_TODO, "😀".repeat(200)), ListKind.TODO)
        assertThrows(IllegalArgumentException::class.java) { Validation.normalize(ItemInput(DEFAULT_TODO, "😀".repeat(201)), ListKind.TODO) }
    }
    @Test fun rejectsBlankAndMultilineTitle() {
        for (title in listOf("  ", "牛奶\n鸡蛋")) assertThrows(IllegalArgumentException::class.java) {
            Validation.normalize(ItemInput(DEFAULT_TODO, title), ListKind.TODO)
        }
    }
    @Test fun rejectsInvalidQuantities() {
        for (q in listOf("-1", "0", "0.001", "10000", "1e2", "NaN", "01", "1.234"))
            assertThrows(q, IllegalArgumentException::class.java) {
                Validation.normalize(ItemInput(DEFAULT_SHOPPING, "牛奶", quantity = q), ListKind.SHOPPING)
            }
    }
    @Test fun acceptsQuantityBounds() {
        assertEquals("0.01", Validation.normalize(ItemInput(DEFAULT_SHOPPING, "盐",
            quantity = "0.01"), ListKind.SHOPPING).quantity)
        assertEquals("9999.99", Validation.normalize(ItemInput(DEFAULT_SHOPPING, "米",
            quantity = "9999.99"), ListKind.SHOPPING).quantity)
    }
    @Test fun unitRequiresQuantity() {
        assertThrows(IllegalArgumentException::class.java) {
            Validation.normalize(ItemInput(DEFAULT_SHOPPING, "牛奶", unit = "盒"), ListKind.SHOPPING)
        }
    }
    @Test fun todoClearsShoppingMetadata() {
        val v = Validation.normalize(ItemInput(DEFAULT_TODO, "取快递", quantity = "2", unit = "盒"), ListKind.TODO)
        assertEquals("", v.quantity); assertEquals("", v.unit)
    }
    @Test fun batchIgnoresBlankLinesAndKeepsOriginalLineNumbers() {
        val input = BatchInput(DEFAULT_TODO, " 第一条 \r\n\r\n第三条\n")
        val items = Validation.normalizeBatch(input, ListKind.TODO)
        assertEquals(listOf("第一条", "第三条"), items.map { it.title })
        assertEquals(listOf(1, 3), Validation.parseBatchLines(input.text).map { it.first })
    }
    @Test fun batchReportsTheOriginalFailingLine() {
        val text = "第一条\n" + "😀".repeat(201)
        val error = assertThrows(ValidationException::class.java) {
            Validation.normalizeBatch(BatchInput(DEFAULT_TODO, text), ListKind.TODO)
        }
        assertEquals(2, error.line)
        assertTrue(error.message.startsWith("第 2 行："))
    }
    @Test fun batchAcceptsFiftyAndRejectsFiftyOneLines() {
        val fifty = (1..50).joinToString("\n") { "记录$it" }
        assertEquals(50, Validation.normalizeBatch(BatchInput(DEFAULT_TODO, fifty), ListKind.TODO).size)
        val fiftyOne = (1..51).joinToString("\n") { "记录$it" }
        assertThrows(ValidationException::class.java) {
            Validation.normalizeBatch(BatchInput(DEFAULT_TODO, fiftyOne), ListKind.TODO)
        }
    }
    @Test fun blankBatchHasHelpfulMessage() {
        val error = assertThrows(ValidationException::class.java) {
            Validation.normalizeBatch(BatchInput(DEFAULT_TODO, " \r\n \n"), ListKind.TODO)
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
}
