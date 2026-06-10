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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch

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
    dateFilter: String? = null,
    onBackClick: () -> Unit,
    onOpenBucket: (String) -> Unit
) {
    val allPhotosWithLocation by viewModel.photosWithLocation.collectAsState()
    val photos = remember(allPhotosWithLocation, dateFilter) {
        if (dateFilter != null) {
            allPhotosWithLocation.filter { it.dateKey == dateFilter }
        } else {
            allPhotosWithLocation
        }
    }
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
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                ModalBottomSheet(
                    onDismissRequest = { selectedGroup = null },
                    sheetState = sheetState
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Representative Thumbnail
                            AsyncImage(
                                model = Uri.parse(group.photos.first().uri),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.dateKey,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.photo_count, group.photos.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) {
                                        onOpenBucket(group.bucketKey)
                                        selectedGroup = null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.open_photo_list))
                        }

                        Text(
                            text = stringResource(R.string.location_photo_group),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(group.photos, key = { it.id }) { photo ->
                                AsyncImage(
                                    model = Uri.parse(photo.uri),
                                    contentDescription = photo.displayName,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(Uri.parse(photo.uri), "image/*")
                                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.cannot_open_photo),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
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
