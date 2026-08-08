package com.diary.app.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class EntryRepository(private val dao: EntryDao) {

    fun observeEntries(): Flow<List<DiaryEntry>> = dao.observeActive()

    /** One-shot read of the active list, for forced refreshes. */
    suspend fun getActive(): List<DiaryEntry> = dao.getActive()

    suspend fun getEntry(id: String): DiaryEntry? = dao.getById(id)

    suspend fun getByDiaryDate(dayStart: Long, dayEnd: Long): DiaryEntry? =
        dao.getByDiaryDate(dayStart, dayEnd)

    suspend fun create(
        title: String?,
        body: String,
        diaryDate: Long,
        now: Long,
        mood: String? = null,
        weather: String? = null,
    ): DiaryEntry {
        val entry = DiaryEntry(
            id = UUID.randomUUID().toString(),
            title = title,
            body = body,
            mood = mood,
            weather = weather,
            diaryDate = diaryDate,
            updatedAt = now,
        )
        dao.upsert(entry)
        return entry
    }

    suspend fun update(entry: DiaryEntry, now: Long) {
        dao.upsert(entry.copy(updatedAt = now))
    }

    suspend fun softDelete(id: String, now: Long) {
        dao.softDelete(id, now)
    }
}
