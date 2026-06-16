package com.example.phototrail.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.phototrail.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoViewerScreen(
    viewModel: PhotoViewModel,
    initialIndex: Int,
    onBackClick: () -> Unit
) {
    val photos by viewModel.viewerPhotos.collectAsState()
    val context = LocalContext.current

    if (photos.isEmpty()) {
        PhotoTrailEmptyState(
            icon = Icons.Default.Info,
            title = stringResource(R.string.no_photos_title),
            description = stringResource(R.string.no_photos_in_viewer)
        )
        return
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    val currentPhoto = photos.getOrNull(pagerState.currentPage)
    var isZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
    }

    val dateFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            ViewerTopControls(
                pageLabel = stringResource(
                    R.string.photo_viewer_page_count,
                    pagerState.currentPage + 1,
                    photos.size
                ),
                dateLabel = currentPhoto?.takenAt?.let { timestamp ->
                    dateFormatter.format(Date(timestamp))
                },
                onBackClick = onBackClick,
                onOpenExternal = {
                    currentPhoto?.let { photo ->
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
                    }
                }
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            pageSpacing = 16.dp,
            userScrollEnabled = !isZoomed
        ) { pageIndex ->
            val photo = photos[pageIndex]
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var size by remember { mutableStateOf(IntSize.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size = it }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val isPinch = event.changes.size > 1

                                if (scale > 1f || zoomChange != 1f || isPinch) {
                                    scale = (scale * zoomChange).coerceIn(1f, 5f)

                                    if (scale > 1f) {
                                        val centroid = event.calculateCentroid(useCurrent = false)
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val centroidFromCenter = centroid - center
                                        offset = (offset * zoomChange) + panChange + (centroidFromCenter * (1 - zoomChange))

                                        val maxX = (scale - 1) * size.width / 2f
                                        val maxY = (scale - 1) * size.height / 2f
                                        offset = Offset(
                                            x = offset.x.coerceIn(-maxX, maxX),
                                            y = offset.y.coerceIn(-maxY, maxY)
                                        )

                                        if (pageIndex == pagerState.currentPage) {
                                            isZoomed = true
                                        }
                                    } else {
                                        scale = 1f
                                        offset = Offset.Zero
                                        if (pageIndex == pagerState.currentPage) {
                                            isZoomed = false
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = Uri.parse(photo.uri),
                    contentDescription = photo.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        PhotoTrailStatusState(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.photo_loading_title),
                            description = stringResource(R.string.photo_loading_description),
                            isLoading = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    error = {
                        PhotoTrailStatusState(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.photo_load_failed_title),
                            description = stringResource(R.string.photo_load_failed_description),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ViewerTopControls(
    pageLabel: String,
    dateLabel: String?,
    onBackClick: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f),
                contentColor = Color.White
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.44f),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = pageLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (dateLabel != null) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.76f)
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.48f),
                contentColor = Color.White
            ) {
                IconButton(onClick = onOpenExternal) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = stringResource(R.string.open_in_external_gallery)
                    )
                }
            }
        }
    }
}
