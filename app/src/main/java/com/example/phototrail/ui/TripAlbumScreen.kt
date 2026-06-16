package com.example.phototrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.phototrail.R
import com.example.phototrail.data.TripAlbumEntity
import com.example.phototrail.ui.theme.PhotoTrailSpacing

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

    val isWideScreen = LocalConfiguration.current.screenWidthDp > 600

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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (tripAlbums.isEmpty() && !isGenerating) {
            PhotoTrailEmptyState(
                modifier = Modifier.padding(innerPadding),
                icon = Icons.Default.Star,
                title = stringResource(R.string.no_trip_albums_title),
                description = stringResource(R.string.no_trip_albums_description)
            )
        } else {
            if (isWideScreen) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
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

    PhotoTrailCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                AsyncImage(
                    model = album.finalRepresentativeUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )

                if (isSelectionEnabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(28.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options), tint = Color.White)
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_trip)) },
                                onClick = { showMenu = false; onRename() },
                                leadingIcon = { Icon(Icons.Default.Build, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.split_trip)) },
                                onClick = { showMenu = false; onSplit() },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.merge_trips)) },
                                onClick = { showMenu = false; onEnterMergeMode() },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                            )
                            if (album.isHidden) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.unhide_trip)) },
                                    onClick = { showMenu = false; onRestore() },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.hide_trip)) },
                                    onClick = { showMenu = false; onHide() },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
                
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = album.displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
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
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhotoTrailStatChip(
                        icon = Icons.AutoMirrored.Filled.List,
                        label = stringResource(R.string.photo_count, album.photoCount),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    PhotoTrailStatChip(
                        icon = Icons.Default.LocationOn,
                        label = stringResource(R.string.place_count, album.placeGroupCount)
                    )
                }

                if (!isSelectionEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PhotoTrailPrimaryButton(
                            text = stringResource(R.string.open_photo_list),
                            onClick = onClick,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Search
                        )
                        OutlinedIconButton(
                            onClick = onMapClick,
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.view_on_map), tint = MaterialTheme.colorScheme.primary)
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
