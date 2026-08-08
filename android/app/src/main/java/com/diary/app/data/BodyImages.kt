package com.diary.app.data

import java.util.UUID

/**
 * A content block inside a diary body: a paragraph of text, an image, a
 * timestamp, or a subheading. The body is a block sequence: newlines
 * separate blocks, a marker owns its own line. The model is open for more
 * block types (video, audio, ...) later.
 */
sealed interface ContentBlock {
    data class Paragraph(val text: String) : ContentBlock
    data class Image(val id: String) : ContentBlock
    data class Timestamp(val text: String) : ContentBlock
    data class Heading(val text: String) : ContentBlock
}

object BodyImages {

    val MARKER_PATTERN = Regex("""!\[img\]\(([0-9a-fA-F-]{36})\)""")

    // A timestamp block is a marker on its own line: ![time](HH:mm:ss).
    val TIME_MARKER_PATTERN = Regex("""!\[time\]\((\d{1,2}:\d{2}:\d{2})\)""")

    // A subheading block: ![sub](text). Text may contain anything except
    // ')' or a newline; leading/trailing whitespace is trimmed on insert.
    val SUB_MARKER_PATTERN = Regex("""!\[sub\]\(([^)\n]+)\)""")

    /** Any body marker (image, timestamp, subheading), for editor span/line handling. */
    val ALL_MARKER_PATTERN = Regex("""!\[(?:img|time|sub)\]\([^)]+\)""")

    // Legacy timestamp blocks: a bare time on its own line (old editor
    // format) or the pre-block "yyyy-MM-dd HH:mm" format still render as
    // one, but are no longer produced by the editor.
    private val TIME_PATTERN = Regex("""^\d{1,2}:\d{2}:\d{2}$""")
    private val LEGACY_TIME_PATTERN = Regex("""^\d{4}-\d{2}-\d{2} \d{1,2}:\d{2}$""")

    fun newMarker(): String = "![img](${UUID.randomUUID()})"

    fun extractIds(body: String): List<String> =
        MARKER_PATTERN.findAll(body).map { it.groupValues[1] }.toList()

    fun firstId(body: String): String? =
        MARKER_PATTERN.find(body)?.groupValues?.get(1)

    /**
     * Parses [body] into a block sequence. Newlines delimit blocks;
     * blank lines collapse (no empty blocks). Legacy bodies with an image
     * marker embedded inside a paragraph are split into separate blocks.
     */
    fun parseBlocks(body: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        for (rawLine in body.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // A bare time on its own line is a legacy timestamp block.
            if (TIME_PATTERN.matches(line) || LEGACY_TIME_PATTERN.matches(line)) {
                blocks.add(ContentBlock.Timestamp(line))
                continue
            }
            // New-style timestamp marker, also on its own line.
            val timeMarker = TIME_MARKER_PATTERN.matchEntire(line)
            if (timeMarker != null) {
                blocks.add(ContentBlock.Timestamp(timeMarker.groupValues[1]))
                continue
            }
            // Subheading marker, on its own line.
            val subMarker = SUB_MARKER_PATTERN.matchEntire(line)
            if (subMarker != null) {
                blocks.add(ContentBlock.Heading(subMarker.groupValues[1].trim()))
                continue
            }

            var last = 0
            var found = false
            for (match in MARKER_PATTERN.findAll(line)) {
                if (match.range.first > last) {
                    val text = line.substring(last, match.range.first).trim()
                    if (text.isNotEmpty()) blocks.add(ContentBlock.Paragraph(text))
                }
                blocks.add(ContentBlock.Image(match.groupValues[1]))
                last = match.range.last + 1
                found = true
            }
            if (!found) {
                blocks.add(ContentBlock.Paragraph(line))
            } else if (last < line.length) {
                val text = line.substring(last).trim()
                if (text.isNotEmpty()) blocks.add(ContentBlock.Paragraph(text))
            }
        }
        return blocks
    }
}
