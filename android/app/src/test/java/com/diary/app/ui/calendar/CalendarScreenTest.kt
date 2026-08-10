package com.diary.app.ui.calendar

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diary.app.data.AppDatabase
import com.diary.app.data.EntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CalendarScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    /**
     * User repro: open the calendar (day card visible), switch to the
     * browse page (calendar leaves composition), delete the entry there,
     * switch back to the calendar. The deleted card must be gone.
     */
    @Test
    fun dayCard_disappearsAfterDeleteWhileCalendarClosed() = runBlocking {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entry = repo.create("要删的卡片", "正文", start, start + 1000)
        vm.onDateSelect(today)

        var showCalendar = true
        composeRule.setContent {
            if (showCalendar) {
                CalendarScreen(onOpenEntry = {}, viewModel = vm)
            }
        }

        // The day card is visible.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片").fetchSemanticsNodes().isNotEmpty()
        }

        // Switch to the browse page: the calendar leaves composition.
        showCalendar = false
        composeRule.waitForIdle()

        // Delete from the browse page.
        repo.softDelete(entry.id, start + 2_000)

        // Switch back to the calendar page.
        showCalendar = true
        composeRule.waitForIdle()

        // The deleted card must disappear (short transient lag is the
        // Room invalidation; a persistent stale card is the bug).
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片").fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Worst case for the flow: the DB row is not even tombstoned yet (the
     * emission has not arrived or was lost). hide() alone must hide the
     * day card immediately — this is the onDeleted behavior.
     */
    @Test
    fun hide_hidesDayCardEvenIfFlowHasNotUpdated() = runBlocking {
        val today = LocalDate.now()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entry = repo.create("要删的卡片", "正文", start, start + 1000)
        vm.onDateSelect(today)

        composeRule.setContent {
            CalendarScreen(onOpenEntry = {}, viewModel = vm)
        }

        // The day card is visible.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片").fetchSemanticsNodes().isNotEmpty()
        }

        // Simulate the delete callback without the DB write having landed
        // in the flow yet: the card must still leave the page.
        vm.hide(entry.id)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片").fetchSemanticsNodes().isEmpty()
        }
        // The data flow itself has NOT changed (nothing was deleted).
        assertNotNull(vm.selectedEntry.value)
    }
}
