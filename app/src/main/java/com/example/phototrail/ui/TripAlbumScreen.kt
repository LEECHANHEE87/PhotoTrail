package com.example.phototrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.phototrail.R
import com.example.phototrail.data.TripAlbumEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripAlbumScreen(
    viewModel: PhotoViewModel,
    showHiddenOnly: Boolean = false,
    onBackClick: () -> Unit,
    onNavigateToHidden: () -> Unit = {},
    onOpenTrip: (TripAlbumEntity) -> Unit,
    onOpenTripOnMap: (TripAlbumEntity) -> Unit
) {
    val tripAlbums by (if (showHiddenOnly) viewModel.hiddenTripAlbums else viewModel.tripAlbums).collectAsState()
    val isGenerating by viewModel.isGeneratingTrips.collectAsState()

    var tripToRename by remember { mutableStateOf<TripAlbumEntity?>(null) }
    var tripToHide by remember { mutableStateOf<TripAlbumEntity?>(null) }
    var tripToSplit by remember { mutableStateOf<TripAlbumEntity?>(null) }
    
    var isMergeMode by remember { mutableStateOf(false) }
    var selectedTripIds by remember { mutableStateOf(setOf<Long>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isMergeMode) stringResource(R.string.merge_mode)
                        else stringResource(if (showHiddenOnly) R.string.view_hidden_trips else R.string.view_trips)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isMergeMode) {
                            isMergeMode = false
                            selectedTripIds = emptySet()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            if (isMergeMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (isMergeMode) {
                        TextButton(
                            onClick = {
                                viewModel.mergeTrips(selectedTripIds.toList())
                                isMergeMode = false
                                selectedTripIds = emptySet()
                            },
                            enabled = selectedTripIds.size >= 2
                        ) {
                            Text(stringResource(R.string.merge))
                        }
                    } else if (!showHiddenOnly) {
                        IconButton(onClick = onNavigateToHidden) {
                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.view_hidden_trips))
                        }
                        IconButton(onClick = { viewModel.generateTrips() }, enabled = !isGenerating) {
                            if (isGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.rebuild_trips))
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (tripAlbums.isEmpty() && !isGenerating) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.no_indexed_photos), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(tripAlbums) { album ->
                    TripAlbumCard(
                        album = album,
                        onClick = {
                            if (isMergeMode) {
                                selectedTripIds = if (album.id in selectedTripIds) {
                                    selectedTripIds - album.id
                                } else {
                                    selectedTripIds + album.id
                                }
                            } else {
                                onOpenTrip(album)
                            }
                        },
                        onMapClick = { onOpenTripOnMap(album) },
                        onRename = { tripToRename = album },
                        onHide = { tripToHide = album },
                        onRestore = { viewModel.unhideTripAlbum(album.id) },
                        onSplit = { tripToSplit = album },
                        onEnterMergeMode = {
                            isMergeMode = true
                            selectedTripIds = setOf(album.id)
                        },
                        isSelected = album.id in selectedTripIds,
                        isSelectionEnabled = isMergeMode
                    )
                }
            }
        }

        // Rename Dialog
        tripToRename?.let { album ->
            RenameTripDialog(
                initialTitle = album.displayTitle,
                onDismiss = { tripToRename = null },
                onConfirm = { newTitle ->
                    viewModel.renameTripAlbum(album.id, newTitle)
                    tripToRename = null
                }
            )
        }

        // Hide Dialog
        tripToHide?.let { album ->
            HideTripConfirmDialog(
                onDismiss = { tripToHide = null },
                onConfirm = {
                    viewModel.hideTripAlbum(album.id)
                    tripToHide = null
                }
            )
        }

        // Split Dialog
        tripToSplit?.let { album ->
            SplitTripDialog(
                album = album,
                onDismiss = { tripToSplit = null },
                onConfirm = { dates ->
                    viewModel.splitTrip(album.id, dates)
                    tripToSplit = null
                }
            )
        }
    }
}

@Composable
fun TripAlbumCard(
    album: TripAlbumEntity,
    onClick: () -> Unit,
    onMapClick: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onRestore: () -> Unit,
    onSplit: () -> Unit,
    onEnterMergeMode: () -> Unit,
    isSelected: Boolean = false,
    isSelectionEnabled: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            width = 3.dp
        ) else null
    ) {
        Column {
            Box {
                AsyncImage(
                    model = album.finalRepresentativeUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
                
                if (isSelectionEnabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        val dateRange = if (album.startDateKey == album.endDateKey) {
                            album.startDateKey
                        } else {
                            "${album.startDateKey} ~ ${album.endDateKey}"
                        }
                        Text(
                            text = dateRange,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isSelectionEnabled) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More"
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename_trip)) },
                                    onClick = {
                                        showMenu = false
                                        onRename()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.split_trip)) },
                                    onClick = {
                                        showMenu = false
                                        onSplit()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.merge_trips)) },
                                    onClick = {
                                        showMenu = false
                                        onEnterMergeMode()
                                    },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                                )
                                if (album.isHidden) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.unhide_trip)) },
                                        onClick = {
                                            showMenu = false
                                            onRestore()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.hide_trip)) },
                                        onClick = {
                                            showMenu = false
                                            onHide()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.trip_stats, album.photoCount, album.placeGroupCount),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                if (album.noLocationPhotoCount > 0) {
                    Text(
                        text = stringResource(R.string.trip_no_location_suffix, album.noLocationPhotoCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                
                if (!isSelectionEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.open_photo_list))
                        }
                        IconButton(
                            onClick = onMapClick,
                            modifier = Modifier
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.view_on_map))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameTripDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_trip)) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.new_title_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun HideTripConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hide_trip)) },
        text = { Text(stringResource(R.string.hide_trip_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SplitTripDialog(
    album: TripAlbumEntity,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val dates = remember(album) { album.dateKeys.split(",") }
    var selectedDates by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_trip)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.select_dates_to_split),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.size(height = 300.dp, width = 280.dp)) {
                    items(dates) { date ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedDates = if (date in selectedDates) {
                                        selectedDates - date
                                    } else {
                                        selectedDates + date
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = date in selectedDates,
                                onCheckedChange = { checked ->
                                    selectedDates = if (checked) {
                                        selectedDates + date
                                    } else {
                                        selectedDates - date
                                    }
                                }
                            )
                            Text(text = date, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedDates.toList()) },
                enabled = selectedDates.isNotEmpty() && selectedDates.size < dates.size
            ) {
                Text(stringResource(R.string.split))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
