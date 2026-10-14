package com.example.phototrail.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phototrail.camera.CameraSettings
import com.example.phototrail.camera.CaptureOrientation
import com.example.phototrail.camera.CapturedMedia
import com.example.phototrail.camera.VideoQualityPreset
import com.example.phototrail.data.MakingProjectEntity
import com.example.phototrail.data.MakingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MakingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MakingRepository(application)

    val projects = repository.allProjects.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val links = repository.allLinks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _selectedProject = MutableStateFlow<MakingProjectEntity?>(null)
    val selectedProject: StateFlow<MakingProjectEntity?> = _selectedProject

    fun selectTrip(tripKey: String) {
        if (_selectedProject.value?.tripKey == tripKey) return
        viewModelScope.launch(Dispatchers.IO) {
            _selectedProject.value = repository.getOrCreateProject(tripKey)
        }
    }

    fun updateProject(project: MakingProjectEntity) {
        _selectedProject.value = project
    }

    fun saveProject(project: MakingProjectEntity, onSaved: (() -> Unit)? = null) {
        _selectedProject.value = project
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveProject(project)
            onSaved?.invoke()
        }
    }

    fun registerCapturedVideo(tripKey: String, captured: CapturedMedia) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.registerCapturedVideo(tripKey, captured)
        }
    }
}

fun MakingProjectEntity.toCameraSettings(): CameraSettings = CameraSettings(
    videoQuality = runCatching { VideoQualityPreset.valueOf(videoQuality) }
        .getOrDefault(VideoQualityPreset.FHD_30),
    orientation = runCatching { CaptureOrientation.valueOf(orientation) }
        .getOrDefault(CaptureOrientation.PORTRAIT),
    audioEnabled = audioEnabled
)

fun MakingProjectEntity.withCameraSettings(settings: CameraSettings): MakingProjectEntity = copy(
    videoQuality = settings.videoQuality.name,
    orientation = settings.orientation.name,
    audioEnabled = settings.audioEnabled
)
