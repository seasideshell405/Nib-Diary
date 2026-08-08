package com.diary.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diary.app.DiaryApplication
import com.diary.app.data.DiaryEntry
import com.diary.app.data.EntryDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarViewModel(private val dao: EntryDao) : ViewModel() {

    // Marked dates are pre-warmed at startup: after a cold start the
    // calendar shows dots immediately instead of flashing empty.
    private val _dates = MutableStateFlow<List<Long>>(emptyList())
    val dates: StateFlow<List<Long>> = _dates.asStateFlow()

    // View state kept in memory so tab switches never re-run or lose it.
    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    init {
        viewModelScope.launch { _dates.value = dao.getActiveDiaryDates() }
        viewModelScope.launch { dao.observeActiveDiaryDates().collect { _dates.value = it } }
    }

    fun onMonthChange(value: YearMonth) {
        _month.value = value
    }

    fun onDateSelect(value: LocalDate) {
        _selectedDate.value = value
    }

    /** Re-reads marked dates immediately (called after delete/edit). */
    fun refresh() {
        viewModelScope.launch { _dates.value = dao.getActiveDiaryDates() }
    }

    /**
     * The single entry for the selected day, kept alive in the ViewModel:
     * returning to the tab reads the cached value instead of flashing the
     * empty state while the DB query resolves.
     */
    val selectedEntry: StateFlow<DiaryEntry?> =
        _selectedDate
            .map { date ->
                val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                start to end
            }
            .flatMapLatest { (start, end) -> dao.observeFirstByDiaryDate(start, end) }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(Long.MAX_VALUE), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiaryApplication
                CalendarViewModel(app.container.database.entryDao())
            }
        }
    }
}
