package com.diary.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diary.app.data.BodyImages
import com.diary.app.data.ContentBlock
import com.diary.app.data.DiaryEntry
import com.diary.app.data.EntryDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(dao: EntryDao) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<DiaryEntry>> =
        _query
            .flatMapLatest { q ->
                if (q.isBlank()) {
                    flowOf(emptyList())
                } else {
                    // SQL pre-filter (title/body LIKE), then a Kotlin
                    // re-filter on the DISPLAYED text: only entries whose
                    // visible text actually contains the query survive, so
                    // the snippet and highlight always find the match. The
                    // untitled entry's default title (its date) matches too.
                    combine(dao.search(q), dao.observeActive()) { hits, all ->
                        refineSearchResults(hits, all, q)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Long.MAX_VALUE), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    companion object {
        fun factory(dao: EntryDao): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(dao)
            }
        }
    }
}

/**
 * Keeps SQL hits whose displayed text (title + marker-stripped body) really
 * contains [query] — marker/uuid hits that would show no highlight are
 * dropped — and appends entries matched by their default title (the diary
 * date). Untitled entries are searchable by date this way.
 */
internal fun refineSearchResults(
    sqlHits: List<DiaryEntry>,
    allEntries: List<DiaryEntry>,
    query: String,
): List<DiaryEntry> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()

    return (sqlHits + allEntries)
        .distinctBy { it.id }
        .filter { searchVisibleText(it).contains(q, ignoreCase = true) }
        .sortedByDescending { it.diaryDate }
}

/** The text a search actually matches: title (or default date title) + paragraphs/subheadings. */
internal fun searchVisibleText(entry: DiaryEntry): String {
    val title = entry.title?.takeIf { it.isNotBlank() }
        ?: dateTextOf(entry.diaryDate)
    return title + "\n" + bodySearchText(entry.body)
}

/**
 * Searchable body text: paragraph and subheading text only. Timestamps,
 * images, and markers never match — a query like "14:30" must not hit a
 * timestamp block, and marker uuids must not surface as false hits.
 * Inline timestamp markers (embedded in a paragraph line) are stripped too,
 * so their time text stays out of search.
 */
internal fun bodySearchText(body: String): String =
    BodyImages.parseBlocks(body).mapNotNull { block ->
        when (block) {
            is ContentBlock.Paragraph -> block.text
                .replace(BodyImages.TIME_MARKER_PATTERN, "")
                .trim()
                .takeIf { it.isNotEmpty() }
            is ContentBlock.Heading -> block.text
            else -> null
        }
    }.joinToString("\n")

internal fun dateTextOf(diaryDateMillis: Long): String =
    Instant.ofEpochMilli(diaryDateMillis).atZone(ZoneId.systemDefault())
        .toLocalDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
