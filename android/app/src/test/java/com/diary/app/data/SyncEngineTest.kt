package com.diary.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fake DiaryApi that records requests and serves canned responses,
 * so the SyncEngine can be tested without a network.
 */
class FakeDiaryApi : DiaryApiContract {
    var serverTime: Long = 10_000
    val pushedRequests = mutableListOf<SyncRequest>()

    private val server = mutableMapOf<String, WireEntry>()
    private var seq = 0L
    private val entrySeq = mutableMapOf<String, Long>()
    var failWith: SyncFailure? = null

    override suspend fun sync(entries: List<WireEntry>, sinceSeq: Long): Result<SyncResponse> {
        val failure = failWith
        if (failure != null) return Result.failure(failure)

        pushedRequests.add(SyncRequest(entries = entries, sinceSeq = sinceSeq))
        for (e in entries) {
            val current = server[e.id]
            if (current == null || e.updatedAt >= current.updatedAt) {
                server[e.id] = e
                seq++
                entrySeq[e.id] = seq
            }
        }
        val changes = server.entries
            .filter { (id, _) -> entrySeq[id]!! > sinceSeq }
            .sortedBy { (id, _) -> entrySeq[id]!! }
            .map { (_, e) -> e }
        return Result.success(
            SyncResponse(
                changes = changes,
                serverSeq = seq,
                serverTime = serverTime,
            )
        )
    }

    fun seedServer(entries: List<WireEntry>) {
        for (e in entries) {
            server[e.id] = e
            seq++
            entrySeq[e.id] = seq
        }
    }

    override suspend fun getProfile(): Result<WireProfile> =
        Result.success(WireProfile(nickname = "测试", signature = "", avatarUrl = "", updatedAt = 0))

    override suspend fun putProfile(profile: WireProfile): Result<WireProfile> = Result.success(profile)
}

class FakeImageApi : ImageApiContract {
    val uploaded = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()
    var failUpload = false
    var failDownload = false

    override suspend fun upload(id: String, entryId: String, file: java.io.File): Boolean {
        if (failUpload) return false
        uploaded.add(id to entryId)
        return true
    }

    override suspend fun download(id: String, target: java.io.File): Boolean {
        if (failDownload) return false
        target.writeBytes(byteArrayOf(1, 2, 3))
        return true
    }

    override suspend fun deleteRemote(id: String): Boolean {
        deleted.add(id)
        return true
    }
}

@RunWith(RobolectricTestRunner::class)
// Plain Application, not DiaryApplication: the app's onCreate spawns a
// background DB-open coroutine that races the test sandbox and leaks
// uncaught SQLite exceptions between tests.
@Config(sdk = [35], application = android.app.Application::class)
class SyncEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: EntryDao
    private lateinit var fakeApi: FakeDiaryApi
    private lateinit var fakeImageApi: FakeImageApi
    private lateinit var cursorStore: SyncStateStore
    private lateinit var engine: SyncEngine
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.entryDao()
        fakeApi = FakeDiaryApi()
        fakeImageApi = FakeImageApi()
        cursorStore = SyncStateStore(context)
        engine = SyncEngine(
            api = fakeApi,
            imageApi = fakeImageApi,
            dao = dao,
            imageDao = db.imageDao(),
            fileStore = ImageFileStore(context),
            cursorStore = cursorStore,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun firstSync_pullsEverythingAsBaseline() = runTest {
        fakeApi.seedServer(
            listOf(
                WireEntry(id = "r1", body = "服务器条目", diaryDate = 1, updatedAt = 100),
                WireEntry(id = "r2", body = "另一条", diaryDate = 2, updatedAt = 200),
            )
        )

        engine.sync()

        val local = dao.observeActive().first()
        assertEquals(2, local.size)
        assertEquals(2L, cursorStore.getServerSeq())
    }

    @Test
    fun sync_pushesLocalChangesAndAdvancesWatermark() = runTest {
        dao.upsert(
            DiaryEntry(
                id = "local-1", title = "本地", body = "离线写的", diaryDate = 1,
                updatedAt = 500,
            )
        )

        engine.sync()

        assertEquals(1, fakeApi.pushedRequests.size)
        assertEquals(listOf("local-1"), fakeApi.pushedRequests[0].entries.map { it.id })
        assertEquals(0L, fakeApi.pushedRequests[0].sinceSeq)
        assertTrue(cursorStore.getPushWatermark() >= 500)
    }

    @Test
    fun secondSync_onlySendsChangesAfterWatermark() = runTest {
        dao.upsert(
            DiaryEntry(id = "a", body = "旧", diaryDate = 1, updatedAt = 100)
        )
        engine.sync()

        dao.upsert(
            DiaryEntry(id = "b", body = "新", diaryDate = 2, updatedAt = 9000)
        )
        engine.sync()

        assertEquals(2, fakeApi.pushedRequests.size)
        val second = fakeApi.pushedRequests[1]
        assertEquals(listOf("b"), second.entries.map { it.id })
        assertTrue(second.sinceSeq >= 1)
    }

    @Test
    fun lww_conflictResolvedByNewerTimestamp() = runTest {
        // Local has a newer version, server has an older one.
        dao.upsert(
            DiaryEntry(id = "x", title = "本地新版", body = "v2", diaryDate = 1, updatedAt = 900)
        )
        fakeApi.seedServer(
            listOf(
                WireEntry(id = "x", title = "服务器旧版", body = "v1", diaryDate = 1, updatedAt = 700)
            )
        )

        engine.sync()

        val local = dao.getById("x")
        assertEquals("本地新版", local!!.title)
    }

    @Test
    fun serverTombstone_appliedLocally() = runTest {
        dao.upsert(
            DiaryEntry(id = "x", body = "要被删", diaryDate = 1, updatedAt = 100)
        )
        fakeApi.seedServer(
            listOf(
                WireEntry(id = "x", body = "", diaryDate = 1, updatedAt = 300, deleted = true, deletedAt = 300)
            )
        )

        engine.sync()

        val local = dao.getById("x")
        assertTrue(local!!.deleted)
        val active = dao.observeActive().first()
        assertTrue(active.isEmpty())
    }

    @Test
    fun serverEntryWithOlderTimestamp_stillPulledAfterWatermark() = runTest {
        // Device clock runs ahead: local watermark is 9000, but the server
        // receives an entry whose timestamp is older (created on a slower
        // device). The seq watermark must still surface it.
        dao.upsert(
            DiaryEntry(id = "mine", body = "x", diaryDate = 1, updatedAt = 9000)
        )
        engine.sync()
        assertEquals(1L, cursorStore.getServerSeq())

        fakeApi.seedServer(
            listOf(
                WireEntry(id = "older-device-entry", body = "y", diaryDate = 2, updatedAt = 100)
            )
        )
        engine.sync()

        val local = dao.getById("older-device-entry")
        assertTrue("older-timestamped server entry must be pulled", local != null)
        assertEquals(2L, cursorStore.getServerSeq())
    }

    @Test
    fun failure_leavesWatermarksUnchangedAndReportsStatus() = runTest {
        fakeApi.failWith = SyncFailure.Network

        engine.sync()

        assertEquals(0L, cursorStore.getServerSeq())
        assertEquals(0L, cursorStore.getPushWatermark())
        assertTrue(engine.status.value is SyncStatus.Failed)
    }

    @Test
    fun failure_keepsLocalDataIntact() = runTest {
        dao.upsert(
            DiaryEntry(id = "keep", body = "数据", diaryDate = 1, updatedAt = 100)
        )
        fakeApi.failWith = SyncFailure.Network

        engine.sync()

        val local = dao.getById("keep")
        assertEquals("数据", local!!.body)
    }

    @Test
    fun retryAfterFailure_succeedsAndSyncs() = runTest {
        dao.upsert(
            DiaryEntry(id = "a", body = "x", diaryDate = 1, updatedAt = 100)
        )
        fakeApi.failWith = SyncFailure.Network
        engine.sync()
        fakeApi.failWith = null

        engine.sync()

        assertEquals(1, fakeApi.pushedRequests.size)
        assertTrue(engine.status.value is SyncStatus.Success)
    }

    @Test
    fun sync_uploadsPendingImages() = runTest {
        val marker = BodyImages.newMarker()
        val id = BodyImages.extractIds(marker).first()
        dao.upsert(
            DiaryEntry(id = "e1", body = "正文 $marker", diaryDate = 1, updatedAt = 100)
        )
        db.imageDao().upsert(ImageEntity(id = id, entryId = "e1", uploaded = false, updatedAt = 100))
        val full = ImageFileStore(context).fullFile(id)
        full.writeBytes(byteArrayOf(9, 9, 9))

        engine.sync()

        assertEquals(1, fakeImageApi.uploaded.size)
        assertEquals(id, fakeImageApi.uploaded[0].first)
        assertTrue(db.imageDao().getById(id)!!.uploaded)
    }

    @Test
    fun sync_imageUploadFailure_keepsPendingForRetry() = runTest {
        val marker = BodyImages.newMarker()
        val id = BodyImages.extractIds(marker).first()
        dao.upsert(
            DiaryEntry(id = "e1", body = "正文 $marker", diaryDate = 1, updatedAt = 100)
        )
        db.imageDao().upsert(ImageEntity(id = id, entryId = "e1", uploaded = false, updatedAt = 100))
        ImageFileStore(context).fullFile(id).writeBytes(byteArrayOf(1))
        fakeImageApi.failUpload = true

        engine.sync()
        assertTrue(fakeImageApi.uploaded.isEmpty())

        fakeImageApi.failUpload = false
        engine.sync()
        assertEquals(1, fakeImageApi.uploaded.size)
        assertTrue(db.imageDao().getById(id)!!.uploaded)
    }

    @Test
    fun sync_downloadsReferencedImagesFromServer() = runTest {
        val marker = BodyImages.newMarker()
        val id = BodyImages.extractIds(marker).first()
        fakeApi.seedServer(
            listOf(
                WireEntry(id = "e1", body = "正文 $marker", diaryDate = 1, updatedAt = 100)
            )
        )

        engine.sync()

        val img = db.imageDao().getById(id)
        assertTrue(img != null && img.uploaded)
        assertTrue(ImageFileStore(context).fullFile(id).exists())
        assertTrue(ImageFileStore(context).thumbFile(id).exists())
    }

    @Test
    fun deleteEntry_removesLocalImagesAndCallsRemote() = runTest {
        val marker = BodyImages.newMarker()
        val id = BodyImages.extractIds(marker).first()
        dao.upsert(
            DiaryEntry(id = "e1", body = "正文 $marker", diaryDate = 1, updatedAt = 100)
        )
        db.imageDao().upsert(ImageEntity(id = id, entryId = "e1", uploaded = true, updatedAt = 100))
        ImageFileStore(context).fullFile(id).writeBytes(byteArrayOf(1))
        ImageFileStore(context).thumbFile(id).writeBytes(byteArrayOf(1))

        engine.deleteEntry("e1")

        assertTrue(dao.getById("e1")!!.deleted)
        assertTrue(db.imageDao().getById(id) == null)
        assertTrue(!ImageFileStore(context).fullFile(id).exists())
        assertEquals(listOf(id), fakeImageApi.deleted)
    }
}
