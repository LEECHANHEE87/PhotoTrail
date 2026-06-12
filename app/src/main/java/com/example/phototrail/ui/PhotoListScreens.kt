package com.example.phototrail.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.phototrail.R
import com.example.phototrail.data.OverrideType
import com.example.phototrail.data.PhotoItemEntity
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

    Scaffold(
        topBar = {
            PhotoTopBar(
                title = stringResource(R.string.view_by_date),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        if (groups.isEmpty()) {
            EmptyPhotoMessage(
                modifier = Modifier.padding(innerPadding),
                text = stringResource(R.string.no_indexed_photos)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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

@Composable
private fun DateSummaryCard(
    group: DatePhotoGroup,
    onClick: () -> Unit,
    onMapClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box {
                group.representativePhotoUri?.let { uri ->
                    AsyncImage(
                        model = Uri.parse(uri),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentScale = ContentScale.Crop
                    )
                } ?: Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = group.dateKey,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.photo_count, group.totalCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatItem(
                        icon = Icons.Default.LocationOn,
                        label = "GPS ${group.locationCount}",
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatItem(
                        icon = Icons.Default.Place,
                        label = "${group.locationGroupCount} Groups",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.view_all))
                    }
                    
                    if (group.locationCount > 0) {
                        OutlinedButton(
                            onClick = onMapClick,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.view_on_map))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            EmptyPhotoMessage(
                modifier = Modifier.padding(innerPadding),
                text = stringResource(R.string.no_photos)
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

@Composable
private fun EmptyPhotoMessage(modifier: Modifier, text: String) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp)
        )
    }
}
