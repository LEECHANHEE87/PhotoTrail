package com.example.phototrail.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.phototrail.R
import com.example.phototrail.data.TripAlbumEntity
import com.example.phototrail.ui.theme.PhotoTrailSpacing

private enum class PhotoPermissionStatus {
    Required, FullAccess, LimitedAccess, Denied
}

@Composable
fun HomeScreen(
    viewModel: PhotoViewModel,
    onNavigateToMap: () -> Unit,
    onNavigateToDates: () -> Unit,
    onNavigateToNoLocation: () -> Unit,
    onNavigateToTrips: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val context = LocalContext.current
    val indexState by viewModel.indexUiState.collectAsState()
    val photos by viewModel.allPhotos.collectAsState()
    val tripAlbums by viewModel.tripAlbums.collectAsState()
    
    var permissionStatus by remember {
        mutableStateOf(photoPermissionStatus(context, denied = false))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionStatus = photoPermissionStatus(context, denied = true)
    }

    LaunchedEffect(permissionStatus.hasPhotoAccess) {
        if (permissionStatus.hasPhotoAccess) {
            viewModel.indexPhotos(isAutoSync = true)
        }
    }

    val stats = remember(photos, tripAlbums) {
        HomeStats(
            totalCount = photos.size,
            locationCount = photos.count { it.hasLocation },
            tripCount = tripAlbums.size
        )
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Brand Area
            BrandHeader(onSearchClick = onNavigateToSearch)

            Column(
                modifier = Modifier
                    .padding(horizontal = PhotoTrailSpacing.ScreenHorizontal)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(PhotoTrailSpacing.SectionSpacing)
            ) {
                // 2. Permission Card (if needed)
                if (!permissionStatus.hasPhotoAccess) {
                    PermissionCard(
                        status = permissionStatus,
                        onRequest = { permissionLauncher.launch(photoPermissions()) }
                    )
                }

                // 3. Stats Summary
                StatsSummaryRow(
                    stats = stats,
                    indexState = indexState,
                    canSync = permissionStatus.hasPhotoAccess,
                    onSyncClick = { viewModel.indexPhotos() }
                )

                // 4. Recent Memory Card
                RecentMemorySection(
                    recentTrip = tripAlbums.firstOrNull(),
                    onOpenTrip = { onNavigateToTrips() }
                )

                // 5. Main Menus
                PhotoTrailSectionTitle(title = stringResource(R.string.view_all))
                
                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MenuCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.view_trips),
                            subtitle = stringResource(R.string.album_count, stats.tripCount),
                            icon = Icons.Default.Star,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = onNavigateToTrips
                        )
                        MenuCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.view_on_map),
                            subtitle = stringResource(R.string.photo_map_subtitle),
                            icon = Icons.Default.LocationOn,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = onNavigateToMap
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MenuCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.view_by_date),
                            subtitle = stringResource(R.string.daily_records_subtitle),
                            icon = Icons.Default.DateRange,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            onClick = onNavigateToDates
                        )
                        MenuCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.view_no_location),
                            subtitle = stringResource(R.string.no_gps_photos_subtitle),
                            icon = Icons.Default.Info,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = onNavigateToNoLocation
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MenuCard(
                            title = stringResource(R.string.view_trips),
                            subtitle = stringResource(R.string.album_count, stats.tripCount),
                            icon = Icons.Default.Star,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = onNavigateToTrips
                        )
                        MenuCard(
                            title = stringResource(R.string.view_on_map),
                            subtitle = stringResource(R.string.photo_map_subtitle),
                            icon = Icons.Default.LocationOn,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = onNavigateToMap
                        )
                        MenuCard(
                            title = stringResource(R.string.view_by_date),
                            subtitle = stringResource(R.string.daily_records_subtitle),
                            icon = Icons.Default.DateRange,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            onClick = onNavigateToDates
                        )
                        MenuCard(
                            title = stringResource(R.string.view_no_location),
                            subtitle = stringResource(R.string.no_gps_photos_subtitle),
                            icon = Icons.Default.Info,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = onNavigateToNoLocation
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.footer_text),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BrandHeader(onSearchClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 32.dp)
            .padding(horizontal = PhotoTrailSpacing.ScreenHorizontal)
    ) {
        IconButton(
            onClick = onSearchClick,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_hint))
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun StatsSummaryRow(
    stats: HomeStats,
    indexState: IndexUiState,
    canSync: Boolean,
    onSyncClick: () -> Unit
) {
    PhotoTrailCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_status),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (indexState.isIndexing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = onSyncClick,
                        modifier = Modifier.size(24.dp),
                        enabled = canSync
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.sync), modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhotoTrailStatChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.List,
                    label = stringResource(R.string.photo_count, stats.totalCount)
                )
                PhotoTrailStatChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocationOn,
                    label = stringResource(R.string.gps_count, stats.locationCount)
                )
                PhotoTrailStatChip(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Star,
                    label = stringResource(R.string.trip_count, stats.tripCount)
                )
            }
            if (indexState.isIndexing) {
                LinearProgressIndicator(
                    progress = { if (indexState.total > 0) indexState.processed.toFloat() / indexState.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            indexState.errorMessage?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.indexing_failed, message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            indexState.stats?.let { syncStats ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        R.string.sync_stats_summary,
                        syncStats.newCount,
                        syncStats.updatedCount,
                        syncStats.deletedCount,
                        syncStats.unchangedCount,
                        syncStats.mediaStoreCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecentMemorySection(
    recentTrip: TripAlbumEntity?,
    onOpenTrip: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PhotoTrailSectionTitle(title = stringResource(R.string.recent_memory))
        if (recentTrip != null) {
            PhotoTrailCard(onClick = onOpenTrip) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    AsyncImage(
                        model = recentTrip.representativePhotoUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = recentTrip.displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(
                                R.string.recent_trip_summary,
                                recentTrip.startDateKey,
                                recentTrip.photoCount
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        } else {
            PhotoTrailCard {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.no_trips_yet), color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    status: PhotoPermissionStatus,
    onRequest: () -> Unit
) {
    PhotoTrailCard {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.photo_permission_required),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.photo_permission_description),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(Modifier.height(12.dp))
            PhotoTrailPrimaryButton(
                text = stringResource(R.string.request_photo_permission),
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class HomeStats(
    val totalCount: Int,
    val locationCount: Int,
    val tripCount: Int
)

private fun photoPermissions(): Array<String> =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun photoPermissionStatus(
    context: Context,
    denied: Boolean
): PhotoPermissionStatus =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ->
            PhotoPermissionStatus.FullAccess
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ->
            PhotoPermissionStatus.LimitedAccess
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ->
            PhotoPermissionStatus.FullAccess
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
            context.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ->
            PhotoPermissionStatus.FullAccess
        denied -> PhotoPermissionStatus.Denied
        else -> PhotoPermissionStatus.Required
    }

private val PhotoPermissionStatus.hasPhotoAccess: Boolean
    get() = this == PhotoPermissionStatus.FullAccess ||
        this == PhotoPermissionStatus.LimitedAccess

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
