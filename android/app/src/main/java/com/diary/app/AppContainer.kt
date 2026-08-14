package com.diary.app

import android.content.Context
import androidx.room.Room
import com.diary.app.data.AppDatabase
import com.diary.app.data.AppearanceStore
import com.diary.app.data.ConfigStore
import com.diary.app.data.DiaryApi
import com.diary.app.data.EntryRepository
import com.diary.app.data.ImageApi
import com.diary.app.data.ImageApiContract
import com.diary.app.data.ImageFileStore
import com.diary.app.data.ImageImporter
import com.diary.app.data.LockStore
import com.diary.app.data.ProfileRepository
import com.diary.app.data.ProfileStore
import com.diary.app.data.SyncEngine
import com.diary.app.data.SyncStateStore
import com.diary.app.data.UiPrefsStore
import com.diary.app.data.UpdateManager

class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "diary.db",
    ).build()

    val configStore = ConfigStore(context)
    val syncStateStore = SyncStateStore(context)
    val imageFileStore = ImageFileStore(context)
    val imageImporter = ImageImporter(context, imageFileStore)
    val profileStore = ProfileStore(context)
    val lockStore = LockStore(context)
    val appearanceStore = AppearanceStore(context)
    val uiPrefs = UiPrefsStore(context)

    val entryRepository: EntryRepository = EntryRepository(database.entryDao())

    private val api = DiaryApi.create(configStore)
    val imageApi: ImageApiContract = ImageApi.create(configStore)

    val syncEngine = SyncEngine(
        api = api,
        imageApi = imageApi,
        dao = database.entryDao(),
        imageDao = database.imageDao(),
        fileStore = imageFileStore,
        cursorStore = syncStateStore,
    )

    /** Profile persistence: local + server. */
    val profileRepository = ProfileRepository(profileStore, api)

    /** App self-update via GitHub Releases. */
    val updateManager = UpdateManager(context)
}
