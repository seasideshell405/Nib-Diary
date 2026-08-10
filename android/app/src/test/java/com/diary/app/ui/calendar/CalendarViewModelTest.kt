package com.diary.app.ui.calendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diary.app.data.AppDatabase
import com.diary.app.data.EntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CalendarViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private lateinit var vm: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repo = EntryRepository(db.entryDao())
        vm = CalendarViewModel(db.entryDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun selectedEntry_becomesNullAfterDelete() = runBlocking {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entry = repo.create("标题", "正文", start, start + 1000)

        vm.onDateSelect(today)

        // Simulate the calendar page being open (subscribing).
        val job = launch { vm.selectedEntry.collect {} }
        withTimeout(5_000) { vm.selectedEntry.first { it != null } }

        // Delete the entry from the browse page.
        repo.softDelete(entry.id, start + 2000)

        // The day card must disappear by itself (Room invalidation alone).
        withTimeout(5_000) { vm.selectedEntry.first { it == null } }
        assertNull(vm.selectedEntry.value)
        job.cancel()
    }

    @Test
    fun selectedEntry_clearsImmediatelyAfterDeleteWithRefresh() = runBlocking {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entry = repo.create("标题", "正文", start, start + 1000)

        vm.onDateSelect(today)

        // The calendar page was open before (subscribed), then left.
        val job = launch { vm.selectedEntry.collect {} }
        withTimeout(5_000) { vm.selectedEntry.first { it != null } }
        job.cancel()

        // Delete on the browse page: onDeleted calls calendarViewModel.refresh().
        repo.softDelete(entry.id, start + 2000)
        vm.refresh()

        // Re-enter the calendar page: the card must be gone right away,
        // without waiting for Room's async invalidation.
        withTimeout(5_000) { vm.selectedEntry.first { it == null } }
        assertNull(vm.selectedEntry.value)
    }

    @Test
    fun selectedEntry_unsubscribed_thenDelete_thenSubscribe_showsNull() = runBlocking {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entry = repo.create("标题", "正文", start, start + 1000)

        vm.onDateSelect(today)

        // First visit: subscribe, see the card, then leave the page.
        val job = launch { vm.selectedEntry.collect {} }
        withTimeout(5_000) { vm.selectedEntry.first { it != null } }
        job.cancel()

        // Delete while the calendar page is closed (no subscribers).
        repo.softDelete(entry.id, start + 2000)
        // Give Room's async invalidation time to reach the flow collector.
        Thread.sleep(500)

        // Re-enter the calendar page: must NOT show the deleted card.
        val job2 = launch { vm.selectedEntry.collect {} }
        withTimeout(5_000) { vm.selectedEntry.first() }
        assertNull(vm.selectedEntry.value)
        job2.cancel()
    }

    @Test
    fun dates_removeMarkAfterDelete() = runBlocking {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entry = repo.create("标题", "正文", start, start + 1000)

        withTimeout(5_000) { vm.dates.first { it.isNotEmpty() } }
        assertTrue(vm.dates.value.contains(start))

        repo.softDelete(entry.id, start + 2000)

        withTimeout(5_000) { vm.dates.first { it.isEmpty() } }
        assertTrue(vm.dates.value.isEmpty())
    }

    @Test
    fun dates_removeMarkAfterDeleteAndRefresh() = runBlocking {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entry = repo.create("标题", "正文", start, start + 1000)

        withTimeout(5_000) { vm.dates.first { it.isNotEmpty() } }

        repo.softDelete(entry.id, start + 2000)
        vm.refresh()

        withTimeout(5_000) { vm.dates.first { it.isEmpty() } }
        assertTrue(vm.dates.value.isEmpty())
    }

    /**
     * Worst case: the flow has not updated (nothing deleted). hide() alone
     * must mark the day card id and its date for the UI filter.
     */
    @Test
    fun hide_recordsIdAndDiaryDateForUiFilter() = runBlocking {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entry = repo.create("标题", "正文", start, start + 1000)

        vm.hide(entry.id)

        withTimeout(5_000) {
            vm.hiddenIds.first { entry.id in it }
            vm.hiddenDates.first { start in it }
        }
        assertTrue(entry.id in vm.hiddenIds.value)
        assertTrue(start in vm.hiddenDates.value)
    }
}
