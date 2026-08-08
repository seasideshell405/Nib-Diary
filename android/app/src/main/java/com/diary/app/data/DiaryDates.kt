package com.diary.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Diary date conventions.
 *
 * A diary entry written between 00:00 and 04:00 belongs to the previous
 * day (the "after 4am" rule): the default diary date for a new entry is
 * today, or yesterday when written before 4am.
 */
object DiaryDates {

    /** The default diary date for a new entry written right now. */
    fun defaultDiaryDate(): LocalDate {
        val now = LocalDateTime.now()
        return if (now.hour < 4) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }

    /** The default diary date for a new entry at a given instant (millis). */
    fun diaryDateFor(nowMillis: Long): LocalDate {
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        return if (now.hour < 4) now.toLocalDate().minusDays(1) else now.toLocalDate()
    }
}
