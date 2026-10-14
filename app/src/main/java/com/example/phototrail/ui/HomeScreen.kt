package com.example.phototrail.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    videoViewModel: VideoViewModel,
    onNavigateToCamera: () -> Unit,
    onNavigateToMaking: () -> Unit,
    onNavigateToVideos: () -> Unit,
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
            if (context.hasVideoAccess()) videoViewModel.indexVideos()
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
    var showLibraryInfo by remember { mutableStateOf(false) }

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
            BrandHeader(
                indexState = indexState,
                canSync = permissionStatus.hasPhotoAccess,
                onSyncClick = {
                    viewModel.indexPhotos()
                    if (context.hasVideoAccess()) videoViewModel.indexVideos()
                },
                onLibraryInfoClick = { showLibraryInfo = true },
                onSearchClick = onNavigateToSearch
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = PhotoTrailSpacing.ScreenHorizontal)
                    .padding(top = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                CaptureActionPanel(
                    onCameraClick = onNavigateToCamera,
                    onVideosClick = onNavigateToVideos,
                    onMakingClick = onNavigateToMaking
                )

                // 2. Permission Card (if needed)
                if (!permissionStatus.hasPhotoAccess) {
                    PermissionCard(
                        status = permissionStatus,
                        onRequest = { permissionLauncher.launch(photoPermissions()) }
                    )
                }

                // 3. Recent Memory Card
                RecentMemorySection(
                    recentTrip = tripAlbums.firstOrNull(),
                    onOpenTrip = { onNavigateToTrips() }
                )

                // 4. Main Menus
                PhotoTrailSectionTitle(title = stringResource(R.string.view_all))
                
                if (isWideScreen) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MenuCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.view_trips),
                            subtitle = stringResource(R.string.album_count, stats.tripCount),
                            imageRes = R.drawable.menu_trips_painting,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = onNavigateToTrips
                        )
                        MenuCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.view_on_map),
                            subtitle = stringResource(R.string.photo_map_subtitle),
                            imageRes = R.drawable.menu_map_painting,
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
                            imageRes = R.drawable.menu_calendar_painting,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            onClick = onNavigateToDates
                        )
                        MenuCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.view_no_location),
                            subtitle = stringResource(R.string.no_gps_photos_subtitle),
                            imageRes = R.drawable.menu_no_gps_painting,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            onClick = onNavigateToNoLocation
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MenuCard(
                                modifier = Modifier.weight(1f),
                                title = stringResource(R.string.view_trips),
                                subtitle = stringResource(R.string.album_count, stats.tripCount),
                                imageRes = R.drawable.menu_trips_painting,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                onClick = onNavigateToTrips
                            )
                            MenuCard(
                                modifier = Modifier.weight(1f),
                                title = stringResource(R.string.view_on_map),
                                subtitle = stringResource(R.string.photo_map_subtitle),
                                imageRes = R.drawable.menu_map_painting,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                onClick = onNavigateToMap
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MenuCard(
                                modifier = Modifier.weight(1f),
                                title = stringResource(R.string.view_by_date),
                                subtitle = stringResource(R.string.daily_records_subtitle),
                                imageRes = R.drawable.menu_calendar_painting,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                onClick = onNavigateToDates
                            )
                            MenuCard(
                                modifier = Modifier.weight(1f),
                                title = stringResource(R.string.view_no_location),
                                subtitle = stringResource(R.string.no_gps_photos_subtitle),
                                imageRes = R.drawable.menu_no_gps_painting,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = onNavigateToNoLocation
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }

        if (showLibraryInfo) {
            LibraryInfoDialog(
                stats = stats,
                indexState = indexState,
                onDismiss = { showLibraryInfo = false }
            )
        }
    }
}

@Composable
private fun CaptureActionPanel(
    onCameraClick: () -> Unit,
    onVideosClick: () -> Unit,
    onMakingClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.capture_studio),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.capture_studio_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_home_camera),
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp).size(18.dp)
                )
            }
        }

        ElevatedCard(
            onClick = onCameraClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_home_camera),
                            contentDescription = null,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.open_camera),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.open_camera_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CaptureActionTile(
                title = stringResource(R.string.video_library),
                subtitle = stringResource(R.string.video_library_subtitle),
                icon = painterResource(R.drawable.ic_home_video_library),
                onClick = onVideosClick,
                modifier = Modifier.weight(1f)
            )
            CaptureActionTile(
                title = stringResource(R.string.making_title),
                subtitle = stringResource(R.string.home_making_subtitle),
                icon = painterResource(R.drawable.ic_home_making),
                onClick = onMakingClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CaptureActionTile(
    title: String,
    subtitle: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(116.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painter = icon, contentDescription = null, modifier = Modifier.size(21.dp))
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BrandHeader(
    indexState: IndexUiState,
    canSync: Boolean,
    onSyncClick: () -> Unit,
    onLibraryInfoClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
            .background(MaterialTheme.colorScheme.primary)
    ) {
        Image(
            painter = painterResource(R.drawable.atlas_antique_album),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99331F10),
                            Color(0x77331F10),
                            Color(0xCC2B1A0D)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PhotoTrailSpacing.ScreenHorizontal)
                .padding(top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.journey_atlas_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                    )
                }

                HeaderIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.sync),
                    enabled = canSync && !indexState.isIndexing,
                    onClick = onSyncClick
                )
                Spacer(Modifier.width(8.dp))
                HeaderIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.library_info),
                    onClick = onLibraryInfoClick
                )
                Spacer(Modifier.width(8.dp))
                HeaderIconButton(
                    icon = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_hint),
                    onClick = onSearchClick
                )
            }

            Column {
                Text(
                    text = stringResource(R.string.home_atlas_headline),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (indexState.isIndexing) {
            LinearProgressIndicator(
                progress = { if (indexState.total > 0) indexState.processed.toFloat() / indexState.total else 0f },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = if (enabled) 0.14f else 0.08f),
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.36f)
            )
        }
    }
}

@Composable
private fun LibraryInfoDialog(
    stats: HomeStats,
    indexState: IndexUiState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.library_info),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryInfoMetric(
                        modifier = Modifier.weight(1f),
                        value = stats.totalCount.toString(),
                        label = stringResource(R.string.filter_photos)
                    )
                    LibraryInfoMetric(
                        modifier = Modifier.weight(1f),
                        value = stats.locationCount.toString(),
                        label = stringResource(R.string.gps_count_short)
                    )
                    LibraryInfoMetric(
                        modifier = Modifier.weight(1f),
                        value = stats.tripCount.toString(),
                        label = stringResource(R.string.filter_trips)
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = stringResource(R.string.last_sync_result),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                indexState.errorMessage?.let { message ->
                    Text(
                        text = stringResource(R.string.indexing_failed, message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } ?: indexState.stats?.let { syncStats ->
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
                } ?: Text(
                    text = stringResource(R.string.no_sync_result),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun LibraryInfoMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RecentMemorySection(
    recentTrip: TripAlbumEntity?,
    onOpenTrip: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            PhotoTrailSectionTitle(title = stringResource(R.string.recent_memory))
            Text(
                text = stringResource(R.string.view_trips),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (recentTrip != null) {
            PhotoTrailCard(onClick = onOpenTrip) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(216.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            AsyncImage(
                                model = recentTrip.finalRepresentativeUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x332C1A0C))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color(0x992C1A0C)
                                            )
                                        )
                                    )
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.trip_stats, recentTrip.photoCount, recentTrip.placeGroupCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recentTrip.displayTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(
                                    R.string.recent_trip_summary,
                                    recentTrip.startDateKey,
                                    recentTrip.photoCount
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            PhotoTrailCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.no_trips_yet),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.no_trip_albums_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.photo_permission_required),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.photo_permission_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuCard(
    title: String,
    subtitle: String,
    imageRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    PhotoTrailCard(
        onClick = onClick,
        modifier = modifier.height(176.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(98.dp),
                shape = RoundedCornerShape(8.dp),
                color = color,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
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
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
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

private fun Context.hasVideoAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
} else {
    hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
}
