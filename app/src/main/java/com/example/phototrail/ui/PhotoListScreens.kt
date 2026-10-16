package com.example.phototrail.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.phototrail.R
import com.example.phototrail.data.PhotoItemEntity
import com.example.phototrail.data.VideoItemEntity
import com.example.phototrail.ui.theme.PhotoTrailSpacing
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateListScreen(
    viewModel: PhotoViewModel,
    onBackClick: () -> Unit,
    onOpenDate: (String) -> Unit,
    onNavigateToMap: (String) -> Unit
) {
    val photos by viewModel.allPhotos.collectAsState()
    val groups = remember(photos) {
        photos.groupBy { it.dateKey }
            .map { (dateKey, items) ->
                val representativePhoto = items.find { it.hasLocation } ?: items.firstOrNull()
                DatePhotoGroup(
                    dateKey = dateKey,
                    totalCount = items.size,
                    locationCount = items.count { it.hasLocation },
                    noLocationCount = items.count { !it.hasLocation },
                    locationGroupCount = items.filter { it.hasLocation }
                        .map { it.bucketKey }
                        .distinct()
                        .size,
                    representativePhotoUri = representativePhoto?.uri
                )
            }
            .sortedByDescending { it.dateKey }
    }

    val isWideScreen = LocalConfiguration.current.screenWidthDp > 600

    Scaffold(
        topBar = {
            PhotoTopBar(
                title = stringResource(R.string.view_by_date),
                onBackClick = onBackClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (groups.isEmpty()) {
            PhotoTrailEmptyState(
                modifier = Modifier.padding(innerPadding),
                icon = Icons.Default.DateRange,
                title = stringResource(R.string.no_indexed_photos),
                description = stringResource(R.string.scan_photos_empty_description)
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
                    items(groups, key = { it.dateKey }) { group ->
                        DateSummaryCard(
                            group = group,
                            onClick = { onOpenDate(group.dateKey) },
                            onMapClick = { onNavigateToMap(group.dateKey) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(groups, key = { it.dateKey }) { group ->
                        DateSummaryCard(
                            group = group,
                            onClick = { onOpenDate(group.dateKey) },
                            onMapClick = { onNavigateToMap(group.dateKey) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSummaryCard(
    group: DatePhotoGroup,
    onClick: () -> Unit,
    onMapClick: () -> Unit
) {
    PhotoTrailCard(onClick = onClick) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                if (group.representativePhotoUri != null) {
                    AsyncImage(
                        model = Uri.parse(group.representativePhotoUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )
                
                Text(
                    text = group.dateKey,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                )
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PhotoTrailStatChip(
                        icon = Icons.AutoMirrored.Filled.List,
                        label = stringResource(R.string.photo_count, group.totalCount),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (group.locationCount > 0) {
                        PhotoTrailStatChip(
                            icon = Icons.Default.LocationOn,
                            label = stringResource(R.string.place_count, group.locationGroupCount)
                        )
                    }
                }
                
                if (group.locationCount > 0) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onMapClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.view_on_map))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGridScreen(
    title: String,
    photos: StateFlow<List<PhotoItemEntity>>,
    onBackClick: () -> Unit,
    onPhotoClick: (List<PhotoItemEntity>, Int) -> Unit,
    filter: (PhotoItemEntity) -> Boolean = { true },
    tripKey: String? = null,
    onExcludePhotos: ((List<Long>) -> Unit)? = null,
    onSetRepresentative: ((String) -> Unit)? = null,
    onAddPhotosClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    onPhotosSelected: ((List<Long>) -> Unit)? = null,
    videos: StateFlow<List<VideoItemEntity>>? = null,
    videoFilter: (VideoItemEntity) -> Boolean = { true },
    onVideoClick: ((VideoItemEntity) -> Unit)? = null
) {
    val allPhotos by photos.collectAsState()
    val visiblePhotos = remember(allPhotos, filter) { allPhotos.filter(filter) }
    val videoState = videos?.collectAsState()
    val allVideos = videoState?.value.orEmpty()
    val visibleVideos = remember(allVideos, videoFilter) { allVideos.filter(videoFilter) }
    val mediaItems = remember(visiblePhotos, visibleVideos) {
        (visiblePhotos.map { TripGridEntry.Photo(it) } + visibleVideos.map { TripGridEntry.Video(it) })
            .sortedByDescending { it.takenAt }
    }
    val context = LocalContext.current
    val videoImageLoader = remember {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }
    
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var isExcludeSelectionMode by remember { mutableStateOf(false) }
    val isPhotoSelectionMode = isSelectionMode || isExcludeSelectionMode

    LaunchedEffect(visiblePhotos, isPhotoSelectionMode) {
        if (isPhotoSelectionMode) {
            val visibleIds = visiblePhotos.map { it.mediaStoreId }.toSet()
            selectedIds = selectedIds.intersect(visibleIds)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isExcludeSelectionMode) {
                            stringResource(R.string.selected_photo_count, selectedIds.size)
                        } else {
                            "$title (${mediaItems.size})"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isExcludeSelectionMode) {
                                selectedIds = emptySet()
                                isExcludeSelectionMode = false
                            } else {
                                onBackClick()
                            }
                        }
                    ) {
                        Icon(
                            if (isExcludeSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(if (isExcludeSelectionMode) R.string.cancel else R.string.back)
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(
                            onClick = { onPhotosSelected?.invoke(selectedIds.toList()) },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.add))
                        }
                    } else if (isExcludeSelectionMode) {
                        if (selectedIds.size == 1 && onSetRepresentative != null) {
                            IconButton(
                                onClick = {
                                    visiblePhotos
                                        .firstOrNull { it.mediaStoreId == selectedIds.first() }
                                        ?.let { photo -> onSetRepresentative(photo.uri) }
                                    selectedIds = emptySet()
                                    isExcludeSelectionMode = false
                                }
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = stringResource(R.string.set_as_representative)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                onExcludePhotos?.invoke(selectedIds.toList())
                                selectedIds = emptySet()
                                isExcludeSelectionMode = false
                            },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.exclude_selected_photos))
                        }
                    } else {
                        if (onAddPhotosClick != null) {
                            IconButton(onClick = onAddPhotosClick) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_photo))
                            }
                        }
                        if (onExcludePhotos != null) {
                            IconButton(
                                onClick = {
                                    selectedIds = emptySet()
                                    isExcludeSelectionMode = true
                                },
                                enabled = visiblePhotos.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.select_photos_to_exclude))
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (mediaItems.isEmpty()) {
            PhotoTrailEmptyState(
                modifier = Modifier.padding(innerPadding),
                icon = Icons.Default.Info,
                title = stringResource(R.string.no_photos),
                description = stringResource(R.string.no_group_photos_description)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(mediaItems, key = { it.key }) { item ->
                    when (item) {
                        is TripGridEntry.Photo -> {
                            val photo = item.value
                            PhotoGridItem(
                                photo = photo,
                                onClick = {
                                    if (isPhotoSelectionMode) {
                                        selectedIds = if (photo.mediaStoreId in selectedIds) {
                                            selectedIds - photo.mediaStoreId
                                        } else {
                                            selectedIds + photo.mediaStoreId
                                        }
                                    } else {
                                        onPhotoClick(visiblePhotos, visiblePhotos.indexOf(photo))
                                    }
                                },
                                onLongClick = if (!isSelectionMode && onExcludePhotos != null) {
                                    {
                                        selectedIds = setOf(photo.mediaStoreId)
                                        isExcludeSelectionMode = true
                                    }
                                } else {
                                    null
                                },
                                isSelected = photo.mediaStoreId in selectedIds,
                                isSelectionMode = isPhotoSelectionMode
                            )
                        }
                        is TripGridEntry.Video -> TripVideoGridItem(
                            video = item.value,
                            imageLoader = videoImageLoader,
                            enabled = !isPhotoSelectionMode,
                            onClick = { onVideoClick?.invoke(item.value) }
                        )
                    }
                }
            }
        }
    }
}

private sealed interface TripGridEntry {
    val key: String
    val takenAt: Long

    data class Photo(val value: PhotoItemEntity) : TripGridEntry {
        override val key = "photo_${value.mediaStoreId}"
        override val takenAt = value.takenAt ?: 0L
    }

    data class Video(val value: VideoItemEntity) : TripGridEntry {
        override val key = "video_${value.mediaStoreId}"
        override val takenAt = value.takenAt ?: 0L
    }
}

@Composable
private fun TripVideoGridItem(
    video: VideoItemEntity,
    imageLoader: ImageLoader,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(1.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(Uri.parse(video.uri))
                .videoFrameMillis(0)
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = video.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(38.dp)
                .background(Color.Black.copy(alpha = 0.58f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play_video), tint = Color.White)
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
            color = Color.Black.copy(alpha = 0.68f),
            contentColor = Color.White,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = formatTripVideoDuration(video.durationMs),
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (!enabled) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
    }
}

private fun formatTripVideoDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    photo: PhotoItemEntity,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    isSelected: Boolean,
    isSelectionMode: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(1.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = Uri.parse(photo.uri),
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isSelectionMode && isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoTopBar(title: String, onBackClick: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        }
    )
}
