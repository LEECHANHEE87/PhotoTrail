package com.example.phototrail.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.location.Location
import android.os.Build
import android.provider.MediaStore
import android.util.Range
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class PhotoTrailCameraController(private val context: Context) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val metadataExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private var activeRecording: Recording? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        mode: CameraMode,
        settings: CameraSettings,
        lensFacing: Int,
        onCapabilities: (CameraCapabilities) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        ProcessCameraProvider.getInstance(context).addListener({
            runCatching {
                val provider = ProcessCameraProvider.getInstance(context).get()
                val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
                val cameraInfo = selector.filter(provider.availableCameraInfos).firstOrNull()
                    ?: error("Selected camera is not available")
                val capabilities = capabilitiesFor(cameraInfo)
                onCapabilities(capabilities)

                val preview = Preview.Builder()
                    .setTargetRotation(previewView.display.rotation)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                provider.unbindAll()
                imageCapture = null
                videoCapture = null

                camera = when (mode) {
                    CameraMode.PHOTO -> {
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .setTargetRotation(previewView.display.rotation)
                            .build()
                        imageCapture = capture
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    }

                    CameraMode.VIDEO -> {
                        val selectedPreset = settings.videoQuality.takeIf(capabilities::supports)
                            ?: VideoQualityPreset.FHD_30
                        val qualitySelector = QualitySelector.fromOrderedList(
                            listOf(selectedPreset.cameraXQuality(), Quality.FHD).distinct(),
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
                        )
                        val recorder = Recorder.Builder()
                            .setQualitySelector(qualitySelector)
                            .build()
                        val capture = VideoCapture.Builder(recorder)
                            .setTargetFrameRate(
                                Range(selectedPreset.framesPerSecond, selectedPreset.framesPerSecond)
                            )
                            .setTargetRotation(previewView.display.rotation)
                            .build()
                        videoCapture = capture
                        provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    }
                }
            }.onFailure(onError)
        }, mainExecutor)
    }

    fun takePhoto(
        location: Location?,
        onSaved: (CapturedMedia) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = imageCapture ?: return onError(IllegalStateException("Photo capture is not ready"))
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("PT", "jpg"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/PhotoTrail")
            }
        }
        val metadata = ImageCapture.Metadata().apply { this.location = location }
        val output = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).setMetadata(metadata).build()

        capture.takePicture(output, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val uri = outputFileResults.savedUri
                    ?: return onError(IllegalStateException("MediaStore did not return a photo URI"))
                onSaved(
                    CapturedMedia(
                        uri = uri,
                        type = CaptureType.PHOTO,
                        latitude = location?.latitude,
                        longitude = location?.longitude
                    )
                )
            }

            override fun onError(exception: ImageCaptureException) = onError(exception)
        })
    }

    fun startVideo(
        audioEnabled: Boolean,
        location: Location?,
        onStarted: () -> Unit,
        onProgress: (Long) -> Unit,
        onSaved: (CapturedMedia) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = videoCapture ?: return onError(IllegalStateException("Video capture is not ready"))
        if (activeRecording != null) return

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("PTV", "mp4"))
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/PhotoTrail")
            }
        }
        val output = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = capture.output.prepareRecording(context, output)
        if (audioEnabled) pending = pending.withAudioEnabled()
        activeRecording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onStarted()
                is VideoRecordEvent.Status -> {
                    onProgress(event.recordingStats.recordedDurationNanos / 1_000_000L)
                }
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    if (event.hasError()) {
                        onError(IllegalStateException("Video finalize error ${event.error}"))
                    } else {
                        val uri = event.outputResults.outputUri
                        val encoderDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                        metadataExecutor.execute {
                            val actualDurationMs = readDurationMs(uri) ?: encoderDurationMs
                            mainExecutor.execute {
                                onSaved(
                                    CapturedMedia(
                                        uri = uri,
                                        type = CaptureType.VIDEO,
                                        durationMs = actualDurationMs,
                                        latitude = location?.latitude,
                                        longitude = location?.longitude
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopVideo() {
        activeRecording?.stop()
    }

    fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun release() {
        activeRecording?.stop()
        activeRecording = null
        runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
    }

    private fun capabilitiesFor(cameraInfo: androidx.camera.core.CameraInfo): CameraCapabilities {
        val videoCapabilities = Recorder.getVideoCapabilities(cameraInfo)
        val qualities = videoCapabilities.getSupportedQualities(DynamicRange.SDR)
        val supportsUhd = Quality.UHD in qualities
        val supports60 = cameraInfo.supportedFrameRateRanges.any { range ->
            range.lower <= 60 && range.upper >= 60
        }
        return CameraCapabilities(
            buildSet {
                if (Quality.FHD in qualities) add(VideoQualityPreset.FHD_30)
                if (supportsUhd) add(VideoQualityPreset.UHD_30)
                if (supportsUhd && supports60) add(VideoQualityPreset.UHD_60)
                if (isEmpty()) add(VideoQualityPreset.FHD_30)
            }
        )
    }

    private fun fileName(prefix: String, extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        return "${prefix}_${timestamp}.$extension"
    }

    private fun VideoQualityPreset.cameraXQuality(): Quality = when (this) {
        VideoQualityPreset.FHD_30 -> Quality.FHD
        VideoQualityPreset.UHD_30, VideoQualityPreset.UHD_60 -> Quality.UHD
    }

    private fun readDurationMs(uri: android.net.Uri): Long? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()
}
