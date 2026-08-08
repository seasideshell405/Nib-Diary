package com.diary.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class DiaryEntry(
    @PrimaryKey val id: String,
    val title: String? = null,
    val body: String,
    val mood: String? = null,
    val weather: String? = null,
    val diaryDate: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deletedAt: Long? = null,
)
