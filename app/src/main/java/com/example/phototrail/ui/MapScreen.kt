package com.example.phototrail.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.phototrail.R
import com.example.phototrail.data.PhotoItemEntity
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

private data class PhotoMapGroup(
    val bucketKey: String,
    val dateKey: String,
    val position: LatLng,
    val photos: List<PhotoItemEntity>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: PhotoViewModel,
    onBackClick: () -> Unit,
    onOpenBucket: (String) -> Unit
) {
    val photos by viewModel.photosWithLocation.collectAsState()
    val groups = remember(photos) {
        photos.groupBy { it.bucketKey }.mapNotNull { (bucketKey, items) ->
            val coordinates = items.mapNotNull { photo ->
                val latitude = photo.latitude
                val longitude = photo.longitude
                if (latitude != null && longitude != null) latitude to longitude else null
            }
            if (coordinates.isEmpty()) {
                null
            } else {
                PhotoMapGroup(
                    bucketKey = bucketKey,
                    dateKey = items.first().dateKey,
                    position = LatLng(
                        coordinates.map { it.first }.average(),
                        coordinates.map { it.second }.average()
                    ),
                    photos = items
                )
            }
        }
    }
    var selectedGroup by remember { mutableStateOf<PhotoMapGroup?>(null) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(36.2, 127.8), 6.3f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                groups.forEach { group ->
                    Marker(
                        state = rememberUpdatedMarkerState(group.position),
                        title = group.dateKey,
                        snippet = stringResource(R.string.photo_count, group.photos.size),
                        onClick = {
                            selectedGroup = group
                            true
                        }
                    )
                }
            }

            if (groups.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = stringResource(R.string.no_map_photos),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            selectedGroup?.let { group ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${group.dateKey} · " +
                                    stringResource(R.string.photo_count, group.photos.size),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Button(onClick = { onOpenBucket(group.bucketKey) }) {
                                Text(stringResource(R.string.view_all))
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(group.photos.take(8), key = { it.id }) { photo ->
                                AsyncImage(
                                    model = Uri.parse(photo.uri),
                                    contentDescription = photo.displayName,
                                    modifier = Modifier.size(72.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
