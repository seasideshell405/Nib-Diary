package com.diary.app.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diary.app.DiaryApplication
import com.diary.app.data.BodyImages
import com.diary.app.data.DiaryDates
import com.diary.app.data.EntryRepository
import com.diary.app.data.ImageDao
import com.diary.app.data.ImageEntity
import com.diary.app.data.ImageApiContract
import com.diary.app.data.ImageFileStore
import com.diary.app.data.ImageImporter
import com.diary.app.data.SyncEngine
import com.diary.app.repository
import com.diary.app.syncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EditorViewModel(
    private val repository: EntryRepository,
    private val engine: SyncEngine,
    private val imageDao: ImageDao,
    private val fileStore: ImageFileStore,
    private val importer: ImageImporter,
    private val imageApi: ImageApiContract,
    private val entryId: String?,
    initialDate: LocalDate?,
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    private val _mood = MutableStateFlow<Mood?>(null)
    val mood: StateFlow<Mood?> = _mood.asStateFlow()

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private val _diaryDate = MutableStateFlow(initialDate ?: DiaryDates.defaultDiaryDate())
    val diaryDate: StateFlow<LocalDate> = _diaryDate.asStateFlow()

    /** Set when the picked date collides with another entry (one per day). */
    private val _dateError = MutableStateFlow<String?>(null)
    val dateError: StateFlow<String?> = _dateError.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** True once anything changed since load; gates the exit warning. */
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    init {
        if (entryId != null) {
            viewModelScope.launch {
                val entry = repository.getEntry(entryId) ?: return@launch
                _title.value = entry.title.orEmpty()
                _body.value = entry.body
                _mood.value = Mood.fromKey(entry.mood)
                _weather.value = Weather.fromKey(entry.weather)
                _diaryDate.value = Instant.ofEpochMilli(entry.diaryDate)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }
    }

    fun onTitleChange(value: String) {
        _title.value = value
        _dirty.value = true
    }

    fun onBodyChange(value: String) {
        val prev = _body.value
        if (value == prev) return
        _body.value = value
        // The editor re-renders (normalizing image line breaks) right after
        // opening; that programmatic change is NOT a user edit and must not
        // arm the unsaved-changes warning.
        if (value != normalizeEditorText(prev)) {
            _dirty.value = true
        }
        val removed = BodyImages.extractIds(prev).toSet() - BodyImages.extractIds(value).toSet()
        if (removed.isNotEmpty()) {
            // Image removed inline in the editor: drop its files and record,
            // and free the server copy too if it was uploaded (a failure
            // just leaves an orphan that a later delete can retry).
            removed.forEach { fileStore.deleteAll(it) }
            viewModelScope.launch {
                for (id in removed) {
                    val uploaded = imageDao.getById(id)?.uploaded == true
                    imageDao.delete(id)
                    if (uploaded) imageApi.deleteRemote(id)
                }
            }
        }
    }

    fun onMoodChange(value: Mood?) {
        _mood.value = value
        _dirty.value = true
    }

    fun onWeatherChange(value: Weather?) {
        _weather.value = value
        _dirty.value = true
    }

    fun onDateChange(value: LocalDate) {
        _dateError.value = null
        _diaryDate.value = value
        _dirty.value = true
    }

    /** True when some OTHER entry already occupies the given diary date. */
    private suspend fun dateTaken(date: LocalDate, excludeId: String?): Boolean {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return repository.getByDiaryDate(start, end)?.let { it.id != excludeId } ?: false
    }

    /** Inserts the current date-time text at the end of the body. */
    fun clearDateError() {
        _dateError.value = null
    }

    fun insertTimestamp() {
        _body.value = timestampMarkerText().let { marker ->
            if (_body.value.isBlank()) marker else "${_body.value}\n$marker"
        }
        _dirty.value = true
    }

    /** Inserts a subheading block at the end of the body. */
    fun insertSubheading(text: String) {
        val marker = subheadingMarkerText(text) ?: return
        _body.value = if (_body.value.isBlank()) marker else "${_body.value}\n$marker"
        _dirty.value = true
    }

    /**
     * Imports a picked image and inserts its marker at [insertAt] (or
     * appends at the end when null), each image on its own line.
     */
    fun importImage(source: File, insertAt: Int? = null) {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            try {
                val id = importer.import(source)
                val marker = "![img]($id)"
                val body = _body.value
                _body.value = when {
                    body.isBlank() -> marker
                    insertAt == null || insertAt < 0 || insertAt > body.length ->
                        body.trimEnd('\n') + "\n" + marker
                    else -> {
                        val before = body.substring(0, insertAt).trimEnd('\n')
                        val after = body.substring(insertAt).trimStart('\n')
                        buildString {
                            append(before)
                            if (before.isNotEmpty()) append("\n")
                            append(marker)
                            if (after.isNotEmpty()) append("\n")
                            append(after)
                        }
                    }
                }
                _dirty.value = true
                imageDao.upsert(
                    ImageEntity(id = id, entryId = entryId.orEmpty(), uploaded = false, updatedAt = System.currentTimeMillis())
                )
            } finally {
                _importing.value = false
            }
        }
    }

    fun save() {
        val body = _body.value
        if (body.isBlank()) return
        viewModelScope.launch {
            if (dateTaken(_diaryDate.value, excludeId = entryId)) {
                _dateError.value = "这一天已经有日记了"
                return@launch
            }
            val date = _diaryDate.value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            val existing = entryId?.let { repository.getEntry(it) }
            val actualId: String
            if (existing != null) {
                actualId = existing.id
                repository.update(
                    existing.copy(
                        title = _title.value,
                        body = body,
                        mood = _mood.value?.key,
                        weather = _weather.value?.key,
                        diaryDate = date,
                    ),
                    now,
                )
            } else {
                actualId = repository.create(
                    title = _title.value,
                    body = body,
                    diaryDate = date,
                    now = now,
                    mood = _mood.value?.key,
                    weather = _weather.value?.key,
                ).id
            }
            // Images imported before this entry existed carry entryId="".
            // Fix them now so media-library JOINs and per-entry deletes work.
            val ids = BodyImages.extractIds(body)
            if (ids.isNotEmpty()) imageDao.assignEntryId(actualId, ids)
            _saved.value = true
            _dirty.value = false
            engine.sync()
        }
    }

    companion object {
        fun factory(entryId: String?, initialDate: LocalDate? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiaryApplication
                EditorViewModel(
                    repository = app.repository,
                    engine = app.syncEngine,
                    imageDao = app.container.database.imageDao(),
                    fileStore = app.container.imageFileStore,
                    importer = app.container.imageImporter,
                    imageApi = app.container.imageApi,
                    entryId = entryId,
                    initialDate = initialDate,
                )
            }
        }
    }
}

/** Marker text for the current time, e.g. "![time](14:30:00)". */
internal fun timestampMarkerText(now: LocalDateTime = LocalDateTime.now()): String =
    "![time](${now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))})"

/** Marker text for a subheading, or null when [text] is blank. */
internal fun subheadingMarkerText(text: String): String? =
    text.trim().takeIf { it.isNotEmpty() }?.let { "![sub]($it)" }
