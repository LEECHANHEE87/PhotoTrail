package com.example.phototrail.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.phototrail.R
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
                DatePhotoGroup(
                    dateKey = dateKey,
                    totalCount = items.size,
                    locationCount = items.count { it.hasLocation },
                    noLocationCount = items.count { !it.hasLocation },
                    locationGroupCount = items.filter { it.hasLocation }
                        .map { it.bucketKey }
                        .distinct()
                        .size,
                    representativePhotoUri = items.firstOrNull()?.uri
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            group.representativePhotoUri?.let { uri ->
                AsyncImage(
                    model = Uri.parse(uri),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentScale = ContentScale.Crop
                )
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.dateKey,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.photo_count, group.totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(
                        R.string.date_group_detailed_stats,
                        group.locationCount,
                        group.noLocationCount,
                        group.locationGroupCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (group.locationCount > 0) {
                    OutlinedButton(
                        onClick = onMapClick,
                        modifier = Modifier.fillMaxWidth()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGridScreen(
    title: String,
    photos: StateFlow<List<PhotoItemEntity>>,
    onBackClick: () -> Unit,
    onPhotoClick: (List<PhotoItemEntity>, Int) -> Unit,
    filter: (PhotoItemEntity) -> Boolean = { true }
) {
    val allPhotos by photos.collectAsState()
    val visiblePhotos = remember(allPhotos, filter) { allPhotos.filter(filter) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            PhotoTopBar(
                title = "$title (${visiblePhotos.size})",
                onBackClick = onBackClick
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
                    AsyncImage(
                        model = Uri.parse(photo.uri),
                        contentDescription = photo.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .clickable {
                                onPhotoClick(visiblePhotos, index)
                            },
                        contentScale = ContentScale.Crop
                    )
                }
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
