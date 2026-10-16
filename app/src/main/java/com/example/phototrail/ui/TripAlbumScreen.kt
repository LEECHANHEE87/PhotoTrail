package com.example.phototrail.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.phototrail.R
import com.example.phototrail.data.TripAlbumEntity
import com.example.phototrail.ui.theme.PhotoTrailSpacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripAlbumScreen(
    viewModel: PhotoViewModel,
    videoViewModel: VideoViewModel,
    showHiddenOnly: Boolean = false,
    onBackClick: () -> Unit,
    onNavigateToHidden: () -> Unit = {},
    onOpenTrip: (TripAlbumEntity) -> Unit,
    onOpenTripOnMap: (TripAlbumEntity) -> Unit
) {
    val tripAlbums by (if (showHiddenOnly) viewModel.hiddenTripAlbums else viewModel.tripAlbums).collectAsState()
    val isGenerating by viewModel.isGeneratingTrips.collectAsState()
    val videos by videoViewModel.allVideos.collectAsState()
    val videoLinks by videoViewModel.tripLinks.collectAsState()

    var tripToRename by remember { mutableStateOf<TripAlbumEntity?>(null) }
    var tripToEdit by remember { mutableStateOf<TripAlbumEntity?>(null) }
    var tripToHide by remember { mutableStateOf<TripAlbumEntity?>(null) }
    var tripToSplit by remember { mutableStateOf<TripAlbumEntity?>(null) }
    var showCreateTrip by remember { mutableStateOf(false) }
    
    var isMergeMode by remember { mutableStateOf(false) }
    var selectedTripIds by remember { mutableStateOf(setOf<Long>()) }

    val isWideScreen = LocalConfiguration.current.screenWidthDp > 600

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
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
        floatingActionButton = {
            if (!showHiddenOnly && !isMergeMode) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateTrip = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.create_trip)) }
                )
            }
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
                            videoCount = videos.count { video ->
                                video.dateKey in album.dateKeys.split(",") || videoLinks.any {
                                    it.tripKey == album.tripKey && it.videoMediaStoreId == video.mediaStoreId
                                }
                            },
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
                            onEditSchedule = { tripToEdit = album },
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
                            videoCount = videos.count { video ->
                                video.dateKey in album.dateKeys.split(",") || videoLinks.any {
                                    it.tripKey == album.tripKey && it.videoMediaStoreId == video.mediaStoreId
                                }
                            },
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
                            onEditSchedule = { tripToEdit = album },
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

        if (showCreateTrip) {
            TripScheduleDialog(
                title = stringResource(R.string.create_trip),
                initialName = "",
                initialStartDate = todayDateKey(),
                initialEndDate = todayDateKey(),
                nameRequired = true,
                onDismiss = { showCreateTrip = false },
                onConfirm = { name, startDate, endDate ->
                    viewModel.createTripAlbum(name, startDate, endDate)
                    showCreateTrip = false
                }
            )
        }

        tripToEdit?.let { album ->
            TripScheduleDialog(
                title = stringResource(R.string.edit_trip_schedule),
                initialName = album.customTitle.orEmpty(),
                initialStartDate = album.startDateKey,
                initialEndDate = album.endDateKey,
                nameRequired = false,
                onDismiss = { tripToEdit = null },
                onConfirm = { name, startDate, endDate ->
                    viewModel.updateTripSchedule(album.id, name, startDate, endDate)
                    tripToEdit = null
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
    videoCount: Int,
    onClick: () -> Unit,
    onMapClick: () -> Unit,
    onRename: () -> Unit,
    onEditSchedule: () -> Unit,
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
            Box(modifier = Modifier.fillMaxWidth().height(232.dp)) {
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
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.08f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.78f)
                                )
                            )
                        )
                )

                val dateRange = if (album.startDateKey == album.endDateKey) {
                    album.startDateKey
                } else {
                    "${album.startDateKey} ~ ${album.endDateKey}"
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = dateRange,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isSelectionEnabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(34.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
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
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = stringResource(R.string.photo_count, album.photoCount),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = stringResource(R.string.video_count, videoCount),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White
                        ) {
                            Text(
                                text = stringResource(R.string.place_count, album.placeGroupCount),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Column(modifier = Modifier.padding(14.dp)) {
                TripLogStrip(album = album)

                if (!isSelectionEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
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
                        Box {
                            OutlinedIconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.album_settings),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit_trip_schedule)) },
                                    onClick = { showMenu = false; onEditSchedule() },
                                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.rename_trip)) },
                                    onClick = { showMenu = false; onRename() },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
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
                        OutlinedIconButton(
                            onClick = onMapClick,
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
private fun TripScheduleDialog(
    title: String,
    initialName: String,
    initialStartDate: String,
    initialEndDate: String,
    nameRequired: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var startDate by remember(initialStartDate) { mutableStateOf(initialStartDate) }
    var endDate by remember(initialEndDate) { mutableStateOf(initialEndDate) }
    val isValid = (!nameRequired || name.isNotBlank()) && startDate <= endDate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(stringResource(if (nameRequired) R.string.trip_name else R.string.trip_name_optional))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TripDateButton(
                    label = stringResource(R.string.trip_start_date),
                    dateKey = startDate,
                    onDateSelected = { selected ->
                        startDate = selected
                        if (endDate < selected) endDate = selected
                    }
                )
                TripDateButton(
                    label = stringResource(R.string.trip_end_date),
                    dateKey = endDate,
                    onDateSelected = { endDate = it }
                )
                Text(
                    text = stringResource(R.string.trip_schedule_auto_include),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (startDate > endDate) {
                    Text(
                        text = stringResource(R.string.trip_date_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), startDate, endDate) },
                enabled = isValid
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun TripDateButton(
    label: String,
    dateKey: String,
    onDateSelected: (String) -> Unit
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val initial = parseDateKey(dateKey)
            val calendar = Calendar.getInstance().apply { time = initial }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    onDateSelected(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("$label  $dateKey")
    }
}

private fun todayDateKey(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private fun parseDateKey(value: String): Date =
    runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value) }.getOrNull() ?: Date()

@Composable
private fun TripLogStrip(album: TripAlbumEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TripLogMetric(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.List,
                value = album.photoCount.toString(),
                label = stringResource(R.string.filter_photos)
            )
            TripLogMetric(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocationOn,
                value = album.placeGroupCount.toString(),
                label = stringResource(R.string.place_count_short)
            )
            TripLogMetric(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Info,
                value = album.noLocationPhotoCount.toString(),
                label = stringResource(R.string.no_gps_short)
            )
        }
    }
}

@Composable
private fun TripLogMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
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
