package com.example.phototrail.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phototrail.data.IndexStats
import com.example.phototrail.data.PhotoItemEntity
import com.example.phototrail.data.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IndexUiState(
    val isIndexing: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val stats: IndexStats? = null,
    val errorMessage: String? = null
)

class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository(application)

    val allPhotos: StateFlow<List<PhotoItemEntity>> = repository.allPhotos.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val photosWithLocation: StateFlow<List<PhotoItemEntity>> =
        repository.photosWithLocation.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    val photosWithoutLocation: StateFlow<List<PhotoItemEntity>> =
        repository.photosWithoutLocation.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val _indexUiState = MutableStateFlow(IndexUiState())
    val indexUiState: StateFlow<IndexUiState> = _indexUiState

    fun indexPhotos() {
        if (_indexUiState.value.isIndexing) return

        viewModelScope.launch {
            _indexUiState.value = IndexUiState(isIndexing = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.indexPhotos { processed, total ->
                        _indexUiState.value = _indexUiState.value.copy(
                            processed = processed,
                            total = total
                        )
                    }
                }
            }.onSuccess { stats ->
                _indexUiState.value = IndexUiState(stats = stats)
            }.onFailure { error ->
                _indexUiState.value = IndexUiState(
                    errorMessage = error.message ?: "Photo indexing failed"
                )
            }
        }
    }
}
