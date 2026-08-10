package com.diary.app.ui.diary

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diary.app.DiaryApplication
import com.diary.app.data.DiaryEntry
import com.diary.app.data.EntryRepository
import com.diary.app.repository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Browse list. Single source of truth: the Room flow, restarted by
 * [refresh] so a delete/edit takes effect immediately. A delete also
 * calls [hide]: the card vanishes from the UI right away, even while the
 * flow emission is still in flight. The DB row is tombstoned by the
 * delete itself, so every later query agrees and nothing "resurrects".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiaryListViewModel(private val repository: EntryRepository) : ViewModel() {

    private val refreshTick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val entries: StateFlow<List<DiaryEntry>> =
        refreshTick
            .onStart { emit(Unit) }
            .flatMapLatest { repository.observeEntries() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Ids hidden from the UI right after a delete. */
    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds.asStateFlow()

    /** Immediately hides a deleted card; the flow catches up on its own. */
    fun hide(id: String) {
        _hiddenIds.value = _hiddenIds.value + id
    }

    /** Re-reads the list right away (called after delete/edit). */
    fun refresh() {
        viewModelScope.launch { refreshTick.emit(Unit) }
    }

    /**
     * Scroll position kept in memory (not navigation state): survives tab
     * switches without the save/restore serialization cost.
     */
    val listState = LazyListState()

    /** Returns the entry for a given diary date (one diary per day). */
    suspend fun entryForDate(dayStart: Long, dayEnd: Long): DiaryEntry? =
        repository.getByDiaryDate(dayStart, dayEnd)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiaryApplication
                DiaryListViewModel(app.repository)
            }
        }
    }
}
