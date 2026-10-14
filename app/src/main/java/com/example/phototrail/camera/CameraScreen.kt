package com.example.phototrail.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.example.phototrail.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEFAULT_VIDEO_DURATION_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBackClick: () -> Unit,
    onMediaCaptured: (CapturedMedia) -> Unit,
    initialSettings: CameraSettings? = null,
    initialMode: CameraMode = CameraMode.PHOTO,
    modeSelectionEnabled: Boolean = true,
    settingsEnabled: Boolean = true,
    showFrameGuide: Boolean = false,
    videoDurationMs: Long = DEFAULT_VIDEO_DURATION_MS,
    projectTitle: String? = null,
    onSettingsChanged: (CameraSettings) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember { CameraPreferences(context) }
    val controller = remember { PhotoTrailCameraController(context.applicationContext) }
    val locationProvider = remember { CameraLocationProvider(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    var settings by remember(initialSettings) {
        mutableStateOf(initialSettings ?: preferences.load())
    }
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var capabilities by remember { mutableStateOf(CameraCapabilities()) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var hasCameraPermission by remember { mutableStateOf(context.hasPermission(Manifest.permission.CAMERA)) }
    var hasAudioPermission by remember { mutableStateOf(context.hasPermission(Manifest.permission.RECORD_AUDIO)) }
    var showSettings by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var captureBusy by remember { mutableStateOf(false) }
    var recordingProgress by remember { mutableFloatStateOf(0f) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var torchEnabled by remember { mutableStateOf(false) }
    var recentMedia by remember { mutableStateOf<CapturedMedia?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA]
            ?: context.hasPermission(Manifest.permission.CAMERA)
        hasAudioPermission = grants[Manifest.permission.RECORD_AUDIO]
            ?: context.hasPermission(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(requiredCameraPermissions())
        }
    }

    DisposableEffect(settings.orientation) {
        val activity = context.findActivity()
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = when (settings.orientation) {
            CaptureOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            CaptureOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            if (previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }

    LaunchedEffect(previewView, hasCameraPermission, mode, settings, lensFacing) {
        val view = previewView ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect
        torchEnabled = false
        controller.bind(
            lifecycleOwner = lifecycleOwner,
            previewView = view,
            mode = mode,
            settings = settings,
            lensFacing = lensFacing,
            onCapabilities = { available ->
                capabilities = available
                if (!available.supports(settings.videoQuality)) {
                    settings = settings.copy(videoQuality = VideoQualityPreset.FHD_30)
                    if (initialSettings == null) preferences.save(settings)
                    onSettingsChanged(settings)
                }
            },
            onError = { error ->
                captureBusy = false
                Toast.makeText(
                    context,
                    context.getString(R.string.camera_capture_failed, error.message.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    LaunchedEffect(isRecording, recordingStartedAt) {
        if (!isRecording) return@LaunchedEffect
        while (isRecording) {
            val elapsed = SystemClock.elapsedRealtime() - recordingStartedAt
            recordingProgress = (elapsed.toFloat() / videoDurationMs).coerceIn(0f, 1f)
            if (elapsed >= videoDurationMs) {
                controller.stopVideo()
                break
            }
            delay(40)
        }
    }

    DisposableEffect(Unit) {
        onDispose { controller.release() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (showFrameGuide) {
                CameraFrameGuide(
                    orientation = settings.orientation,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            CameraTopControls(
                torchEnabled = torchEnabled,
                controlsEnabled = !captureBusy && !isRecording,
                onBackClick = onBackClick,
                onTorchClick = {
                    torchEnabled = !torchEnabled
                    controller.setTorch(torchEnabled)
                },
                onSettingsClick = { showSettings = true },
                settingsEnabled = settingsEnabled,
                onSwitchCamera = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            CameraBottomControls(
                mode = mode,
                isRecording = isRecording,
                captureBusy = captureBusy,
                progress = recordingProgress,
                recentMedia = recentMedia,
                modeSelectionEnabled = modeSelectionEnabled,
                onModeChange = { mode = it },
                onCaptureClick = {
                    if (mode == CameraMode.PHOTO) {
                        captureBusy = true
                        coroutineScope.launch {
                            controller.takePhoto(
                                location = locationProvider.currentLocationOrNull(),
                                onSaved = { captured ->
                                    captureBusy = false
                                    recentMedia = captured
                                    onMediaCaptured(captured)
                                    Toast.makeText(context, R.string.camera_photo_saved, Toast.LENGTH_SHORT).show()
                                },
                                onError = { error ->
                                    captureBusy = false
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.camera_capture_failed, error.message.orEmpty()),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    } else if (isRecording) {
                        controller.stopVideo()
                    } else {
                        captureBusy = true
                        recordingProgress = 0f
                        coroutineScope.launch {
                            controller.startVideo(
                                audioEnabled = settings.audioEnabled && hasAudioPermission,
                                location = locationProvider.currentLocationOrNull(),
                                onStarted = {
                                    captureBusy = false
                                    isRecording = true
                                    recordingStartedAt = SystemClock.elapsedRealtime()
                                },
                                onProgress = { durationMs ->
                                    recordingProgress = (durationMs.toFloat() / videoDurationMs)
                                        .coerceIn(0f, 1f)
                                },
                                onSaved = { captured ->
                                    captureBusy = false
                                    isRecording = false
                                    recordingProgress = 0f
                                    recentMedia = captured
                                    onMediaCaptured(captured)
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.camera_video_saved,
                                            (captured.durationMs ?: 0L) / 1_000f
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onError = { error ->
                                    captureBusy = false
                                    isRecording = false
                                    recordingProgress = 0f
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.camera_capture_failed, error.message.orEmpty()),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if (isRecording) {
                Text(
                    text = stringResource(
                        R.string.camera_recording,
                        recordingProgress * videoDurationMs / 1_000f
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 92.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (!projectTitle.isNullOrBlank() && !isRecording) {
                Text(
                    text = projectTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 92.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.camera_permission_required), color = Color.White)
                Button(onClick = {
                    permissionLauncher.launch(requiredCameraPermissions())
                }) {
                    Text(stringResource(R.string.grant_camera_permission))
                }
            }
        }
    }

    if (showSettings) {
        CameraSettingsDialog(
            settings = settings,
            capabilities = capabilities,
            audioPermissionGranted = hasAudioPermission,
            onSettingsChange = { updated ->
                settings = updated
                if (initialSettings == null) preferences.save(updated)
                onSettingsChanged(updated)
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun CameraTopControls(
    torchEnabled: Boolean,
    controlsEnabled: Boolean,
    onBackClick: () -> Unit,
    onTorchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    settingsEnabled: Boolean,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraControlButton(onClick = onBackClick, enabled = controlsEnabled) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.camera_close))
        }
        Row {
            CameraControlButton(onClick = onTorchClick, enabled = controlsEnabled) {
                Icon(
                    if (torchEnabled) Icons.Default.Star else Icons.Default.Info,
                    stringResource(R.string.camera_toggle_light)
                )
            }
            CameraControlButton(onClick = onSwitchCamera, enabled = controlsEnabled) {
                Icon(Icons.Default.Refresh, stringResource(R.string.camera_switch_lens))
            }
            if (settingsEnabled) {
                CameraControlButton(onClick = onSettingsClick, enabled = controlsEnabled) {
                    Icon(Icons.Default.Settings, stringResource(R.string.camera_settings))
                }
            }
        }
    }
}

@Composable
private fun CameraControlButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.padding(4.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White
    ) {
        IconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

@Composable
private fun CameraBottomControls(
    mode: CameraMode,
    isRecording: Boolean,
    captureBusy: Boolean,
    progress: Float,
    recentMedia: CapturedMedia?,
    modeSelectionEnabled: Boolean,
    onModeChange: (CameraMode) -> Unit,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            if (modeSelectionEnabled) {
                ModeLabel(
                    text = stringResource(R.string.camera_photo),
                    selected = mode == CameraMode.PHOTO,
                    enabled = !captureBusy && !isRecording,
                    onClick = { onModeChange(CameraMode.PHOTO) }
                )
            }
            ModeLabel(
                text = stringResource(R.string.camera_video),
                selected = mode == CameraMode.VIDEO,
                enabled = modeSelectionEnabled && !captureBusy && !isRecording,
                onClick = { onModeChange(CameraMode.VIDEO) }
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = if (recentMedia == null) 0.12f else 0.32f)),
                contentAlignment = Alignment.Center
            ) {
                recentMedia?.let {
                    Text(if (it.type == CaptureType.PHOTO) "JPG" else "MP4", color = Color.White)
                }
            }

            Box(contentAlignment = Alignment.Center) {
                if (isRecording) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(86.dp),
                        color = Color.Red,
                        trackColor = Color.White.copy(alpha = 0.28f),
                        strokeWidth = 5.dp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRecording -> Color.Red
                                mode == CameraMode.VIDEO -> Color.Red.copy(alpha = 0.88f)
                                else -> Color.White
                            }
                        )
                        .border(4.dp, Color.White.copy(alpha = 0.82f), CircleShape)
                        .clickable(enabled = !captureBusy || isRecording, onClick = onCaptureClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Icon(Icons.Default.Close, stringResource(R.string.camera_stop_recording), tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.size(52.dp))
        }
    }
}

@Composable
private fun ModeLabel(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = when {
            !enabled -> Color.White.copy(alpha = 0.35f)
            selected -> Color(0xFFFFD54F)
            else -> Color.White
        },
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(8.dp)
    )
}

@Composable
private fun CameraFrameGuide(
    orientation: CaptureOrientation,
    modifier: Modifier = Modifier
) {
    val frameModifier = if (orientation == CaptureOrientation.PORTRAIT) {
        Modifier.fillMaxWidth(0.88f).aspectRatio(9f / 16f)
    } else {
        Modifier.fillMaxWidth(0.94f).aspectRatio(16f / 9f)
    }
    Box(
        modifier = modifier
            .then(frameModifier)
            .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .drawBehind {
                val lineColor = Color.White.copy(alpha = 0.42f)
                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(size.width / 3f, 0f), end = androidx.compose.ui.geometry.Offset(size.width / 3f, size.height), strokeWidth = 1.dp.toPx())
                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, 0f), end = androidx.compose.ui.geometry.Offset(size.width * 2f / 3f, size.height), strokeWidth = 1.dp.toPx())
                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(0f, size.height / 3f), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 3f), strokeWidth = 1.dp.toPx())
                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(0f, size.height * 2f / 3f), end = androidx.compose.ui.geometry.Offset(size.width, size.height * 2f / 3f), strokeWidth = 1.dp.toPx())
            }
    )
}

@Composable
private fun CameraSettingsDialog(
    settings: CameraSettings,
    capabilities: CameraCapabilities,
    audioPermissionGranted: Boolean,
    onSettingsChange: (CameraSettings) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.camera_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.video_quality), style = MaterialTheme.typography.titleSmall)
                VideoQualityPreset.entries.forEach { preset ->
                    val supported = capabilities.supports(preset)
                    SettingRadioRow(
                        label = if (supported) preset.displayName else {
                            "${preset.displayName} · ${stringResource(R.string.quality_not_supported)}"
                        },
                        selected = settings.videoQuality == preset,
                        enabled = supported,
                        onClick = { onSettingsChange(settings.copy(videoQuality = preset)) }
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.capture_orientation), style = MaterialTheme.typography.titleSmall)
                SettingRadioRow(
                    label = stringResource(R.string.orientation_portrait),
                    selected = settings.orientation == CaptureOrientation.PORTRAIT,
                    enabled = true,
                    onClick = { onSettingsChange(settings.copy(orientation = CaptureOrientation.PORTRAIT)) }
                )
                SettingRadioRow(
                    label = stringResource(R.string.orientation_landscape),
                    selected = settings.orientation == CaptureOrientation.LANDSCAPE,
                    enabled = true,
                    onClick = { onSettingsChange(settings.copy(orientation = CaptureOrientation.LANDSCAPE)) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.camera_audio))
                    Switch(
                        checked = settings.audioEnabled && audioPermissionGranted,
                        onCheckedChange = { onSettingsChange(settings.copy(audioEnabled = it)) },
                        enabled = audioPermissionGranted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.camera_done)) }
        }
    )
}

@Composable
private fun SettingRadioRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(
            text = label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            }
        )
    }
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun requiredCameraPermissions(): Array<String> = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}.toTypedArray()
