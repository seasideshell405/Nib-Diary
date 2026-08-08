package com.diary.app.ui.search

import com.diary.app.data.DiaryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SearchRefineTest {

    private fun entry(
        id: String,
        body: String,
        title: String? = null,
        diaryDate: Long = 1_700_000_000_000L,
    ) = DiaryEntry(
        id = id,
        title = title,
        body = body,
        diaryDate = diaryDate,
        updatedAt = diaryDate,
    )

    @Test
    fun matchInMiddleOfBody_kept() {
        val e = entry("e1", "前面一大段文字".repeat(6) + "匹配内容在这里" + "后面一大段文字".repeat(6))

        val result = refineSearchResults(listOf(e), listOf(e), "匹配内容")

        assertEquals(listOf(e), result)
    }

    @Test
    fun snippetAroundMiddleMatch_containsTheMatch() {
        val body = "前面一大段文字".repeat(6) + "匹配内容在这里" + "后面一大段文字".repeat(6)

        val snippet = snippetAroundMatch(body, "匹配内容")

        assertTrue("snippet must contain the match", snippet.contains("匹配内容"))
        assertTrue("snippet must be a short window", snippet.length < 60)
    }

    @Test
    fun snippetWithBlankLinesBeforeMatch_keepsMatchVisibleInFirstThreeLines() {
        // Blank lines before the match push it past the card's 3-line
        // maxLines; the snippet must flatten newlines so the highlighted
        // match stays on screen.
        val body = "abc\n\ndef\n\n匹配内容在这里"

        val snippet = snippetAroundMatch(body, "匹配内容")

        val firstThreeLines = snippet.lineSequence().take(3).joinToString("\n")
        assertTrue(
            "match must fit in the first 3 lines, was:\n$snippet",
            firstThreeLines.contains("匹配内容"),
        )
    }

    @Test
    fun snippetAroundMatch_inSearchableBodyText_stillWorks() {
        val body = "前面一大段![img](11111111-1111-1111-1111-111111111111)匹配内容在这里后面一大段"

        val snippet = snippetAroundMatch(bodySearchText(body), "匹配内容")

        assertTrue(snippet.contains("匹配内容"))
    }

    @Test
    fun timestampBlockHit_filteredOut() {
        // SQL LIKE would hit the time inside the timestamp marker, but the
        // searchable text (paragraphs + subheadings) has no such match.
        val e = entry("e1", "正文![time](14:30:00)末尾")

        val result = refineSearchResults(listOf(e), listOf(e), "14:30")

        assertTrue(result.isEmpty())
    }

    @Test
    fun subheadingBlockHit_kept() {
        val e = entry("e1", "![sub](爬山记)\n正文段落")

        val result = refineSearchResults(listOf(e), listOf(e), "爬山")

        assertTrue(result.contains(e))
    }

    @Test
    fun bodySearchText_excludesTimestampsAndImages_keepsHeadings() {
        val body = "段落一\n![time](14:30:00)\n![img](11111111-1111-1111-1111-111111111111)\n![sub](小标题)\n段落二"

        val text = bodySearchText(body)

        assertEquals("段落一\n小标题\n段落二", text)
    }

    @Test
    fun markerUuidHitWithoutDisplayText_filteredOut() {
        // SQL LIKE would hit the uuid inside the image marker, but the
        // displayed text has no such match: the card could not highlight
        // anything, so it must not be shown.
        val e = entry("e1", "正文![img](11111111-1111-1111-1111-111111111111)末尾")

        val result = refineSearchResults(listOf(e), listOf(e), "1111")

        assertTrue(result.isEmpty())
    }

    @Test
    fun dateMatch_findsUntitledEntry() {
        val date = java.time.LocalDate.of(2026, 8, 8)
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val e = entry("e1", "纯文字正文", title = null, diaryDate = millis)

        val byMonthDay = refineSearchResults(emptyList(), listOf(e), "8月8日")
        val byFullDate = refineSearchResults(emptyList(), listOf(e), "2026年8月8日")

        assertTrue(byMonthDay.contains(e))
        assertTrue(byFullDate.contains(e))
    }

    @Test
    fun titledEntry_matchesByTitle() {
        val e = entry("e1", "正文", title = "爬山记")

        val result = refineSearchResults(listOf(e), listOf(e), "爬山")

        assertEquals(listOf(e), result)
    }

    @Test
    fun result_sortedByDiaryDateDesc_andDistinct() {
        val newer = entry("new", "甲内容", diaryDate = 2_000_000_000_000L)
        val older = entry("old", "乙内容", diaryDate = 1_000_000_000_000L)

        // SQL hit + date hit for the same entry must collapse to one.
        val result = refineSearchResults(listOf(older, newer), listOf(newer, older), "内容")

        assertEquals(listOf(newer, older), result)
    }

    @Test
    fun defaultTitleText_isTheFullDate() {
        val date = java.time.LocalDate.of(2026, 8, 8)
        val millis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        assertEquals("2026年8月8日", dateTextOf(millis))
    }
}
