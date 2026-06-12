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
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PhotoMapGroup(
    val bucketKey: String,
    val dateKey: String,
    val position: LatLng,
    val photos: List<PhotoItemEntity>,
    val firstTakenAt: Long
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
        val filtered = if (dateFilter != null) {
            val filters = dateFilter.split(",")
            allPhotosWithLocation.filter { it.dateKey in filters }
        } else {
            allPhotosWithLocation
        }
        filtered.sortedBy { it.takenAt ?: 0L }
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
                    photos = items,
                    firstTakenAt = items.minOf { it.takenAt ?: Long.MAX_VALUE }
                )
            }
        }.sortedBy { it.firstTakenAt }
    }

    val pathPoints = remember(groups, dateFilter) {
        if (dateFilter != null && groups.size >= 2) {
            groups.map { it.position }
        } else {
            emptyList()
        }
    }

    var selectedGroup by remember { mutableStateOf<PhotoMapGroup?>(null) }
    var selectedStopIndex by remember { mutableIntStateOf(-1) }
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.5665, 126.9780), 10f)
    }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    
    val startPathColor = colorScheme.primaryContainer
    val endPathColor = colorScheme.primary

    var initialCameraSet by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(groups, initialCameraSet) {
        if (!initialCameraSet) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            var targetLatLng: LatLng? = null
            var targetBounds: LatLngBounds? = null
            var zoomLevel = 13f

            if (dateFilter != null && groups.isNotEmpty()) {
                if (groups.size >= 2) {
                    val builder = LatLngBounds.Builder()
                    groups.forEach { builder.include(it.position) }
                    targetBounds = builder.build()
                } else {
                    targetLatLng = groups.first().position
                    zoomLevel = ZOOM_LEVEL_REGIONAL
                }
            }

            if (targetLatLng == null && targetBounds == null) {
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasFineLocation) {
                    try {
                        val location = fusedLocationClient.lastLocation.await()
                        if (location != null) {
                            targetLatLng = LatLng(location.latitude, location.longitude)
                        }
                    } catch (e: Exception) {}
                }
            }

            if (targetBounds != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(targetBounds, 200)
                )
                initialCameraSet = true
            } else if (targetLatLng != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(targetLatLng, zoomLevel)
                )
                initialCameraSet = true
            }
        }
    }

    val currentZoom = cameraPositionState.position.zoom
    val clusters = remember(groups, currentZoom, dateFilter) {
        if (dateFilter != null || currentZoom >= CLUSTERING_THRESHOLD) {
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

    val journeyMarkers = remember { mutableStateMapOf<String, BitmapDescriptor>() }
    val globalMarkers = remember { mutableStateMapOf<String, BitmapDescriptor>() }
    val clusterIcons = remember { mutableStateMapOf<Int, BitmapDescriptor>() }
    val imageLoader = remember { ImageLoader(context) }
    
    var arrowIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }

    LaunchedEffect(colorScheme.primary) {
        val bitmap = generateArrowBitmap(colorScheme.primary.toArgb())
        arrowIcon = BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    LaunchedEffect(clusters, cameraPositionState.isMoving, dateFilter, selectedStopIndex) {
        if (!cameraPositionState.isMoving) {
            val visibleBounds = cameraPositionState.projection?.visibleRegion?.latLngBounds
            clusters.forEach { cluster ->
                val isVisible = visibleBounds?.contains(cluster.position) ?: true
                if (isVisible && currentZoom >= CLUSTERING_THRESHOLD) {
                    val group = cluster.groups.first()
                    if (dateFilter != null) {
                        // Journey Mode: Use Dot Markers
                        val order = groups.indexOf(group)
                        val markerKey = "journey_${order}_${if (selectedStopIndex == order) "S" else "N"}"
                        if (!journeyMarkers.containsKey(markerKey)) {
                            launch {
                                val bitmap = generateJourneyDotBitmap(
                                    order + 1,
                                    selectedStopIndex == order,
                                    colorScheme.primary.toArgb(),
                                    colorScheme.onPrimary.toArgb()
                                )
                                journeyMarkers[markerKey] = BitmapDescriptorFactory.fromBitmap(bitmap)
                            }
                        }
                    } else {
                        // Global Mode: Use Thumbnails
                        if (!globalMarkers.containsKey(group.bucketKey)) {
                            launch {
                                val bitmap = generateThumbnailMarker(context, imageLoader, group.photos.first().uri, group.photos.size)
                                if (bitmap != null) {
                                    globalMarkers[group.bucketKey] = BitmapDescriptorFactory.fromBitmap(bitmap)
                                }
                            }
                        }
                    }
                } else if (isVisible && !clusterIcons.containsKey(cluster.totalPhotos)) {
                    launch {
                        val bitmap = generateClusterBitmap(cluster.totalPhotos)
                        clusterIcons[cluster.totalPhotos] = BitmapDescriptorFactory.fromBitmap(bitmap)
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
                        Text(if (dateFilter != null) dateFilter else stringResource(R.string.map_screen_title))
                        if (dateFilter != null) {
                            Text(
                                text = stringResource(R.string.path_stats, groups.size, photos.size),
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
                onMapClick = { 
                    selectedGroup = null
                    selectedStopIndex = -1
                }
            ) {
                // Improvement 3: Segmented Polyline for Flow
                if (dateFilter != null && pathPoints.size >= 2) {
                    for (i in 0 until pathPoints.size - 1) {
                        val fraction = i.toFloat() / (pathPoints.size - 1)
                        val nextFraction = (i + 1).toFloat() / (pathPoints.size - 1)
                        
                        val segmentColor = lerp(startPathColor, endPathColor, fraction)
                        
                        // Outline
                        Polyline(
                            points = listOf(pathPoints[i], pathPoints[i+1]),
                            color = ComposeColor.White,
                            width = 16f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap()
                        )
                        // Main segment
                        Polyline(
                            points = listOf(pathPoints[i], pathPoints[i+1]),
                            color = segmentColor,
                            width = 12f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap()
                        )

                        // Direction arrow (Requirement 3)
                        val start = pathPoints[i]
                        val end = pathPoints[i+1]
                        val midPoint = LatLng((start.latitude + end.latitude) / 2, (start.longitude + end.longitude) / 2)
                        val heading = bearingBetween(start, end)
                        
                        arrowIcon?.let { icon ->
                            Marker(
                                state = MarkerState(midPoint),
                                rotation = heading.toFloat(),
                                icon = icon,
                                anchor = Offset(0.5f, 0.5f),
                                flat = true,
                                onClick = { false }
                            )
                        }
                    }
                }

                clusters.forEach { cluster ->
                    if (currentZoom >= CLUSTERING_THRESHOLD) {
                        val group = cluster.groups.first()
                        val order = groups.indexOf(group)
                        val markerIcon = if (dateFilter != null) {
                            journeyMarkers["journey_${order}_${if (selectedStopIndex == order) "S" else "N"}"]
                        } else {
                            globalMarkers[group.bucketKey]
                        }
                        
                        Marker(
                            state = rememberUpdatedMarkerState(cluster.position),
                            title = if (dateFilter != null) "#${order + 1}" else group.dateKey,
                            icon = markerIcon,
                            onClick = {
                                selectedGroup = group
                                if (dateFilter != null) selectedStopIndex = order
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
                                        CameraUpdateFactory.newLatLngZoom(cluster.position, currentZoom + 2f)
                                    )
                                }
                                true
                            }
                        )
                    }
                }
            }

            // Path Info Header (Requirement 5)
            if (dateFilter != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (groups.size < 2) stringResource(R.string.insufficient_data_for_path) else stringResource(R.string.estimated_path),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (groups.size >= 2) {
                            Text(
                                text = "실제 경로가 아닌 사진 촬영 위치 연결입니다.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Timeline 연동 (Improvement 2 & 4)
            if (dateFilter != null && groups.isNotEmpty()) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                    TimelineCardList(
                        groups = groups,
                        selectedIndex = selectedStopIndex,
                        onItemClick = { index, group ->
                            selectedStopIndex = index
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(group.position, 16f)
                                )
                                selectedGroup = group
                            }
                        }
                    )
                }
            }

            selectedGroup?.let { group ->
                ModalBottomSheet(
                    onDismissRequest = { 
                        selectedGroup = null 
                        if (dateFilter != null) selectedStopIndex = -1
                    },
                    sheetState = sheetState
                ) {
                    BottomSheetContent(
                        group = group,
                        dateFilter = dateFilter,
                        order = if (dateFilter != null) groups.indexOf(group) + 1 else null,
                        onOpenBucket = { bucketKey ->
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onOpenBucket(bucketKey)
                                selectedGroup = null
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineCardList(
    groups: List<PhotoMapGroup>,
    selectedIndex: Int,
    onItemClick: (Int, PhotoMapGroup) -> Unit
) {
    val listState = rememberLazyListState()
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(groups) { index, group ->
            TimelineCard(
                group = group,
                order = index + 1,
                timeLabel = timeFormatter.format(Date(group.firstTakenAt)),
                isSelected = index == selectedIndex,
                onClick = { onItemClick(index, group) }
            )
        }
    }
}

@Composable
private fun TimelineCard(
    group: PhotoMapGroup,
    order: Int,
    timeLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val elevation by animateFloatAsState(if (isSelected) 12f else 4f, label = "elevation")
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else ComposeColor.Transparent

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(220.dp)
            .height(110.dp)
            .border(2.dp, borderColor, MaterialTheme.shapes.medium),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.TopStart) {
                AsyncImage(
                    model = Uri.parse(group.photos.first().uri),
                    contentDescription = null,
                    modifier = Modifier.size(70.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text("#$order", style = MaterialTheme.typography.labelSmall, color = ComposeColor.White, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = timeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.timeline_item_desc, group.photos.size, ""),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BottomSheetContent(
    group: PhotoMapGroup,
    dateFilter: String?,
    order: Int?,
    onOpenBucket: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = Uri.parse(group.photos.first().uri),
                contentDescription = null,
                modifier = Modifier.size(80.dp).clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (order != null) "#$order - ${group.dateKey}" else group.dateKey,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.photo_count, group.photos.size),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Button(onClick = { onOpenBucket(group.bucketKey) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_photo_list))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.photos) { photo ->
                AsyncImage(
                    model = Uri.parse(photo.uri),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp).clip(MaterialTheme.shapes.small).clickable {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(photo.uri), "image/*")
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        context.startActivity(intent)
                    },
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

private fun generateJourneyDotBitmap(
    order: Int,
    isSelected: Boolean,
    primaryColor: Int,
    onPrimaryColor: Int
): Bitmap {
    val size = if (isSelected) 100 else 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true }

    // Outer Glow/Border for selection
    if (isSelected) {
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    }

    // Main Circle
    paint.color = primaryColor
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - (if (isSelected) 6 else 0), paint)

    // Text
    paint.color = onPrimaryColor
    paint.textSize = if (isSelected) 40f else 32f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    val text = order.toString()
    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    canvas.drawText(text, size / 2f, size / 2f + bounds.height() / 2f, paint)

    return bitmap
}

private fun bearingBetween(start: LatLng, end: LatLng): Double {
    val lat1 = Math.toRadians(start.latitude)
    val lon1 = Math.toRadians(start.longitude)
    val lat2 = Math.toRadians(end.latitude)
    val lon2 = Math.toRadians(end.longitude)

    val dLon = lon2 - lon1
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
}

private fun generateArrowBitmap(color: Int): Bitmap {
    val size = 30
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        isAntiAlias = true
        this.color = color
        style = Paint.Style.FILL
    }
    val path = Path().apply {
        moveTo(size / 2f, 0f)
        lineTo(size.toFloat(), size.toFloat())
        lineTo(size / 2f, size * 0.8f)
        lineTo(0f, size.toFloat())
        close()
    }
    canvas.drawPath(path, paint)
    return bitmap
}

private suspend fun generateThumbnailMarker(
    context: Context,
    loader: ImageLoader,
    uri: String,
    count: Int
): Bitmap? = withContext(Dispatchers.IO) {
    val size = 140
    val request = ImageRequest.Builder(context).data(uri).size(size, size).allowHardware(false).build()
    val result = loader.execute(request)
    if (result !is SuccessResult) return@withContext null
    val photo = result.drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)
    val output = Bitmap.createBitmap(size + 20, size + 20, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply { isAntiAlias = true }

    paint.color = AndroidColor.WHITE
    canvas.drawCircle((size + 20) / 2f, (size + 20) / 2f, (size + 20) / 2f, paint)
    
    val path = Path().apply { addCircle((size + 20) / 2f, (size + 20) / 2f, size / 2f, Path.Direction.CCW) }
    canvas.save()
    canvas.clipPath(path)
    canvas.drawBitmap(photo, null, RectF(10f, 10f, size + 10f, size + 10f), paint)
    canvas.restore()

    if (count > 1) {
        paint.color = AndroidColor.RED
        canvas.drawCircle(size + 5f, 25f, 25f, paint)
        paint.color = AndroidColor.WHITE
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (count > 99) "99+" else count.toString(), size + 5f, 33f, paint)
    }
    output
}

private suspend fun generateClusterBitmap(count: Int): Bitmap = withContext(Dispatchers.IO) {
    val size = 100
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true }
    paint.color = AndroidColor.parseColor("#3F51B5")
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)
    paint.color = AndroidColor.parseColor("#3F51B5")
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 10, paint)
    paint.color = AndroidColor.WHITE
    paint.textSize = 32f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    val text = if (count > 999) "999+" else count.toString()
    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    canvas.drawText(text, size / 2f, size / 2f + bounds.height() / 2f, paint)
    bitmap
}
