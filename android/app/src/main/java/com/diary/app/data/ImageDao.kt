package com.diary.app.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One image joined with its owning entry, for the media library. */
data class ImageWithEntry(
    val id: String,
    val entryId: String,
    val uploaded: Boolean,
    val diaryDate: Long,
    val title: String?,
)

@Dao
interface ImageDao {

    @Upsert
    suspend fun upsert(image: ImageEntity)

    @Query("SELECT * FROM images WHERE uploaded = 0 ORDER BY updatedAt ASC")
    suspend fun getPendingUploads(): List<ImageEntity>

    @Query("SELECT * FROM images WHERE id = :id")
    suspend fun getById(id: String): ImageEntity?

    @Query("SELECT * FROM images WHERE entryId = :entryId")
    suspend fun getForEntry(entryId: String): List<ImageEntity>

    @Query("SELECT * FROM images WHERE entryId = :entryId")
    fun observeForEntry(entryId: String): Flow<List<ImageEntity>>

    @Query("DELETE FROM images WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM images WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: String)

    @Query("UPDATE images SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: String)

    /** Fixes the entryId of images created before their entry existed ("" at import time). */
    @Query("UPDATE images SET entryId = :entryId WHERE id IN (:ids)")
    suspend fun assignEntryId(entryId: String, ids: List<String>)

    /**
     * Every image of a non-deleted entry, newest diary date first. Used by
     * the media library; file bytes/sizes are read from disk, not stored.
     */
    @Query(
        """
        SELECT images.id, images.entryId, images.uploaded, entries.diaryDate, entries.title
        FROM images
        JOIN entries ON images.entryId = entries.id
        WHERE entries.deleted = 0
        ORDER BY entries.diaryDate DESC, images.updatedAt ASC
        """
    )
    fun observeAllWithEntry(): Flow<List<ImageWithEntry>>
}
