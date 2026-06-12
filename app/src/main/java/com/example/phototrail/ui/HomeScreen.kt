package com.example.phototrail.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.phototrail.R

private enum class PhotoPermissionStatus {
    Required,
    FullAccess,
    LimitedAccess,
    Denied
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PhotoViewModel,
    onNavigateToMap: () -> Unit,
    onNavigateToDates: () -> Unit,
    onNavigateToNoLocation: () -> Unit,
    onNavigateToTrips: () -> Unit
) {
    val context = LocalContext.current
    val indexState by viewModel.indexUiState.collectAsState()
    val photos by viewModel.allPhotos.collectAsState()
    var permissionStatus by remember {
        mutableStateOf(photoPermissionStatus(context, denied = false))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionStatus = photoPermissionStatus(context, denied = true)
    }

    // 자동 동기화: 권한이 허용되어 있으면 앱 시작 시(또는 권한 획득 시) 자동으로 스캔 시작
    LaunchedEffect(permissionStatus.hasPhotoAccess) {
        if (permissionStatus.hasPhotoAccess) {
            viewModel.indexPhotos(isAutoSync = true)
        }
    }

    val currentStats = remember(photos) {
        HomeStats(
            totalCount = photos.size,
            locationCount = photos.count { it.hasLocation },
            noLocationCount = photos.count { !it.hasLocation },
            dateGroupCount = photos.map { it.dateKey }.distinct().size,
            locationGroupCount = photos.filter { it.hasLocation }
                .map { it.bucketKey }
                .distinct()
                .size
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyLarge
            )

            PhotoAccessCard(
                permissionStatus = permissionStatus,
                onRequestPermission = {
                    permissionLauncher.launch(photoPermissions())
                }
            )

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = viewModel::indexPhotos,
                        enabled = permissionStatus.hasPhotoAccess && !indexState.isIndexing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.scan_photos))
                    }

                    if (indexState.isIndexing) {
                        val progress = if (indexState.total > 0) {
                            indexState.processed.toFloat() / indexState.total
                        } else {
                            0f
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            stringResource(
                                R.string.indexing_progress,
                                indexState.processed,
                                indexState.total
                            )
                        )
                    }

                    indexState.errorMessage?.let {
                        Text(
                            text = stringResource(R.string.indexing_failed, it),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    indexState.stats?.let { stats ->
                        Text(
                            text = stringResource(
                                R.string.sync_stats_summary,
                                stats.newCount,
                                stats.updatedCount,
                                stats.deletedCount,
                                stats.unchangedCount,
                                stats.mediaStoreCount
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Text(
                        text = stringResource(
                            R.string.index_stats,
                            currentStats.totalCount,
                            currentStats.locationCount,
                            currentStats.noLocationCount,
                            currentStats.dateGroupCount,
                            currentStats.locationGroupCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            MenuCard(
                text = stringResource(R.string.view_trips),
                onClick = onNavigateToTrips
            )
            MenuCard(
                text = stringResource(R.string.view_on_map),
                onClick = onNavigateToMap
            )
            MenuCard(
                text = stringResource(R.string.view_by_date),
                onClick = onNavigateToDates
            )
            MenuCard(
                text = stringResource(R.string.view_no_location),
                onClick = onNavigateToNoLocation
            )

            Spacer(modifier = Modifier.heightIn(min = 4.dp))
            Text(
                text = stringResource(R.string.footer_text),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun PhotoAccessCard(
    permissionStatus: PhotoPermissionStatus,
    onRequestPermission: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (permissionStatus) {
                    PhotoPermissionStatus.Required ->
                        stringResource(R.string.photo_permission_required)
                    PhotoPermissionStatus.FullAccess ->
                        stringResource(R.string.photo_permission_granted)
                    PhotoPermissionStatus.LimitedAccess ->
                        stringResource(R.string.photo_permission_limited)
                    PhotoPermissionStatus.Denied ->
                        stringResource(R.string.photo_permission_denied)
                },
                fontWeight = FontWeight.SemiBold
            )
            if (!permissionStatus.hasPhotoAccess) {
                Button(onClick = onRequestPermission) {
                    Text(stringResource(R.string.request_photo_permission))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuCard(text: String, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private data class HomeStats(
    val totalCount: Int,
    val locationCount: Int,
    val noLocationCount: Int,
    val dateGroupCount: Int,
    val locationGroupCount: Int
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
