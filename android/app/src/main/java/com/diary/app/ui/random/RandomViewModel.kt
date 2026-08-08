package com.diary.app.ui.random

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diary.app.data.DiaryEntry
import com.diary.app.data.EntryDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RandomViewModel(private val dao: EntryDao) : ViewModel() {

    private val _entry = MutableStateFlow<DiaryEntry?>(null)
    val entry: StateFlow<DiaryEntry?> = _entry.asStateFlow()

    init {
        roll()
    }

    /** Picks another random diary entry. */
    fun roll() {
        viewModelScope.launch {
            _entry.value = dao.getRandomActive()
        }
    }

    companion object {
        fun factory(dao: EntryDao): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RandomViewModel(dao)
            }
        }
    }
}
