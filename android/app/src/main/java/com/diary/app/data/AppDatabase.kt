package com.diary.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DiaryEntry::class, ImageEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun imageDao(): ImageDao
}
