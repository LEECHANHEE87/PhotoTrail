package com.example.phototrail.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.phototrail.R
import com.example.phototrail.camera.CaptureOrientation
import com.example.phototrail.camera.CameraCapabilities
import com.example.phototrail.camera.CameraCapabilityProvider
import com.example.phototrail.camera.VideoQualityPreset
import com.example.phototrail.data.MakingProjectEntity
import com.example.phototrail.data.TripAlbumEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MakingTripListScreen(
    photoViewModel: PhotoViewModel,
    makingViewModel: MakingViewModel,
    onBackClick: () -> Unit,
    onTripClick: (TripAlbumEntity) -> Unit
) {
    val trips by photoViewModel.tripAlbums.collectAsState()
    val projects by makingViewModel.projects.collectAsState()
    val links by makingViewModel.links.collectAsState()
    val projectsByTrip = projects.associateBy { it.tripKey }
    val clipCounts = links.groupingBy { it.tripKey }.eachCount()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.making_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (trips.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Star, contentDescription = null)
                Text(
                    stringResource(R.string.making_no_trips),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.making_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(trips, key = { it.tripKey }) { trip ->
                    val project = projectsByTrip[trip.tripKey]
                    MakingTripCard(
                        trip = trip,
                        project = project,
                        clipCount = clipCounts[trip.tripKey] ?: 0,
                        onClick = { onTripClick(trip) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MakingTripCard(
    trip: TripAlbumEntity,
    project: MakingProjectEntity?,
    clipCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(trip.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                "${trip.startDateKey} · ${trip.endDateKey}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                project?.let {
                    stringResource(
                        R.string.making_project_summary,
                        qualityLabel(it.videoQuality),
                        orientationLabel(it.orientation),
                        clipCount
                    )
                } ?: stringResource(R.string.making_project_not_configured),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MakingSetupScreen(
    tripKey: String,
    photoViewModel: PhotoViewModel,
    makingViewModel: MakingViewModel,
    onBackClick: () -> Unit,
    onStartCamera: () -> Unit
) {
    val trips by photoViewModel.tripAlbums.collectAsState()
    val project by makingViewModel.selectedProject.collectAsState()
    val links by makingViewModel.links.collectAsState()
    val trip = trips.firstOrNull { it.tripKey == tripKey }
    val context = LocalContext.current
    var capabilities by remember { mutableStateOf(CameraCapabilities()) }

    LaunchedEffect(tripKey) {
        makingViewModel.selectTrip(tripKey)
        CameraCapabilityProvider(context.applicationContext).loadBackCamera(
            onResult = { capabilities = it }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.displayTitle ?: stringResource(R.string.making_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        val current = project
        if (current == null || current.tripKey != tripKey) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(stringResource(R.string.making_quality), style = MaterialTheme.typography.titleMedium)
                }
                items(VideoQualityPreset.entries) { preset ->
                    val supported = capabilities.supports(preset)
                    MakingRadioRow(
                        label = if (supported) preset.displayName else {
                            "${preset.displayName} · ${stringResource(R.string.quality_not_supported)}"
                        },
                        selected = current.videoQuality == preset.name,
                        enabled = supported,
                        onClick = {
                            makingViewModel.updateProject(current.copy(videoQuality = preset.name))
                        }
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.making_orientation), style = MaterialTheme.typography.titleMedium)
                }
                items(CaptureOrientation.entries) { orientation ->
                    MakingRadioRow(
                        label = if (orientation == CaptureOrientation.PORTRAIT) {
                            stringResource(R.string.orientation_portrait_9_16)
                        } else {
                            stringResource(R.string.orientation_landscape_16_9)
                        },
                        selected = current.orientation == orientation.name,
                        onClick = {
                            makingViewModel.updateProject(current.copy(orientation = orientation.name))
                        }
                    )
                }
                item {
                    MakingSwitchRow(
                        label = stringResource(R.string.making_grid),
                        checked = current.showGrid,
                        onCheckedChange = {
                            makingViewModel.updateProject(current.copy(showGrid = it))
                        }
                    )
                    MakingSwitchRow(
                        label = stringResource(R.string.camera_audio),
                        checked = current.audioEnabled,
                        onCheckedChange = {
                            makingViewModel.updateProject(current.copy(audioEnabled = it))
                        }
                    )
                    Text(
                        stringResource(
                            R.string.making_clip_count,
                            links.count { it.tripKey == tripKey }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    com.example.phototrail.ui.PhotoTrailPrimaryButton(
                        text = stringResource(R.string.making_start),
                        onClick = {
                            makingViewModel.saveProject(current)
                            onStartCamera()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Add
                    )
                }
            }
        }
    }
}

@Composable
private fun MakingRadioRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(
            label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            }
        )
    }
}

@Composable
private fun MakingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun qualityLabel(value: String): String =
    runCatching { VideoQualityPreset.valueOf(value).displayName }.getOrDefault(value)

private fun orientationLabel(value: String): String = when (value) {
    CaptureOrientation.LANDSCAPE.name -> "16:9"
    else -> "9:16"
}
