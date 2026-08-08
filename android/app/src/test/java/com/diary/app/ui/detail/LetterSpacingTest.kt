package com.diary.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterSpacingTest {

    @Test
    fun mixedText_splitsByLatinAndCjk() {
        val runs = splitTypeRuns("今天iPhone15到了")

        assertEquals(listOf("今天" to false, "iPhone15" to true, "到了" to false), runs)
    }

    @Test
    fun pureChinese_isOneCjkRun() {
        val runs = splitTypeRuns("全是中文")

        assertEquals(listOf("全是中文" to false), runs)
    }

    @Test
    fun digitsCountAsLatin() {
        val runs = splitTypeRuns("10月2日 8:30 开始")

        assertEquals(
            listOf("10" to true, "月" to false, "2" to true, "日 " to false, "8" to true, ":" to false, "30" to true, " 开始" to false),
            runs,
        )
    }

    @Test
    fun emptyText_returnsEmpty() {
        assertTrue(splitTypeRuns("").isEmpty())
    }
}
