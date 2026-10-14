package com.example.phototrail.ui

import com.example.phototrail.camera.CameraSettings
import com.example.phototrail.camera.CaptureOrientation
import com.example.phototrail.camera.VideoQualityPreset
import com.example.phototrail.data.MakingProjectEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MakingProjectSettingsTest {
    @Test
    fun defaultProjectUsesFhd30Portrait() {
        val settings = MakingProjectEntity(tripKey = "trip").toCameraSettings()

        assertEquals(VideoQualityPreset.FHD_30, settings.videoQuality)
        assertEquals(CaptureOrientation.PORTRAIT, settings.orientation)
    }

    @Test
    fun cameraSettingsRoundTripIntoProject() {
        val project = MakingProjectEntity(tripKey = "trip")
        val settings = CameraSettings(
            videoQuality = VideoQualityPreset.UHD_30,
            orientation = CaptureOrientation.LANDSCAPE,
            audioEnabled = false
        )

        val updated = project.withCameraSettings(settings)

        assertEquals(settings, updated.toCameraSettings())
    }
}
