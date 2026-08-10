package com.diary.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diary.app.data.AppDatabase
import com.diary.app.data.EntryRepository
import com.diary.app.data.FakeDiaryApi
import com.diary.app.data.FakeImageApi
import com.diary.app.data.ImageFileStore
import com.diary.app.data.SyncEngine
import com.diary.app.data.SyncStateStore
import com.diary.app.ui.calendar.CalendarScreen
import com.diary.app.ui.calendar.CalendarViewModel
import com.diary.app.ui.detail.DetailViewModel
import com.diary.app.ui.detail.ReadingOverlay
import com.diary.app.ui.diary.DiaryListScreen
import com.diary.app.ui.diary.DiaryListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Exact user repro: open the app on the browse page, switch to the
 * calendar tab, switch back to the browse page, then delete a card from
 * the reading sheet. The card must leave the browse list immediately,
 * without switching pages again.
 *
 * Root cause this guards against: after a tab round trip the diary
 * NavBackStackEntry used to stay CREATED (never RESUMED), so
 * collectAsStateWithLifecycle on the browse page stopped collecting and
 * the deleted card stayed visible (and clickable) until the next page
 * switch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DiaryTabRoundTripDeleteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private lateinit var listVm: DiaryListViewModel
    private lateinit var calendarVm: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repo = EntryRepository(db.entryDao())
        listVm = DiaryListViewModel(repo)
        calendarVm = CalendarViewModel(db.entryDao())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** The exact selectTab logic from DiaryApp. */
    private fun selectTabLogic(nav: NavHostController, route: String) {
        val topRoute = nav.currentBackStackEntry?.destination?.route
        if (topRoute == route) return
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    @Test
    fun deleteAfterCalendarRoundTrip_hidesCardImmediately() = runBlocking {
        val title = "Delete Me Card"
        val entry = repo.create(title, "body", 1_000_000_000_000L, 1_000_000_000_000L)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = SyncEngine(
            api = FakeDiaryApi(),
            imageApi = FakeImageApi(),
            dao = db.entryDao(),
            imageDao = db.imageDao(),
            fileStore = ImageFileStore(context),
            cursorStore = SyncStateStore(context),
        )
        val detailVm = DetailViewModel(db.entryDao(), engine, entry.id)

        var readingEntryId by mutableStateOf<String?>(null)
        lateinit var nav: NavHostController

        composeRule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = "diary") {
                composable("diary") {
                    DiaryListScreen(
                        onOpenEntry = { id -> readingEntryId = id },
                        onSearch = {},
                        onRandom = {},
                        viewModel = listVm,
                    )
                    readingEntryId?.let { id ->
                        ReadingOverlay(
                            entryId = id,
                            onDismiss = { readingEntryId = null },
                            onEdit = {},
                            onDeleted = {
                                readingEntryId = null
                                // Exactly what DiaryApp.onDeleted does.
                                listVm.hide(id)
                                calendarVm.hide(id)
                                listVm.refresh()
                                calendarVm.refresh()
                            },
                            viewModel = detailVm,
                        )
                    }
                }
                composable("calendar") {
                    CalendarScreen(onOpenEntry = {}, viewModel = calendarVm)
                }
            }
        }
        composeRule.waitForIdle()

        // The card is visible on the browse page.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        // User repro: switch to calendar, then back to the browse page.
        composeRule.runOnIdle { selectTabLogic(nav, "calendar") }
        composeRule.waitForIdle()
        composeRule.runOnIdle { selectTabLogic(nav, "diary") }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        // Open the card: the reading sheet appears.
        composeRule.onNodeWithText(title).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("\u5220\u9664").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap delete: the confirm dialog appears.
        composeRule.onAllNodesWithText("\u5220\u9664")[0].performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("\u5220\u9664\u8fd9\u7bc7\u65e5\u8bb0\uff1f").fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm: the reading sheet closes and the card must leave the
        // browse list WITHOUT another page switch.
        val confirms = composeRule.onAllNodesWithText("\u5220\u9664").fetchSemanticsNodes()
        composeRule.onAllNodesWithText("\u5220\u9664")[confirms.size - 1].performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty()
        }
        assertTrue(listVm.entries.value.none { it.id == entry.id })
    }
}