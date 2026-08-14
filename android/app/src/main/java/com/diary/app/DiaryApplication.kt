package com.diary.app

import android.app.Application
import android.os.Build
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
        // Robolectric 测试环境下跳过启动任务：异步协程会在测试重置后访问已关闭
        // 的 SQLite 连接，导致 "Illegal connection pointer" 的 flaky 失败。
        if (Build.FINGERPRINT.contains("robolectric")) return
        // Open the database right away (off the main thread): the first
        // list query then hits a warm connection instead of paying the
        // WAL-recovery cost during the launch animation.
        applicationScope.launch { container.database.openHelper.writableDatabase }
        if (container.configStore.isConfigured()) {
            applicationScope.launch { container.syncEngine.sync() }
        }
        // Silent update check (toggleable): only surfaces a dialog when a
        // newer version exists.
        if (container.uiPrefs.startupUpdateCheckEnabled) {
            applicationScope.launch { container.updateManager.check(silent = true) }
        }
    }
}

val Application.repository: EntryRepository
    get() = (this as DiaryApplication).container.entryRepository

val Application.syncEngine: SyncEngine
    get() = (this as DiaryApplication).container.syncEngine
