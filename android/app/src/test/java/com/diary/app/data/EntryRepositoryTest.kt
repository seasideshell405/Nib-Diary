package com.diary.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Plain Application, not DiaryApplication: the app's onCreate spawns a
// background DB-open coroutine that races the test sandbox and leaks
// uncaught SQLite exceptions between tests.
@Config(sdk = [35], application = android.app.Application::class)
class EntryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repo = EntryRepository(db.entryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun create_assignsUuidAndTimestamp() = runTest {
        val entry = repo.create("标题", "正文", 1_600_000_000_000L, 1_700_000_000_000L)

        assertTrue(entry.id.isNotBlank())
        assertEquals(1_700_000_000_000L, entry.updatedAt)
        assertTrue(entry.id.length > 20)
    }

    @Test
    fun observeActive_returnsEntriesOrderedByDiaryDateDesc() = runTest {
        repo.create("旧的", "正文", 1_000_000_000_000L, 1_000_000_000_000L)
        repo.create("新的", "正文", 2_000_000_000_000L, 2_000_000_000_000L)
        repo.create("中间", "正文", 1_500_000_000_000L, 1_500_000_000_000L)

        val entries = repo.observeEntries().first()
        assertEquals(listOf("新的", "中间", "旧的"), entries.map { it.title })
    }

    @Test
    fun softDelete_hidesFromActiveList() = runTest {
        val entry = repo.create("要删的", "正文", 1_000_000_000_000L, 1_000_000_000_000L)

        repo.softDelete(entry.id, 1_100_000_000_000L)

        val active = repo.observeEntries().first()
        assertTrue(active.isEmpty())

        val tombstone = repo.getEntry(entry.id)
        assertTrue(tombstone!!.deleted)
        assertEquals(1_100_000_000_000L, tombstone.deletedAt)
    }

    @Test
    fun update_refreshesContentAndTimestamp() = runTest {
        val entry = repo.create("原标题", "原正文", 1_000_000_000_000L, 1_000_000_000_000L)

        repo.update(entry.copy(title = "新标题", body = "新正文"), 1_200_000_000_000L)

        val updated = repo.getEntry(entry.id)
        assertEquals("新标题", updated!!.title)
        assertEquals("新正文", updated.body)
        assertEquals(1_200_000_000_000L, updated.updatedAt)
    }

    @Test
    fun getById_returnsNullForUnknownId() = runTest {
        assertNull(repo.getEntry("does-not-exist"))
    }

    @Test
    fun create_persistsMoodWeatherAndDiaryDate() = runTest {
        val entry = repo.create(
            title = "带心情",
            body = "正文",
            diaryDate = 1_600_000_000_000L,
            now = 1_700_000_000_000L,
            mood = "happy",
            weather = "sunny",
        )

        val loaded = repo.getEntry(entry.id)
        assertEquals("happy", loaded!!.mood)
        assertEquals("sunny", loaded.weather)
        assertEquals(1_600_000_000_000L, loaded.diaryDate)
    }

    @Test
    fun search_matchesTitleAndBody() = runTest {
        repo.create("爬山记", "今天去了香山", 1_000_000_000_000L, 1_000_000_000_000L)
        repo.create("做饭", "炒了个番茄炒蛋", 1_100_000_000_000L, 1_100_000_000_000L)
        repo.create("无图", "![img](11111111-1111-1111-1111-111111111111) 只有图", 1_200_000_000_000L, 1_200_000_000_000L)

        val byTitle = db.entryDao().search("爬山").first()
        assertEquals(1, byTitle.size)
        assertEquals("爬山记", byTitle[0].title)

        val byBody = db.entryDao().search("番茄").first()
        assertEquals(1, byBody.size)

        val byMarker = db.entryDao().search("11111111").first()
        assertEquals(1, byMarker.size)
    }

    @Test
    fun observeActiveDiaryDates_returnsDistinctDates() = runTest {
        repo.create("a", "x", 1_000_000_000_000L, 1_000_000_000_000L)
        repo.create("b", "x", 1_000_000_000_000L, 1_100_000_000_000L)
        repo.create("c", "x", 2_000_000_000_000L, 2_000_000_000_000L)

        val dates = db.entryDao().observeActiveDiaryDates().first()
        assertEquals(2, dates.size)
    }

    @Test
    fun observeAllWithEntry_joinsImagesToEntriesNewestDiaryDateFirst() = runTest {
        val old = repo.create("旧条目", "![img](aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa)", 1_000_000_000_000L, 1_000_000_000_000L)
        val recent = repo.create("新条目", "![img](bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb)", 2_000_000_000_000L, 2_000_000_000_000L)
        val imageDao = db.imageDao()
        imageDao.upsert(ImageEntity(id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", entryId = old.id, uploaded = true, updatedAt = 1L))
        imageDao.upsert(ImageEntity(id = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", entryId = recent.id, uploaded = true, updatedAt = 2L))

        val rows = imageDao.observeAllWithEntry().first()

        assertEquals(2, rows.size)
        assertEquals(listOf("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), rows.map { it.id })
        assertEquals(listOf("新条目", "旧条目"), rows.map { it.title })
        assertEquals(listOf(2_000_000_000_000L, 1_000_000_000_000L), rows.map { it.diaryDate })
        assertEquals(listOf(recent.id, old.id), rows.map { it.entryId })
    }

    @Test
    fun observeAllWithEntry_hidesImagesOfSoftDeletedEntries() = runTest {
        val entry = repo.create("要删的", "![img](cccccccc-cccc-cccc-cccc-cccccccccccc)", 1_000_000_000_000L, 1_000_000_000_000L)
        val imageDao = db.imageDao()
        imageDao.upsert(ImageEntity(id = "cccccccc-cccc-cccc-cccc-cccccccccccc", entryId = entry.id, uploaded = true, updatedAt = 1L))

        assertEquals(1, imageDao.observeAllWithEntry().first().size)

        repo.softDelete(entry.id, 1_100_000_000_000L)

        assertTrue(imageDao.observeAllWithEntry().first().isEmpty())
    }

    @Test
    fun assignEntryId_fixesImagesCreatedBeforeTheirEntryExisted() = runTest {
        val entry = repo.create("新条目", "![img](dddddddd-dddd-dddd-dddd-dddddddddddd)", 1_000_000_000_000L, 1_000_000_000_000L)
        val imageDao = db.imageDao()
        // importImage writes entryId="" while the entry does not exist yet.
        imageDao.upsert(ImageEntity(id = "dddddddd-dddd-dddd-dddd-dddddddddddd", entryId = "", uploaded = false, updatedAt = 1L))

        imageDao.assignEntryId(entry.id, listOf("dddddddd-dddd-dddd-dddd-dddddddddddd"))

        val row = imageDao.observeAllWithEntry().first().single()
        assertEquals(entry.id, row.entryId)
    }
}
