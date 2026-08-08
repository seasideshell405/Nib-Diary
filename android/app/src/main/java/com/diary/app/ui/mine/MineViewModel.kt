package com.diary.app.ui.mine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diary.app.DiaryApplication
import com.diary.app.data.BodyImages
import com.diary.app.data.DiaryEntry
import com.diary.app.data.EntryDao
import com.diary.app.data.ImageDao
import com.diary.app.data.ImageWithEntry
import com.diary.app.data.ProfileRepository
import com.diary.app.data.WireProfile
import com.diary.app.ui.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant
import java.time.ZoneId

data class Stats(
    val totalEntries: Int = 0,
    val totalDays: Int = 0,
    val currentStreak: Int = 0,
    val totalWords: Int = 0,
    val totalImages: Int = 0,
    val moodCounts: Map<String, Int> = emptyMap(),
)

class MineViewModel(
    dao: EntryDao,
    imageDao: ImageDao,
    private val profileRepository: ProfileRepository,
    private val appContext: Context,
) : ViewModel() {

    // Stats are pre-warmed at startup (activity-scope ViewModel): the tab
    // shows the real numbers immediately after a cold start instead of a
    // 0-flash while the DB query resolves.
    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    // The few most recent images (diary-date order) for the media library
    // entry card on this tab.
    private val _recentImages = MutableStateFlow<List<ImageWithEntry>>(emptyList())
    val recentImages: StateFlow<List<ImageWithEntry>> = _recentImages.asStateFlow()

    private val _profile = MutableStateFlow(profileRepository.load())
    val profile: StateFlow<WireProfile> = _profile.asStateFlow()

    // Avatar bitmap lives here (activity scope) and is cached to disk:
    // cold starts read the local file instead of re-downloading.
    private val httpClient = OkHttpClient()
    private var loadedAvatarUrl: String? = null
    private val _avatarBitmap = MutableStateFlow<Bitmap?>(null)
    val avatarBitmap: StateFlow<Bitmap?> = _avatarBitmap.asStateFlow()

    init {
        viewModelScope.launch {
            _stats.value = computeStats(dao.observeActive().first())
        }
        viewModelScope.launch {
            dao.observeActive().collect { _stats.value = computeStats(it) }
        }
        viewModelScope.launch {
            imageDao.observeAllWithEntry().collect { list ->
                _recentImages.value = list.take(PREVIEW_COUNT)
            }
        }
        ensureAvatar()
        // Pull the server profile (also covers fresh-device restore).
        viewModelScope.launch {
            profileRepository.pullFromServer()
            _profile.value = profileRepository.load()
            ensureAvatar()
        }
    }

    private fun computeStats(entries: List<DiaryEntry>): Stats {
        val dates = entries.map {
            Instant.ofEpochMilli(it.diaryDate).atZone(ZoneId.systemDefault()).toLocalDate()
        }.distinct().sorted()

        val currentStreak = if (dates.isEmpty()) 0 else {
            val today = java.time.LocalDate.now()
            var streak = 0
            var day = today
            if (dates.contains(today)) {
                while (dates.contains(day)) {
                    streak++
                    day = day.minusDays(1)
                }
            } else {
                day = today.minusDays(1)
                while (dates.contains(day)) {
                    streak++
                    day = day.minusDays(1)
                }
            }
            streak
        }

        var words = 0
        var images = 0
        for (entry in entries) {
            val body = BodyImages.SUB_MARKER_PATTERN.replace(entry.body) { it.groupValues[1] }
                .replace(BodyImages.TIME_MARKER_PATTERN) { it.groupValues[1] }
            words += BodyImages.MARKER_PATTERN.replace(body, "").length
            images += BodyImages.extractIds(entry.body).size
        }

        return Stats(
            totalEntries = entries.size,
            totalDays = dates.size,
            currentStreak = currentStreak,
            totalWords = words,
            totalImages = images,
            moodCounts = entries.filter { it.mood != null }.groupingBy { it.mood!! }.eachCount(),
        )
    }

    private fun avatarCacheFile(url: String): File =
        File(appContext.filesDir, "avatar_${url.hashCode()}.jpg")

    /**
     * Shows the avatar: disk cache first, download only when the URL is
     * new. The loaded URL is remembered so tab switches never re-run it.
     */
    private fun ensureAvatar() {
        val url = _profile.value.avatarUrl
        if (url.isBlank()) {
            loadedAvatarUrl = null
            _avatarBitmap.value = null
            return
        }
        if (url == loadedAvatarUrl) return
        loadedAvatarUrl = url
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                val cached = avatarCacheFile(url)
                if (cached.exists()) {
                    // Fast local path after a cold start.
                    ImageDecoder.decodeSampled(cached, 512)
                } else {
                    try {
                        val bytes = httpClient.newCall(
                            Request.Builder().url(url).build()
                        ).execute().use { it.body?.bytes() }
                        bytes?.let {
                            cached.writeBytes(it)
                            BitmapFactory.decodeByteArray(it, 0, it.size)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            _avatarBitmap.value = bitmap
        }
    }

    fun saveProfile(nickname: String, signature: String, avatarUrl: String) {
        viewModelScope.launch {
            profileRepository.save(
                WireProfile(
                    nickname = nickname,
                    signature = signature,
                    avatarUrl = avatarUrl,
                    updatedAt = _profile.value.updatedAt,
                )
            )
            _profile.value = profileRepository.load()
            ensureAvatar()
        }
    }

    companion object {
        private const val PREVIEW_COUNT = 4

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiaryApplication
                MineViewModel(
                    dao = app.container.database.entryDao(),
                    imageDao = app.container.database.imageDao(),
                    profileRepository = app.container.profileRepository,
                    appContext = app,
                )
            }
        }
    }
}
