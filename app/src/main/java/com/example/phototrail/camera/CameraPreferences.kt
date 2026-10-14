package com.example.phototrail.camera

import android.content.Context

class CameraPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): CameraSettings = CameraSettings(
        videoQuality = preferences.getString(KEY_VIDEO_QUALITY, null)
            ?.let { runCatching { VideoQualityPreset.valueOf(it) }.getOrNull() }
            ?: VideoQualityPreset.FHD_30,
        orientation = preferences.getString(KEY_ORIENTATION, null)
            ?.let { runCatching { CaptureOrientation.valueOf(it) }.getOrNull() }
            ?: CaptureOrientation.PORTRAIT,
        audioEnabled = preferences.getBoolean(KEY_AUDIO, true)
    )

    fun save(settings: CameraSettings) {
        preferences.edit()
            .putString(KEY_VIDEO_QUALITY, settings.videoQuality.name)
            .putString(KEY_ORIENTATION, settings.orientation.name)
            .putBoolean(KEY_AUDIO, settings.audioEnabled)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "camera_settings"
        const val KEY_VIDEO_QUALITY = "video_quality"
        const val KEY_ORIENTATION = "orientation"
        const val KEY_AUDIO = "audio_enabled"
    }
}
