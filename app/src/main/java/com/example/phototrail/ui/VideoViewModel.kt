package com.example.phototrail.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phototrail.camera.CapturedMedia
import com.example.phototrail.data.VideoIndexStats
import com.example.phototrail.data.VideoItemEntity
import com.example.phototrail.data.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoIndexUiState(
    val isIndexing: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val stats: VideoIndexStats? = null,
    val errorMessage: String? = null
)

class VideoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application)
    private val database = com.example.phototrail.data.PhotoDatabase.getInstance(application)

    val allVideos: StateFlow<List<VideoItemEntity>> = repository.allVideos.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val tripLinks = database.tripVideoLinkDao().getAllLinks().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _indexState = MutableStateFlow(VideoIndexUiState())
    val indexState: StateFlow<VideoIndexUiState> = _indexState

    private val _selectedVideo = MutableStateFlow<VideoItemEntity?>(null)
    val selectedVideo: StateFlow<VideoItemEntity?> = _selectedVideo

    fun indexVideos() {
        if (_indexState.value.isIndexing) return
        viewModelScope.launch {
            _indexState.value = VideoIndexUiState(isIndexing = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.indexVideos { processed, total ->
                        _indexState.value = _indexState.value.copy(
                            processed = processed,
                            total = total
                        )
                    }
                }
            }.onSuccess { stats ->
                _indexState.value = VideoIndexUiState(stats = stats)
            }.onFailure { error ->
                _indexState.value = VideoIndexUiState(errorMessage = error.message)
            }
        }
    }

    fun registerCapturedVideo(captured: CapturedMedia) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.registerCapturedVideo(captured)
        }
    }

    fun setFavorite(video: VideoItemEntity, favorite: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setFavorite(video.mediaStoreId, favorite)
        }
    }

    fun selectVideo(video: VideoItemEntity) {
        _selectedVideo.value = video
    }
}
