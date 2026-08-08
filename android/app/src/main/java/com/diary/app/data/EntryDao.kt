package com.diary.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Upsert
    suspend fun upsert(entry: DiaryEntry)

    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY diaryDate DESC")
    fun observeActive(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY diaryDate DESC")
    suspend fun getActive(): List<DiaryEntry>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: String): DiaryEntry?

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: String): Flow<DiaryEntry?>

    @Query("SELECT * FROM entries WHERE deleted = 0 AND diaryDate >= :dayStart AND diaryDate < :dayEnd LIMIT 1")
    suspend fun getByDiaryDate(dayStart: Long, dayEnd: Long): DiaryEntry?

    @Query("UPDATE entries SET deleted = 1, deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM entries WHERE updatedAt > :since ORDER BY updatedAt ASC")
    suspend fun getChangesSince(since: Long): List<DiaryEntry>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM entries")
    suspend fun getMaxUpdatedAt(): Long

    @Query("SELECT DISTINCT diaryDate FROM entries WHERE deleted = 0")
    fun observeActiveDiaryDates(): Flow<List<Long>>

    @Query("SELECT DISTINCT diaryDate FROM entries WHERE deleted = 0")
    suspend fun getActiveDiaryDates(): List<Long>

    @Query("SELECT * FROM entries WHERE deleted = 0 AND diaryDate >= :dayStart AND diaryDate < :dayEnd ORDER BY updatedAt DESC")
    fun observeByDiaryDate(dayStart: Long, dayEnd: Long): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM entries WHERE deleted = 0 AND diaryDate >= :dayStart AND diaryDate < :dayEnd LIMIT 1")
    fun observeFirstByDiaryDate(dayStart: Long, dayEnd: Long): Flow<DiaryEntry?>

    @Query("SELECT * FROM entries WHERE deleted = 0 AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%') ORDER BY diaryDate DESC")
    fun search(query: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM entries WHERE deleted = 0 ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomActive(): DiaryEntry?

    @Query("SELECT COUNT(*) FROM entries WHERE deleted = 0")
    suspend fun countActive(): Int
}
