package com.diary.app.ui.diary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diary.app.data.AppDatabase
import com.diary.app.data.EntryRepository
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
// Plain Application, not DiaryApplication: same reason as EntryRepositoryTest.
@Config(sdk = [35], application = android.app.Application::class)
class DiaryListViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private lateinit var vm: DiaryListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repo = EntryRepository(db.entryDao())
        vm = DiaryListViewModel(repo)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun entries_removeSoftDeletedEntry_afterRefresh() = runBlocking {
        val entry = repo.create("标题", "正文", 1_000_000_000_000L, 1_000_000_000_000L)

        // Wait for the list to load (Eagerly-started flow, real IO query).
        withTimeout(5_000) { vm.entries.first { it.isNotEmpty() } }

        repo.softDelete(entry.id, 1_100_000_000_000L)
        vm.refresh()

        // The deleted card must leave the list.
        withTimeout(5_000) { vm.entries.first { list -> list.none { it.id == entry.id } } }
        assertTrue(vm.entries.value.none { it.id == entry.id })
    }

    @Test
    fun entries_removeSoftDeletedEntry_viaFlowInvalidation() = runBlocking {
        val entry = repo.create("标题", "正文", 1_000_000_000_000L, 1_000_000_000_000L)

        withTimeout(5_000) { vm.entries.first { it.isNotEmpty() } }

        repo.softDelete(entry.id, 1_100_000_000_000L)

        // No refresh() call: the Room invalidation alone must update the list.
        withTimeout(5_000) { vm.entries.first { list -> list.none { it.id == entry.id } } }
        assertTrue(vm.entries.value.none { it.id == entry.id })
    }

    @Test
    fun entries_surviveUnrelatedFlowRestart() = runBlocking {
        val kept = repo.create("保留", "正文", 1_000_000_000_000L, 1_000_000_000_000L)
        val removed = repo.create("删除", "正文", 1_500_000_000_000L, 1_500_000_000_000L)

        withTimeout(5_000) { vm.entries.first { it.size == 2 } }

        repo.softDelete(removed.id, 1_600_000_000_000L)
        vm.refresh()
        // A second refresh must not resurrect the tombstoned entry.
        vm.refresh()

        withTimeout(5_000) { vm.entries.first { list -> list.none { it.id == removed.id } } }
        assertTrue(vm.entries.value.any { it.id == kept.id })
        assertTrue(vm.entries.value.none { it.id == removed.id })
    }

    /**
     * The exact user repro: open the app, switch to the calendar tab and
     * back (both switches call refresh() via LaunchedEffect), then create
     * an entry, then delete it. The deleted card must leave the list.
     */
    @Test
    fun userScenario_tabSwitchThenCreateThenDelete() = runBlocking {
        // Open the app (empty list loads).
        withTimeout(5_000) { vm.entries.first() }

        // Tab switch away and back: LaunchedEffect(currentRoute) refreshes
        // both view models on every tab entry.
        vm.refresh()
        vm.refresh()

        // New entry, saved in the editor.
        val entry = repo.create("新笔记", "正文", 1_000_000_000_000L, 1_000_000_000_000L)
        withTimeout(5_000) { vm.entries.first { list -> list.any { it.id == entry.id } } }

        // Delete it from the reading sheet.
        repo.softDelete(entry.id, 1_100_000_000_000L)
        vm.refresh()

        withTimeout(5_000) { vm.entries.first { list -> list.none { it.id == entry.id } } }
        assertTrue(vm.entries.value.none { it.id == entry.id })
    }
}
