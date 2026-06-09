package com.example.phototrail.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.phototrail.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class PhotoPermissionStatus {
    Required,
    Granted,
    Denied
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val photoPermissions = photoPermissions()
    var permissionStatus by remember {
        mutableStateOf(
            if (hasPhotoPermission(context)) {
                PhotoPermissionStatus.Granted
            } else {
                PhotoPermissionStatus.Required
            }
        )
    }
    var imageCount by remember { mutableStateOf<Int?>(null) }
    var isLoadingImages by remember { mutableStateOf(false) }
    var imageQueryFailed by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionStatus = if (hasPhotoPermission(context)) {
            PhotoPermissionStatus.Granted
        } else {
            PhotoPermissionStatus.Denied
        }
        imageCount = null
        imageQueryFailed = false
    }

    LaunchedEffect(permissionStatus) {
        if (permissionStatus == PhotoPermissionStatus.Granted) {
            isLoadingImages = true
            imageQueryFailed = false
            imageCount = runCatching { queryImageCount(context) }
                .onFailure { imageQueryFailed = true }
                .getOrNull()
            isLoadingImages = false
        }
    }

    val onButtonClick = {
        Toast.makeText(context, context.getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PhotoAccessCard(
                    permissionStatus = permissionStatus,
                    imageCount = imageCount,
                    isLoadingImages = isLoadingImages,
                    imageQueryFailed = imageQueryFailed,
                    onRequestPermission = {
                        permissionLauncher.launch(photoPermissions)
                    }
                )
                MenuCard(
                    text = stringResource(R.string.view_on_map),
                    onClick = onButtonClick
                )
                MenuCard(
                    text = stringResource(R.string.view_by_date),
                    onClick = onButtonClick
                )
                MenuCard(
                    text = stringResource(R.string.view_no_location),
                    onClick = onButtonClick
                )
            }

            Text(
                text = stringResource(R.string.footer_text),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun PhotoAccessCard(
    permissionStatus: PhotoPermissionStatus,
    imageCount: Int?,
    isLoadingImages: Boolean,
    imageQueryFailed: Boolean,
    onRequestPermission: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
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
                    PhotoPermissionStatus.Granted ->
                        stringResource(R.string.photo_permission_granted)
                    PhotoPermissionStatus.Denied ->
                        stringResource(R.string.photo_permission_denied)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (permissionStatus == PhotoPermissionStatus.Granted) {
                Text(
                    text = when {
                        isLoadingImages -> stringResource(R.string.photo_count_loading)
                        imageQueryFailed -> stringResource(R.string.photo_count_failed)
                        imageCount != null -> stringResource(
                            R.string.photo_count_found,
                            imageCount
                        )
                        else -> stringResource(R.string.photo_permission_granted)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
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
            .heightIn(min = 80.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun photoPermissions(): Array<String> =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasPhotoPermission(context: Context): Boolean =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES) ||
                context.hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES)
        else -> context.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private suspend fun queryImageCount(context: Context): Int = withContext(Dispatchers.IO) {
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media._ID),
        null,
        null,
        null
    )?.use { cursor ->
        cursor.count
    } ?: 0
}
