package com.example.phototrail.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.phototrail.R
import com.example.phototrail.data.PhotoItemEntity
import kotlinx.coroutines.flow.StateFlow

private data class DatePhotoGroup(
    val dateKey: String,
    val totalCount: Int,
    val locationCount: Int,
    val noLocationCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateListScreen(
    viewModel: PhotoViewModel,
    onBackClick: () -> Unit,
    onOpenDate: (String) -> Unit
) {
    val photos by viewModel.allPhotos.collectAsState()
    val groups = remember(photos) {
        photos.groupBy { it.dateKey }
            .map { (dateKey, items) ->
                DatePhotoGroup(
                    dateKey = dateKey,
                    totalCount = items.size,
                    locationCount = items.count { it.hasLocation },
                    noLocationCount = items.count { !it.hasLocation }
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
                    .padding(innerPadding)
            ) {
                items(groups, key = { it.dateKey }) { group ->
                    ListItem(
                        headlineContent = { Text(group.dateKey) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.date_group_stats,
                                    group.totalCount,
                                    group.locationCount,
                                    group.noLocationCount
                                )
                            )
                        },
                        modifier = Modifier.clickable { onOpenDate(group.dateKey) }
                    )
                    HorizontalDivider()
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
    filter: (PhotoItemEntity) -> Boolean = { true }
) {
    val allPhotos by photos.collectAsState()
    val visiblePhotos = remember(allPhotos, filter) { allPhotos.filter(filter) }

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
                items(visiblePhotos, key = { it.id }) { photo ->
                    AsyncImage(
                        model = Uri.parse(photo.uri),
                        contentDescription = photo.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(1.dp),
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
