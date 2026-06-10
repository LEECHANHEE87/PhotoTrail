package com.example.phototrail.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.phototrail.R
import com.example.phototrail.data.PhotoItemEntity
import com.example.phototrail.data.ThumbnailManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private data class PhotoMapGroup(
    val bucketKey: String,
    val dateKey: String,
    val position: LatLng,
    val photos: List<PhotoItemEntity>
)

private data class MapCluster(
    val id: String,
    val position: LatLng,
    val totalPhotos: Int,
    val groups: List<PhotoMapGroup>
)

private const val ZOOM_LEVEL_LOCAL = 15f
private const val ZOOM_LEVEL_REGIONAL = 14f
private const val ZOOM_LEVEL_CITY = 12f
private const val CLUSTERING_THRESHOLD = 15f

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: PhotoViewModel,
    dateFilter: String? = null,
    onBackClick: () -> Unit,
    onOpenBucket: (String) -> Unit
) {
    val context = LocalContext.current
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
        position = CameraPosition.fromLatLngZoom(LatLng(37.5665, 126.9780), 10f) // Default: Seoul
    }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var initialCameraSet by remember { mutableStateOf(false) }
    var locationCriterion by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(groups, initialCameraSet) {
        if (!initialCameraSet) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val hasFineLocation = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocation = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            var targetLatLng: LatLng? = null
            var zoomLevel = 13f

            if (hasFineLocation || hasCoarseLocation) {
                try {
                    val location = fusedLocationClient.lastLocation.await()
                    if (location != null) {
                        targetLatLng = LatLng(location.latitude, location.longitude)
                        locationCriterion = context.getString(R.string.criterion_current_location)
                    }
                } catch (e: Exception) {
                    // Fallback
                }
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            if (targetLatLng == null) {
                val latestPhoto = photos.maxByOrNull { it.takenAt ?: 0L }
                if (latestPhoto != null && latestPhoto.latitude != null && latestPhoto.longitude != null) {
                    targetLatLng = LatLng(latestPhoto.latitude, latestPhoto.longitude)
                    zoomLevel = ZOOM_LEVEL_LOCAL
                    locationCriterion = context.getString(R.string.criterion_recent_photo)
                }
            }

            if (targetLatLng == null && groups.isNotEmpty()) {
                val mostPhotoGroup = groups.maxByOrNull { it.photos.size }
                if (mostPhotoGroup != null) {
                    targetLatLng = mostPhotoGroup.position
                    zoomLevel = ZOOM_LEVEL_REGIONAL
                    locationCriterion = context.getString(R.string.criterion_frequent_location)
                }
            }

            if (targetLatLng != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(targetLatLng, zoomLevel)
                )
                initialCameraSet = true
            } else if (allPhotosWithLocation.isEmpty()) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(37.5665, 126.9780), ZOOM_LEVEL_CITY)
                locationCriterion = context.getString(R.string.criterion_default_seoul)
                initialCameraSet = true
            }
        }
    }

    val currentZoom = cameraPositionState.position.zoom
    val clusters = remember(groups, currentZoom) {
        if (currentZoom >= CLUSTERING_THRESHOLD) {
            groups.map { group ->
                MapCluster(
                    id = group.bucketKey,
                    position = group.position,
                    totalPhotos = group.photos.size,
                    groups = listOf(group)
                )
            }
        } else {
            val gridSize = when {
                currentZoom < 8 -> 1.0
                currentZoom < 10 -> 0.5
                currentZoom < 12 -> 0.2
                currentZoom < 14 -> 0.05
                else -> 0.01
            }
            groups.groupBy {
                val latGrid = (it.position.latitude / gridSize).toInt()
                val lngGrid = (it.position.longitude / gridSize).toInt()
                "$latGrid-$lngGrid"
            }.map { (gridId, clusterGroups) ->
                MapCluster(
                    id = gridId,
                    position = LatLng(
                        clusterGroups.map { it.position.latitude }.average(),
                        clusterGroups.map { it.position.longitude }.average()
                    ),
                    totalPhotos = clusterGroups.sumOf { it.photos.size },
                    groups = clusterGroups
                )
            }
        }
    }

    val markerIcons = remember { mutableStateMapOf<String, BitmapDescriptor>() }
    val clusterIcons = remember { mutableStateMapOf<Int, BitmapDescriptor>() }
    val imageLoader = remember { ImageLoader(context) }
    val thumbnailManager = remember { ThumbnailManager(context) }

    LaunchedEffect(clusters, cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val visibleBounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
            clusters.forEach { cluster ->
                val isVisible = visibleBounds?.contains(cluster.position) ?: true
                if (isVisible) {
                    if (currentZoom >= CLUSTERING_THRESHOLD) {
                        // Individual bucket marker
                        val group = cluster.groups.first()
                        if (!markerIcons.containsKey(group.bucketKey)) {
                            launch {
                                // 1. Try disk cache
                                val cachedBitmap = thumbnailManager.getThumbnail(group.bucketKey)
                                if (cachedBitmap != null) {
                                    markerIcons[group.bucketKey] = BitmapDescriptorFactory.fromBitmap(cachedBitmap)
                                } else {
                                    // 2. Generate and save
                                    val bitmap = generateCustomMarkerBitmap(
                                        context,
                                        imageLoader,
                                        group.photos.first().uri,
                                        group.photos.size
                                    )
                                    if (bitmap != null) {
                                        markerIcons[group.bucketKey] = BitmapDescriptorFactory.fromBitmap(bitmap)
                                        thumbnailManager.saveThumbnail(group.bucketKey, bitmap)
                                    }
                                }
                            }
                        }
                    } else {
                        // Cluster marker
                        if (!clusterIcons.containsKey(cluster.totalPhotos)) {
                            launch {
                                val descriptor = createClusterMarker(context, cluster.totalPhotos)
                                if (descriptor != null) {
                                    clusterIcons[cluster.totalPhotos] = descriptor
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(stringResource(R.string.map_screen_title))
                        if (locationCriterion.isNotEmpty()) {
                            Text(
                                text = locationCriterion,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
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
                cameraPositionState = cameraPositionState,
                onMapClick = { selectedGroup = null }
            ) {
                clusters.forEach { cluster ->
                    if (currentZoom >= CLUSTERING_THRESHOLD) {
                        val group = cluster.groups.first()
                        Marker(
                            state = rememberUpdatedMarkerState(cluster.position),
                            title = group.dateKey,
                            snippet = stringResource(R.string.photo_count, cluster.totalPhotos),
                            icon = markerIcons[group.bucketKey],
                            onClick = {
                                selectedGroup = group
                                true
                            }
                        )
                    } else {
                        Marker(
                            state = rememberUpdatedMarkerState(cluster.position),
                            icon = clusterIcons[cluster.totalPhotos],
                            onClick = {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            cluster.position,
                                            currentZoom + 2f
                                        )
                                    )
                                }
                                true
                            }
                        )
                    }
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

private suspend fun generateCustomMarkerBitmap(
    context: Context,
    loader: ImageLoader,
    uri: String,
    count: Int
): Bitmap? = withContext(Dispatchers.IO) {
    val size = 150
    val request = ImageRequest.Builder(context)
        .data(uri)
        .size(size, size)
        .allowHardware(false)
        .build()

    val result = loader.execute(request)
    if (result !is SuccessResult) return@withContext null
    
    val bitmap = result.drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)

    val output = Bitmap.createBitmap(size + 20, size + 20, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    paint.color = AndroidColor.LTGRAY
    canvas.drawCircle((size + 20) / 2f, (size + 20) / 2f, (size / 2f) + 8, paint)

    paint.color = AndroidColor.WHITE
    canvas.drawCircle((size + 20) / 2f, (size + 20) / 2f, (size / 2f) + 6, paint)

    val rect = RectF(10f, 10f, (size + 10).toFloat(), (size + 10).toFloat())
    val path = android.graphics.Path().apply {
        addCircle((size + 20) / 2f, (size + 20) / 2f, size / 2f, android.graphics.Path.Direction.CCW)
    }
    canvas.save()
    canvas.clipPath(path)
    canvas.drawBitmap(bitmap, null, rect, paint)
    canvas.restore()

    if (count > 1) {
        paint.xfermode = null
        paint.color = AndroidColor.RED
        val badgeRadius = 25f
        val badgeX = size + 5f
        val badgeY = 25f
        canvas.drawCircle(badgeX, badgeY, badgeRadius, paint)

        paint.color = AndroidColor.WHITE
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val text = if (count > 99) "99+" else count.toString()
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, badgeX, badgeY + (textBounds.height() / 2f), paint)
    }

    output
}

private suspend fun createClusterMarker(
    context: Context,
    count: Int
): BitmapDescriptor? = withContext(Dispatchers.IO) {
    val size = 100
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply {
        isAntiAlias = true
    }

    // Draw background circle (Primary colorish)
    paint.color = AndroidColor.parseColor("#3F51B5") // Primary Blue
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

    // Draw inner white circle
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)

    // Draw fill circle
    paint.color = AndroidColor.parseColor("#3F51B5")
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 10, paint)

    // Draw text
    paint.color = AndroidColor.WHITE
    paint.textSize = 32f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    val text = if (count > 999) "999+" else count.toString()
    val textBounds = Rect()
    paint.getTextBounds(text, 0, text.length, textBounds)
    canvas.drawText(text, size / 2f, size / 2f + (textBounds.height() / 2f), paint)

    BitmapDescriptorFactory.fromBitmap(output)
}
