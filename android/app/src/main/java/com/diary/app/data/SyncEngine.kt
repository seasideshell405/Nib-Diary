package com.diary.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Failed(val reason: SyncFailure) : SyncStatus()
    data class Success(val pushed: Int, val pulled: Int) : SyncStatus()
}

class SyncEngine(
    private val api: DiaryApiContract,
    private val imageApi: ImageApiContract,
    private val dao: EntryDao,
    private val imageDao: ImageDao,
    private val fileStore: ImageFileStore,
    private val cursorStore: SyncStateStore,
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun sync() {
        // All network work must run off the main thread (OkHttp would throw
        // NetworkOnMainThreadException), and callers may invoke this from
        // viewModelScope (main dispatcher).
        withContext(Dispatchers.IO) {
            if (!mutex.tryLock()) return@withContext
            try {
                _status.value = SyncStatus.Syncing
                runSync()
                syncImages()
            } finally {
                mutex.unlock()
            }
        }
    }

    private suspend fun runSync() {
        val pushWatermark = cursorStore.getPushWatermark()
        val serverSeq = cursorStore.getServerSeq()

        // Push: everything locally changed since the last push watermark.
        val localChanges = dao.getChangesSince(pushWatermark).map { it.toWire() }

        val result = api.sync(entries = localChanges, sinceSeq = serverSeq)
        result.fold(
            onSuccess = { resp ->
                for (change in resp.changes) {
                    val local = change.toLocal()
                    val existing = dao.getById(local.id)
                    if (existing == null || change.updatedAt >= existing.updatedAt) {
                        dao.upsert(local)
                    }
                }
                // Pull watermark advances to the server's seq: pulls never miss.
                if (resp.serverSeq > serverSeq) {
                    cursorStore.setServerSeq(resp.serverSeq)
                }
                // Push watermark advances to the newest timestamp we know:
                // locally created entries and everything the server echoed back.
                val newest = resp.changes.maxOfOrNull { it.updatedAt } ?: 0L
                val newestLocal = dao.getMaxUpdatedAt()
                if (maxOf(newest, newestLocal) > pushWatermark) {
                    cursorStore.setPushWatermark(maxOf(newest, newestLocal))
                }
                _status.value = SyncStatus.Success(
                    pushed = localChanges.size,
                    pulled = resp.changes.size,
                )
            },
            onFailure = { failure ->
                _status.value = SyncStatus.Failed(failure as? SyncFailure ?: SyncFailure.Network)
            },
        )
    }

    /** Uploads locally created images and downloads referenced ones we lack. */
    private suspend fun syncImages() {
        for (img in imageDao.getPendingUploads()) {
            // existingFull: legacy .jpg files still upload fine.
            val file = fileStore.existingFull(img.id) ?: continue
            if (imageApi.upload(img.id, img.entryId, file)) {
                imageDao.markUploaded(img.id)
            }
        }

        val allEntries = dao.getChangesSince(-1)
        for (entry in allEntries) {
            if (entry.deleted) continue
            for (imgId in BodyImages.extractIds(entry.body)) {
                val local = imageDao.getById(imgId)
                if (local != null) continue
                val target = fileStore.fullFile(imgId)
                if (imageApi.download(imgId, target)) {
                    imageDao.upsert(ImageEntity(id = imgId, entryId = entry.id, uploaded = true, updatedAt = 0))
                    fileStore.thumbFile(imgId).also { thumb ->
                        if (!thumb.exists() && target.exists()) {
                            try {
                                ImageCompressor.thumbnail(target, thumb)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Deletes an entry and its images. The LOCAL delete (soft delete + DB
     * rows + files) completes immediately; the remote image delete runs in
     * the background so a slow network never delays the UI refresh.
     */
    suspend fun deleteEntry(entryId: String) {
        val entryImages = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val entry = dao.getById(entryId) ?: return@withContext emptyList()
            // The soft delete retries transient SQLite failures (write
            // lock contention while the sync engine writes in parallel):
            // a silently failed delete would leave the card in the list
            // until the next refresh, looking like a stale card.
            retrySoftDelete(entryId, now)
            val imgs = imageDao.getForEntry(entryId)
            for (img in imgs) {
                imageDao.delete(img.id)
                fileStore.deleteAll(img.id)
            }
            imgs
        }
        if (entryImages.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                entryImages.forEach { imageApi.deleteRemote(it.id) }
            }
        }
    }

    private suspend fun retrySoftDelete(entryId: String, now: Long) {
        repeat(5) { attempt ->
            try {
                dao.softDelete(entryId, now)
                return
            } catch (e: Exception) {
                if (attempt == 4) throw e
                kotlinx.coroutines.delay(60)
            }
        }
    }
}
