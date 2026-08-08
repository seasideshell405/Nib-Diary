package com.diary.app.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.diary.app.DiaryApplication
import com.diary.app.data.ImageDao
import com.diary.app.data.ImageFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** One media-library card: image metadata plus its entry's title/date. */
data class MediaItem(
    val id: String,
    val entryId: String,
    val diaryDate: Long,
    val title: String?,
    val sizeBytes: Long,
)

class MediaLibraryViewModel(
    imageDao: ImageDao,
    private val fileStore: ImageFileStore,
) : ViewModel() {

    val items: StateFlow<List<MediaItem>> = imageDao.observeAllWithEntry()
        .map { rows ->
            withContext(Dispatchers.IO) {
                rows.map { row ->
                    val size = fileStore.existingFull(row.id)?.length() ?: 0L
                    MediaItem(
                        id = row.id,
                        entryId = row.entryId,
                        diaryDate = row.diaryDate,
                        title = row.title,
                        sizeBytes = size,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DiaryApplication
                MediaLibraryViewModel(
                    imageDao = app.container.database.imageDao(),
                    fileStore = app.container.imageFileStore,
                )
            }
        }
    }
}
