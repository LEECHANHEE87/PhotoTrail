package com.example.phototrail.camera

import android.net.Uri

enum class CameraMode { PHOTO, VIDEO }

enum class CaptureType { PHOTO, VIDEO }

enum class CaptureOrientation { PORTRAIT, LANDSCAPE }

enum class VideoQualityPreset(
    val framesPerSecond: Int,
    val displayName: String
) {
    FHD_30(30, "FHD 30fps"),
    UHD_30(30, "4K 30fps"),
    UHD_60(60, "4K 60fps")
}

data class CameraSettings(
    val videoQuality: VideoQualityPreset = VideoQualityPreset.FHD_30,
    val orientation: CaptureOrientation = CaptureOrientation.PORTRAIT,
    val audioEnabled: Boolean = true
)

data class CameraCapabilities(
    val supportedVideoQualities: Set<VideoQualityPreset> = setOf(VideoQualityPreset.FHD_30)
) {
    fun supports(preset: VideoQualityPreset): Boolean = preset in supportedVideoQualities
}

data class CapturedMedia(
    val uri: Uri,
    val type: CaptureType,
    val durationMs: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
