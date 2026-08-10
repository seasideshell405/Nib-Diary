package com.diary.app.ui.detail

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diary.app.data.AppDatabase
import com.diary.app.data.EntryRepository
import com.diary.app.data.FakeDiaryApi
import com.diary.app.data.FakeImageApi
import com.diary.app.data.ImageFileStore
import com.diary.app.data.SyncEngine
import com.diary.app.data.SyncStateStore
import com.diary.app.ui.diary.DiaryListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DeleteFlowTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private lateinit var listVm: DiaryListViewModel
    private lateinit var detailVm: DetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = EntryRepository(db.entryDao())
        listVm = DiaryListViewModel(repo)
        val engine = SyncEngine(
            api = FakeDiaryApi(),
            imageApi = FakeImageApi(),
            dao = db.entryDao(),
            imageDao = db.imageDao(),
            fileStore = ImageFileStore(context),
            cursorStore = SyncStateStore(context),
        )
        detailVm = DetailViewModel(db.entryDao(), engine, "placeholder")
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * The full delete chain: DetailViewModel.delete(onDone) → onDone runs
     * listViewModel.refresh() → the card leaves the browse list.
     */
    @Test
    fun deleteEntry_thenOnDoneRefresh_removesCardFromList() = runBlocking {
        val entry = repo.create("要删的卡片", "正文", 1_000_000_000_000L, 1_000_000_000_000L)
        detailVm = DetailViewModel(db.entryDao(), engineFor(), entry.id)

        withTimeout(5_000) { listVm.entries.first { it.isNotEmpty() } }
        assertTrue(listVm.entries.value.any { it.id == entry.id })

        var onDoneCalled = false
        detailVm.delete {
            onDoneCalled = true
            listVm.refresh()
        }

        withTimeout(5_000) { listVm.entries.first { list -> list.none { it.id == entry.id } } }
        assertTrue(onDoneCalled)
        assertTrue(listVm.entries.value.none { it.id == entry.id })
    }

    /**
     * Same chain but WITHOUT calling refresh() in onDone: the Room
     * invalidation alone must still remove the card (the flow is the
     * single source of truth).
     */
    @Test
    fun deleteEntry_flowInvalidationAlone_removesCardFromList() = runBlocking {
        val entry = repo.create("要删的卡片2", "正文", 1_000_000_000_000L, 1_000_000_000_000L)
        detailVm = DetailViewModel(db.entryDao(), engineFor(), entry.id)

        withTimeout(5_000) { listVm.entries.first { it.isNotEmpty() } }

        detailVm.delete { /* no refresh at all */ }

        withTimeout(5_000) { listVm.entries.first { list -> list.none { it.id == entry.id } } }
        assertTrue(listVm.entries.value.none { it.id == entry.id })
    }

    private fun engineFor(): SyncEngine {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return SyncEngine(
            api = FakeDiaryApi(),
            imageApi = FakeImageApi(),
            dao = db.entryDao(),
            imageDao = db.imageDao(),
            fileStore = ImageFileStore(context),
            cursorStore = SyncStateStore(context),
        )
    }
}
