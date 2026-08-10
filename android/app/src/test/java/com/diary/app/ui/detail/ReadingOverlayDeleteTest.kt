package com.diary.app.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.diary.app.data.AppDatabase
import com.diary.app.data.EntryRepository
import com.diary.app.data.FakeDiaryApi
import com.diary.app.data.FakeImageApi
import com.diary.app.data.ImageFileStore
import com.diary.app.data.SyncEngine
import com.diary.app.data.SyncStateStore
import com.diary.app.ui.diary.DiaryListScreen
import com.diary.app.ui.diary.DiaryListViewModel
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
class ReadingOverlayDeleteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private lateinit var listVm: DiaryListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repo = EntryRepository(db.entryDao())
        listVm = DiaryListViewModel(repo)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun detailVm(entryId: String): DetailViewModel {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val engine = SyncEngine(
            api = FakeDiaryApi(),
            imageApi = FakeImageApi(),
            dao = db.entryDao(),
            imageDao = db.imageDao(),
            fileStore = ImageFileStore(context),
            cursorStore = SyncStateStore(context),
        )
        return DetailViewModel(db.entryDao(), engine, entryId)
    }

    /**
     * The exact user flow on the browse page: tap the card → reading
     * sheet → delete → confirm → the card must leave the list.
     */
    @Test
    fun deleteThroughReadingOverlay_removesCardFromBrowseList() = runBlocking {
        val entry = repo.create("要删的卡片", "正文", 1_000_000_000_000L, 1_000_000_000_000L)
        val detailVm = detailVm(entry.id)

        var readingEntryId by mutableStateOf<String?>(null)

        composeRule.setContent {
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
                        listVm.refresh()
                    },
                    viewModel = detailVm,
                )
            }
        }

        // The card is visible on the browse page.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap the card: the reading sheet opens with a delete button.
        composeRule.onNodeWithText("要删的卡片").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("删除").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap delete: the confirm dialog appears.
        composeRule.onAllNodesWithText("删除")[0].performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("删除这篇日记？").fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm: the reading sheet closes and the card must leave the list.
        val confirms = composeRule.onAllNodesWithText("删除").fetchSemanticsNodes()
        composeRule.onAllNodesWithText("删除")[confirms.size - 1].performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("要删的卡片").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(listVm.entries.value.none { it.id == entry.id })
    }
}
