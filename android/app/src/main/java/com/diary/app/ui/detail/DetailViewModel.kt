package com.diary.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diary.app.DiaryApplication
import com.diary.app.data.DiaryEntry
import com.diary.app.data.EntryDao
import com.diary.app.data.SyncEngine
import com.diary.app.syncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(
    private val dao: EntryDao,
    private val engine: SyncEngine,
    private val entryId: String,
) : ViewModel() {

    // Observed, not one-shot: the sheet must reflect edits made elsewhere
    // (the ViewModel is reused across reopenings by entryId key).
    val entry: StateFlow<DiaryEntry?> =
        dao.observeById(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(Long.MAX_VALUE), null)

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    fun delete() {
        viewModelScope.launch {
            engine.deleteEntry(entryId)
            _deleted.value = true
        }
    }

    fun setExporting(value: Boolean) {
        _exporting.value = value
    }

    companion object {
        fun factory(entryId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiaryApplication
                DetailViewModel(app.container.database.entryDao(), app.syncEngine, entryId)
            }
        }
    }
}
