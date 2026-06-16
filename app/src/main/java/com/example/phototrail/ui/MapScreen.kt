package com.example.phototrail.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.lerp
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
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
            if (coordinates.isEmpty()) null else {
                PhotoMapGroup(
                    bucketKey = bucketKey,
                    dateKey = items.first().dateKey,
                    position = LatLng(coordinates.map { it.first }.average(), coordinates.map { it.second }.average()),
                    photos = items,
                    firstTakenAt = items.minOf { it.takenAt ?: Long.MAX_VALUE }
                )
            }
        }.sortedBy { it.firstTakenAt }
    }

    val pathPoints = remember(groups, dateFilter) {
        if (dateFilter != null && groups.size >= 2) groups.map { it.position } else emptyList()
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
                val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasFineLocation) {
                    try {
                        val location = fusedLocationClient.lastLocation.await()
                        if (location != null) targetLatLng = LatLng(location.latitude, location.longitude)
                    } catch (e: Exception) {}
                }
            }

            if (targetBounds != null) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(targetBounds, 200))
                initialCameraSet = true
            } else if (targetLatLng != null) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(targetLatLng, zoomLevel))
                initialCameraSet = true
            }
        }
    }

    val currentZoom = cameraPositionState.position.zoom
    val clusters = remember(groups, currentZoom, dateFilter) {
        if (dateFilter != null || currentZoom >= CLUSTERING_THRESHOLD) {
            groups.map { MapCluster(it.bucketKey, it.position, it.photos.size, listOf(it)) }
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
                    gridId,
                    LatLng(clusterGroups.map { it.position.latitude }.average(), clusterGroups.map { it.position.longitude }.average()),
                    clusterGroups.sumOf { it.photos.size },
                    clusterGroups
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
                        val order = groups.indexOf(group)
                        val markerKey = "journey_${order}_${if (selectedStopIndex == order) "S" else "N"}"
                        if (!journeyMarkers.containsKey(markerKey)) {
                            launch {
                                val bitmap = generateJourneyDotBitmap(order + 1, selectedStopIndex == order, colorScheme.primary.toArgb(), colorScheme.onPrimary.toArgb())
                                journeyMarkers[markerKey] = BitmapDescriptorFactory.fromBitmap(bitmap)
                            }
                        }
                    } else {
                        if (!globalMarkers.containsKey(group.bucketKey)) {
                            launch {
                                val bitmap = generateThumbnailMarker(context, imageLoader, group.photos.first().uri, group.photos.size, colorScheme.primary.toArgb())
                                if (bitmap != null) globalMarkers[group.bucketKey] = BitmapDescriptorFactory.fromBitmap(bitmap)
                            }
                        }
                    }
                } else if (isVisible && !clusterIcons.containsKey(cluster.totalPhotos)) {
                    launch {
                        val bitmap = generateClusterBitmap(cluster.totalPhotos, colorScheme.primary.toArgb())
                        clusterIcons[cluster.totalPhotos] = BitmapDescriptorFactory.fromBitmap(bitmap)
                    }
                }
            }
        }
    }

    Scaffold { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
                onMapClick = { 
                    selectedGroup = null
                    selectedStopIndex = -1
                }
            ) {
                if (dateFilter != null && pathPoints.size >= 2) {
                    for (i in 0 until pathPoints.size - 1) {
                        val fraction = i.toFloat() / (pathPoints.size - 1)
                        val segmentColor = lerp(startPathColor, endPathColor, fraction)
                        Polyline(points = listOf(pathPoints[i], pathPoints[i+1]), color = ComposeColor.White, width = 14f, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())
                        Polyline(points = listOf(pathPoints[i], pathPoints[i+1]), color = segmentColor, width = 10f, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())

                        val midPoint = LatLng((pathPoints[i].latitude + pathPoints[i+1].latitude) / 2, (pathPoints[i].longitude + pathPoints[i+1].longitude) / 2)
                        arrowIcon?.let { icon ->
                            Marker(state = MarkerState(midPoint), rotation = bearingBetween(pathPoints[i], pathPoints[i+1]).toFloat(), icon = icon, anchor = Offset(0.5f, 0.5f), flat = true, onClick = { false })
                        }
                    }
                }

                clusters.forEach { cluster ->
                    if (currentZoom >= CLUSTERING_THRESHOLD) {
                        val group = cluster.groups.first()
                        val order = groups.indexOf(group)
                        val markerIcon = if (dateFilter != null) journeyMarkers["journey_${order}_${if (selectedStopIndex == order) "S" else "N"}"] else globalMarkers[group.bucketKey]
                        Marker(
                            state = rememberUpdatedMarkerState(cluster.position),
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
                                scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(cluster.position, currentZoom + 2f)) }
                                true
                            }
                        )
                    }
                }
            }

            // 1. Floating Header
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = dateFilter ?: stringResource(R.string.photo_map_subtitle),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PhotoTrailStatChip(icon = Icons.AutoMirrored.Filled.List, label = stringResource(R.string.photo_count, photos.size), containerColor = ComposeColor.Transparent)
                                PhotoTrailStatChip(icon = Icons.Default.Place, label = stringResource(R.string.place_count, groups.size), containerColor = ComposeColor.Transparent)
                            }
                        }
                    }
                }
                
                if (dateFilter != null && groups.size >= 2) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = stringResource(R.string.estimated_path),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 2. Timeline (Journey Mode)
            if (dateFilter != null && groups.isNotEmpty()) {
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                    TimelineCardList(
                        groups = groups,
                        selectedIndex = selectedStopIndex,
                        onItemClick = { index, group ->
                            selectedStopIndex = index
                            scope.launch {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(group.position, 16f))
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
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
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
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
    PhotoTrailCard(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(90.dp)
            .border(if (isSelected) 2.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else ComposeColor.Transparent, MaterialTheme.shapes.medium)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.TopStart) {
                AsyncImage(
                    model = Uri.parse(group.photos.first().uri),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text("#$order", style = MaterialTheme.typography.labelSmall, color = ComposeColor.White, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = timeLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.photo_count, group.photos.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = Uri.parse(group.photos.first().uri),
                contentDescription = null,
                modifier = Modifier.size(100.dp).clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (order != null) "#$order · ${group.dateKey}" else group.dateKey,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                PhotoTrailStatChip(icon = Icons.AutoMirrored.Filled.List, label = stringResource(R.string.photo_count, group.photos.size), containerColor = MaterialTheme.colorScheme.secondaryContainer)
            }
        }
        
        PhotoTrailPrimaryButton(
            text = stringResource(R.string.open_photo_list),
            onClick = { onOpenBucket(group.bucketKey) },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Search
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.photos) { photo ->
                AsyncImage(
                    model = Uri.parse(photo.uri),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp).clip(MaterialTheme.shapes.small).clickable {
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

private fun generateJourneyDotBitmap(order: Int, isSelected: Boolean, primaryColor: Int, onPrimaryColor: Int): Bitmap {
    val size = if (isSelected) 100 else 80
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true }
    if (isSelected) {
        paint.color = AndroidColor.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    }
    paint.color = primaryColor
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - (if (isSelected) 6 else 0), paint)
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
    val lat1 = Math.toRadians(start.latitude); val lon1 = Math.toRadians(start.longitude)
    val lat2 = Math.toRadians(end.latitude); val lon2 = Math.toRadians(end.longitude)
    val dLon = lon2 - lon1
    val y = Math.sin(dLon) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
    return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
}

private fun generateArrowBitmap(color: Int): Bitmap {
    val size = 30
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true; this.color = color; style = Paint.Style.FILL }
    val path = Path().apply { moveTo(size / 2f, 0f); lineTo(size.toFloat(), size.toFloat()); lineTo(size / 2f, size * 0.8f); lineTo(0f, size.toFloat()); close() }
    canvas.drawPath(path, paint)
    return bitmap
}

private suspend fun generateThumbnailMarker(context: Context, loader: ImageLoader, uri: String, count: Int, primaryColor: Int): Bitmap? = withContext(Dispatchers.IO) {
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
    canvas.save(); canvas.clipPath(path); canvas.drawBitmap(photo, null, RectF(10f, 10f, size + 10f, size + 10f), paint); canvas.restore()
    if (count > 1) {
        paint.color = primaryColor
        canvas.drawCircle(size + 5f, 25f, 25f, paint)
        paint.color = AndroidColor.WHITE; paint.textSize = 24f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (count > 99) "99+" else count.toString(), size + 5f, 33f, paint)
    }
    output
}

private suspend fun generateClusterBitmap(count: Int, primaryColor: Int): Bitmap = withContext(Dispatchers.IO) {
    val size = 100
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = true }
    paint.color = primaryColor
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, paint)
    paint.color = primaryColor
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 10, paint)
    paint.color = AndroidColor.WHITE; paint.textSize = 32f; paint.textAlign = Paint.Align.CENTER; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    val text = if (count > 999) "999+" else count.toString()
    val bounds = Rect(); paint.getTextBounds(text, 0, text.length, bounds)
    canvas.drawText(text, size / 2f, size / 2f + bounds.height() / 2f, paint)
    bitmap
}
