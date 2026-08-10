package com.diary.app.ui.diary

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DiaryListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

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
    fun delete_removesCardFromListUi() = runBlocking {
        val entry = repo.create("要删的卡片", "正文", 1_000_000_000_000L, 1_000_000_000_000L)

        composeRule.setContent {
            DiaryListScreen(
                onOpenEntry = {},
                onSearch = {},
                onRandom = {},
                viewModel = vm,
            )
        }

        // The card is visible.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("要删的卡片").assertIsDisplayed()

        // Delete, then refresh (exactly what onDeleted does).
        repo.softDelete(entry.id, 1_100_000_000_000L)
        vm.refresh()

        // The card must leave the list UI.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(vm.entries.value.none { it.id == entry.id })
    }

    @Test
    fun delete_removesCardViaRoomInvalidationAlone() = runBlocking {
        val entry = repo.create("要删的卡片2", "正文", 1_000_000_000_000L, 1_000_000_000_000L)

        composeRule.setContent {
            DiaryListScreen(
                onOpenEntry = {},
                onSearch = {},
                onRandom = {},
                viewModel = vm,
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片2").fetchSemanticsNodes().isNotEmpty()
        }

        // No refresh() at all: only the Room invalidation may update the list.
        repo.softDelete(entry.id, 1_100_000_000_000L)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片2").fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Worst case for the flow: the DB row is tombstoned but the Room flow
     * emission has not arrived yet (or was lost). hide alone must
     * hide the card immediately — this is the exact onDeleted behavior.
     */
    @Test
    fun hide_hidesCardEvenIfFlowHasNotUpdated() = runBlocking {
        val entry = repo.create("要删的卡片3", "正文", 1_000_000_000_000L, 1_000_000_000_000L)

        composeRule.setContent {
            DiaryListScreen(
                onOpenEntry = {},
                onSearch = {},
                onRandom = {},
                viewModel = vm,
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片3").fetchSemanticsNodes().isNotEmpty()
        }

        // Simulate the delete callback without the DB write having landed
        // in the flow yet: the card must still leave the list.
        vm.hide(entry.id)

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片3").fetchSemanticsNodes().isEmpty()
        }
        // The data flow itself has NOT changed (nothing was deleted).
        assertTrue(vm.entries.value.any { it.id == entry.id })
    }
}
