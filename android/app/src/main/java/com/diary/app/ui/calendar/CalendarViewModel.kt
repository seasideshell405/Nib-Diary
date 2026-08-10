package com.diary.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diary.app.DiaryApplication
import com.diary.app.data.DiaryEntry
import com.diary.app.data.EntryDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Calendar page. Both [dates] (marked dots) and [selectedEntry] (the day
 * card) are single-source flows restarted by [refresh], so a delete or
 * edit performed on another tab takes effect immediately without waiting
 * for Room's async invalidation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val dao: EntryDao) : ViewModel() {

    private val refreshTick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Marked dates (diary dots), pre-warmed from creation. */
    val dates: StateFlow<List<Long>> =
        refreshTick
            .onStart { emit(Unit) }
            .flatMapLatest { dao.observeActiveDiaryDates() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // View state kept in memory so tab switches never re-run or lose it.
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun onMonthChange(value: YearMonth) {
        _month.value = value
    }

    fun onDateSelect(value: LocalDate) {
        _selectedDate.value = value
    }

    /** Re-reads marked dates and the day card right away (after delete/edit). */
    fun refresh() {
        viewModelScope.launch { refreshTick.emit(Unit) }
    }

    /** Ids hidden from the UI right after a delete (mirrors the browse list). */
    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds.asStateFlow()

    /** Diary dates of just-deleted entries: their dots vanish right away. */
    private val _hiddenDates = MutableStateFlow<Set<Long>>(emptySet())
    val hiddenDates: StateFlow<Set<Long>> = _hiddenDates.asStateFlow()

    /**
     * Immediately hides a deleted day card (and its dot); the flow catches
     * up on its own. The DB row is already tombstoned by the delete, so
     * every later query agrees and nothing "resurrects".
     */
    fun hide(id: String) {
        viewModelScope.launch {
            _hiddenIds.value = _hiddenIds.value + id
            dao.getById(id)?.let { _hiddenDates.value = _hiddenDates.value + it.diaryDate }
        }
    }

    /** The single entry for the selected day, kept alive in the ViewModel. */
    val selectedEntry: StateFlow<DiaryEntry?> =
        refreshTick
            .onStart { emit(Unit) }
            .flatMapLatest {
                _selectedDate
                    .map { date ->
                        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        start to end
                    }
                    .flatMapLatest { (start, end) -> dao.observeFirstByDiaryDate(start, end) }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiaryApplication
                CalendarViewModel(app.container.database.entryDao())
            }
        }
    }
}
