package com.example.phototrail.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.core.content.ContextCompat

class CameraCapabilityProvider(private val context: Context) {
    fun loadBackCamera(
        onResult: (CameraCapabilities) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                val cameraInfo = selector.filter(provider.availableCameraInfos).firstOrNull()
                    ?: error("Back camera is not available")
                val videoCapabilities = Recorder.getVideoCapabilities(cameraInfo)
                val qualities = videoCapabilities.getSupportedQualities(DynamicRange.SDR)
                val supportsUhd = Quality.UHD in qualities
                val supports60 = cameraInfo.supportedFrameRateRanges.any {
                    it.lower <= 60 && it.upper >= 60
                }
                CameraCapabilities(
                    buildSet {
                        if (Quality.FHD in qualities) add(VideoQualityPreset.FHD_30)
                        if (supportsUhd) add(VideoQualityPreset.UHD_30)
                        if (supportsUhd && supports60) add(VideoQualityPreset.UHD_60)
                        if (isEmpty()) add(VideoQualityPreset.FHD_30)
                    }
                )
            }.onSuccess(onResult).onFailure(onError)
        }, ContextCompat.getMainExecutor(context))
    }
}
