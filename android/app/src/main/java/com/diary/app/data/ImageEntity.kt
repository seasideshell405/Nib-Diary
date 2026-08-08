package com.diary.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val uploaded: Boolean = false,
    val updatedAt: Long,
)
