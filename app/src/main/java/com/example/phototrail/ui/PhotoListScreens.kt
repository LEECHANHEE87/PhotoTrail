package com.example.phototrail.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.phototrail.R
import com.example.phototrail.data.PhotoItemEntity
import com.example.phototrail.ui.theme.PhotoTrailSpacing
import kotlinx.coroutines.flow.StateFlow

private data class DatePhotoGroup(
    val dateKey: String,
    val totalCount: Int,
    val locationCount: Int,
    val noLocationCount: Int,
    val locationGroupCount: Int,
    val representativePhotoUri: String?
)

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
    onExcludePhoto: ((Long) -> Unit)? = null,
    onSetRepresentative: ((String) -> Unit)? = null,
    onAddPhotosClick: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    onPhotosSelected: ((List<Long>) -> Unit)? = null
) {
    val allPhotos by photos.collectAsState()
    val visiblePhotos = remember(allPhotos, filter) { allPhotos.filter(filter) }
    
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$title (${visiblePhotos.size})") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    } else if (onAddPhotosClick != null) {
                        IconButton(onClick = onAddPhotosClick) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_photo))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (visiblePhotos.isEmpty()) {
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
                itemsIndexed(visiblePhotos, key = { _, photo -> photo.id }) { index, photo ->
                    PhotoGridItem(
                        photo = photo,
                        onClick = {
                            if (isSelectionMode) {
                                selectedIds = if (photo.mediaStoreId in selectedIds) {
                                    selectedIds - photo.mediaStoreId
                                } else {
                                    selectedIds + photo.mediaStoreId
                                }
                            } else {
                                onPhotoClick(visiblePhotos, index)
                            }
                        },
                        isSelected = photo.mediaStoreId in selectedIds,
                        isSelectionMode = isSelectionMode,
                        onExclude = if (onExcludePhoto != null) { { onExcludePhoto(photo.mediaStoreId) } } else null,
                        onSetRepresentative = if (onSetRepresentative != null) { { onSetRepresentative(photo.uri) } } else null
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    photo: PhotoItemEntity,
    onClick: () -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onExclude: (() -> Unit)?,
    onSetRepresentative: (() -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(1.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (!isSelectionMode && (onExclude != null || onSetRepresentative != null)) showMenu = true }
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

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            onSetRepresentative?.let {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.set_as_representative)) },
                    onClick = {
                        showMenu = false
                        it()
                    },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) }
                )
            }
            onExclude?.let {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exclude_from_trip)) },
                    onClick = {
                        showMenu = false
                        it()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
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
