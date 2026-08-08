package com.diary.app.ui.diary

import com.diary.app.data.BodyImages

/**
 * Body text with image markers stripped (timestamp and subheading markers
 * become their text, like the legacy bare-time format), for list summaries.
 */
fun strippedSummary(body: String): String =
    body.replace(BodyImages.SUB_MARKER_PATTERN) { it.groupValues[1] }
        .replace(BodyImages.TIME_MARKER_PATTERN) { it.groupValues[1] }
        .replace(BodyImages.MARKER_PATTERN, "")
