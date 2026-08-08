package com.diary.app

import android.app.Application
import com.diary.app.data.EntryRepository
import com.diary.app.data.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DiaryApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Open the database right away (off the main thread): the first
        // list query then hits a warm connection instead of paying the
        // WAL-recovery cost during the launch animation.
        applicationScope.launch { container.database.openHelper.writableDatabase }
        if (container.configStore.isConfigured()) {
            applicationScope.launch { container.syncEngine.sync() }
        }
    }
}

val Application.repository: EntryRepository
    get() = (this as DiaryApplication).container.entryRepository

val Application.syncEngine: SyncEngine
    get() = (this as DiaryApplication).container.syncEngine
