package com.diary.app.ui.diary

import android.content.Context
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlockInsertTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun editor(text: String, caret: Int): EditText = EditText(ctx).apply {
        setText(text, android.widget.TextView.BufferType.SPANNABLE)
        setSelection(caret)
    }

    @Test
    fun insertAtLineStart_wrapsMarkerOwnLine_caretLandsOnNextLine() {
        val view = editor("第一段\n第二段", caret = 4) // 光标在 "第二段" 行首

        view.insertBlockMarker("![sub](工作)")

        assertEquals("第一段\n![sub](工作)\n第二段", view.text.toString())
        assertEquals("光标应落在新块之后的行首", 15, view.selectionStart)
    }

    @Test
    fun insertMidParagraph_splitsLineWithMarker() {
        val view = editor("第一段\n第二段", caret = 2) // 光标在 "第一" 中间

        view.insertBlockMarker("![time](10:30:00)")

        assertEquals("第一\n![time](10:30:00)\n段\n第二段", view.text.toString())
        assertEquals("光标应落在 marker 之后", 21, view.selectionStart)
    }

    @Test
    fun insertAtEnd_appendsOnOwnLine() {
        val view = editor("正文", caret = 2)

        view.insertBlockMarker("![sub](标题)")

        assertEquals("正文\n![sub](标题)", view.text.toString())
        assertEquals("光标应停在末尾", 13, view.selectionStart)
    }

    @Test
    fun insertIntoEmptyBody_markerOnly() {
        val view = editor("", caret = 0)

        view.insertBlockMarker("![img](abc)")

        assertEquals("![img](abc)", view.text.toString())
        assertEquals(11, view.selectionStart)
    }

    @Test
    fun insertAtDocumentStart_prependsNewlineAfter() {
        val view = editor("第二段", caret = 0)

        view.insertBlockMarker("![sub](开头)")

        assertEquals("![sub](开头)\n第二段", view.text.toString())
    }

    @Test
    fun subheadingMarker_trimsAndRejectsBlank() {
        assertEquals("![sub](工作)", subheadingMarkerText("  工作  "))
        assertNull(subheadingMarkerText("  "))
        assertNull(subheadingMarkerText(""))
    }

    @Test
    fun timestampMarker_usesGivenTime() {
        assertEquals(
            "![time](14:30:00)",
            timestampMarkerText(LocalDateTime.of(2026, 8, 8, 14, 30, 0)),
        )
    }
}
