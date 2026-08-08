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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiaryListViewModel(private val repository: EntryRepository) : ViewModel() {

    // Manual StateFlow instead of stateIn: delete/edit operations refresh
    // the list explicitly via [refresh], so the UI never shows a stale
    // entry even if the Room flow notification is delayed.
    private val _entries = MutableStateFlow<List<DiaryEntry>>(emptyList())
    val entries: StateFlow<List<DiaryEntry>> = _entries.asStateFlow()

    init {
        viewModelScope.launch {
            _entries.value = repository.getActive()
            repository.observeEntries().collect { _entries.value = it }
        }
    }

    /** Re-reads the active list immediately (called after delete/edit). */
    fun refresh() {
        viewModelScope.launch { _entries.value = repository.getActive() }
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
