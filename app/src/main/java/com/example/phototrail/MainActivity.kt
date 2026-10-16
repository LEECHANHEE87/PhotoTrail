package com.example.phototrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import android.util.Log
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapsSdkInitializedCallback
import com.example.phototrail.data.OverrideType
import com.example.phototrail.camera.CameraScreen
import com.example.phototrail.camera.CaptureType
import com.example.phototrail.ui.DateListScreen
import com.example.phototrail.ui.HomeScreen
import com.example.phototrail.ui.MapScreen
import com.example.phototrail.ui.PhotoGridScreen
import com.example.phototrail.ui.PhotoViewModel
import com.example.phototrail.ui.PhotoViewerScreen
import com.example.phototrail.ui.SearchScreen
import com.example.phototrail.ui.TripAlbumScreen
import com.example.phototrail.ui.VideoGridScreen
import com.example.phototrail.ui.VideoPlayerScreen
import com.example.phototrail.ui.VideoViewModel
import com.example.phototrail.ui.MakingTripListScreen
import com.example.phototrail.ui.MakingSetupScreen
import com.example.phototrail.ui.MakingViewModel
import com.example.phototrail.ui.toCameraSettings
import com.example.phototrail.ui.withCameraSettings
import com.example.phototrail.camera.CameraMode
import com.example.phototrail.ui.theme.PhotoTrailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST, object : OnMapsSdkInitializedCallback {
            override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
                when (renderer) {
                    MapsInitializer.Renderer.LATEST -> Log.d("Maps", "The latest version of the renderer is used.")
                    MapsInitializer.Renderer.LEGACY -> Log.d("Maps", "The legacy version of the renderer is used.")
                }
            }
        })
        setContent {
            PhotoTrailTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val photoViewModel: PhotoViewModel = viewModel()
                    val videoViewModel: VideoViewModel = viewModel()
                    val makingViewModel: MakingViewModel = viewModel()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                viewModel = photoViewModel,
                                videoViewModel = videoViewModel,
                                onNavigateToCamera = { navController.navigate("camera") },
                                onNavigateToMaking = { navController.navigate("making") },
                                onNavigateToVideos = { navController.navigate("videos") },
                                onNavigateToMap = { navController.navigate("map") },
                                onNavigateToDates = { navController.navigate("dates") },
                                onNavigateToNoLocation = {
                                    navController.navigate("photos/no-location")
                                },
                                onNavigateToTrips = { navController.navigate("trips") },
                                onNavigateToSearch = { navController.navigate("search") }
                            )
                        }
                        composable("camera") {
                            CameraScreen(
                                onBackClick = { navController.popBackStack() },
                                onMediaCaptured = { captured ->
                                    if (captured.type == CaptureType.PHOTO) {
                                        photoViewModel.indexPhotos()
                                    } else {
                                        videoViewModel.registerCapturedVideo(captured)
                                    }
                                }
                            )
                        }
                        composable("videos") {
                            VideoGridScreen(
                                viewModel = videoViewModel,
                                onBackClick = { navController.popBackStack() },
                                onVideoClick = { video ->
                                    videoViewModel.selectVideo(video)
                                    navController.navigate("video-player")
                                }
                            )
                        }
                        composable("video-player") {
                            val selectedVideo by videoViewModel.selectedVideo.collectAsState()
                            VideoPlayerScreen(
                                video = selectedVideo,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("making") {
                            MakingTripListScreen(
                                photoViewModel = photoViewModel,
                                makingViewModel = makingViewModel,
                                onBackClick = { navController.popBackStack() },
                                onTripClick = { trip ->
                                    navController.navigate("making/setup/${Uri.encode(trip.tripKey)}")
                                }
                            )
                        }
                        composable("making/setup/{tripKey}") { entry ->
                            val tripKey = Uri.decode(entry.arguments?.getString("tripKey").orEmpty())
                            MakingSetupScreen(
                                tripKey = tripKey,
                                photoViewModel = photoViewModel,
                                makingViewModel = makingViewModel,
                                onBackClick = { navController.popBackStack() },
                                onStartCamera = {
                                    navController.navigate("making/camera/${Uri.encode(tripKey)}")
                                }
                            )
                        }
                        composable("making/camera/{tripKey}") { entry ->
                            val tripKey = Uri.decode(entry.arguments?.getString("tripKey").orEmpty())
                            val project by makingViewModel.selectedProject.collectAsState()
                            val trips by photoViewModel.tripAlbums.collectAsState()
                            LaunchedEffect(tripKey) { makingViewModel.selectTrip(tripKey) }
                            project?.takeIf { it.tripKey == tripKey }?.let { activeProject ->
                                CameraScreen(
                                    onBackClick = { navController.popBackStack() },
                                    onMediaCaptured = { captured ->
                                        makingViewModel.registerCapturedVideo(tripKey, captured)
                                    },
                                    initialSettings = activeProject.toCameraSettings(),
                                    initialMode = CameraMode.VIDEO,
                                    modeSelectionEnabled = false,
                                    settingsEnabled = false,
                                    showFrameGuide = activeProject.showGrid,
                                    videoDurationMs = activeProject.clipDurationMs,
                                    projectTitle = trips.firstOrNull { it.tripKey == tripKey }?.displayTitle,
                                    onSettingsChanged = { effectiveSettings ->
                                        makingViewModel.saveProject(
                                            activeProject.withCameraSettings(effectiveSettings)
                                        )
                                    }
                                )
                            }
                        }
                        composable("search") {
                            SearchScreen(
                                viewModel = photoViewModel,
                                onBackClick = { navController.popBackStack() },
                                onNavigateToTrip = { album ->
                                    navController.navigate("photos/trip/${album.tripKey}?dates=${album.dateKeys}")
                                },
                                onNavigateToDate = { dateKey ->
                                    navController.navigate("photos/date/$dateKey")
                                },
                                onNavigateToPhoto = { list, index ->
                                    photoViewModel.setViewerPhotos(list)
                                    navController.navigate("photo-viewer/$index")
                                },
                                onNavigateToMap = { dateKey ->
                                    navController.navigate("map?date=$dateKey")
                                }
                            )
                        }
                        composable("trips") {
                            TripAlbumScreen(
                                viewModel = photoViewModel,
                                videoViewModel = videoViewModel,
                                showHiddenOnly = false,
                                onBackClick = { navController.popBackStack() },
                                onNavigateToHidden = { navController.navigate("trips/hidden") },
                                onOpenTrip = { album ->
                                    navController.navigate("photos/trip/${album.tripKey}?dates=${album.dateKeys}")
                                },
                                onOpenTripOnMap = { album ->
                                    navController.navigate(
                                        "map?date=${album.dateKeys}&tripKey=${Uri.encode(album.tripKey)}"
                                    )
                                }
                            )
                        }
                        composable("trips/hidden") {
                            TripAlbumScreen(
                                viewModel = photoViewModel,
                                videoViewModel = videoViewModel,
                                showHiddenOnly = true,
                                onBackClick = { navController.popBackStack() },
                                onOpenTrip = { album ->
                                    navController.navigate("photos/trip/${album.tripKey}?dates=${album.dateKeys}")
                                },
                                onOpenTripOnMap = { album ->
                                    navController.navigate(
                                        "map?date=${album.dateKeys}&tripKey=${Uri.encode(album.tripKey)}"
                                    )
                                }
                            )
                        }
                        composable("map?date={date}&tripKey={tripKey}") { entry ->
                            val dateFilter = entry.arguments?.getString("date")
                            val tripKey = entry.arguments?.getString("tripKey")?.let(Uri::decode)
                            MapScreen(
                                viewModel = photoViewModel,
                                dateFilter = dateFilter,
                                tripKey = tripKey,
                                onBackClick = { navController.popBackStack() },
                                onOpenBucket = { bucketKey ->
                                    navController.navigate(
                                        "photos/bucket/${Uri.encode(bucketKey)}"
                                    )
                                }
                            )
                        }
                        composable("dates") {
                            DateListScreen(
                                viewModel = photoViewModel,
                                onBackClick = { navController.popBackStack() },
                                onOpenDate = { dateKey ->
                                    navController.navigate("photos/date/$dateKey")
                                },
                                onNavigateToMap = { dateKey ->
                                    navController.navigate("map?date=$dateKey")
                                }
                            )
                        }
                        composable("photos/no-location") {
                            PhotoGridScreen(
                                title = getString(R.string.view_no_location),
                                photos = photoViewModel.photosWithoutLocation,
                                onBackClick = { navController.popBackStack() },
                                onPhotoClick = { list, index ->
                                    photoViewModel.setViewerPhotos(list)
                                    navController.navigate("photo-viewer/$index")
                                }
                            )
                        }
                        composable("photos/date/{dateKey}") { entry ->
                            val dateKey = entry.arguments?.getString("dateKey").orEmpty()
                            PhotoGridScreen(
                                title = dateKey,
                                photos = photoViewModel.allPhotos,
                                filter = { it.dateKey == dateKey },
                                onBackClick = { navController.popBackStack() },
                                onPhotoClick = { list, index ->
                                    photoViewModel.setViewerPhotos(list)
                                    navController.navigate("photo-viewer/$index")
                                }
                            )
                        }
                        composable("photos/bucket/{bucketKey}") { entry ->
                            val bucketKey = Uri.decode(
                                entry.arguments?.getString("bucketKey").orEmpty()
                            )
                            PhotoGridScreen(
                                title = getString(R.string.location_photo_group),
                                photos = photoViewModel.allPhotos,
                                filter = { it.bucketKey == bucketKey },
                                onBackClick = { navController.popBackStack() },
                                onPhotoClick = { list, index ->
                                    photoViewModel.setViewerPhotos(list)
                                    navController.navigate("photo-viewer/$index")
                                }
                            )
                        }
                        composable("photos/trip/{tripKey}?dates={dates}") { entry ->
                            val tripKey = entry.arguments?.getString("tripKey").orEmpty()
                            val dateKeys = entry.arguments?.getString("dates").orEmpty().split(",")
                            val albums by photoViewModel.tripAlbums.collectAsState()
                            val hiddenAlbums by photoViewModel.hiddenTripAlbums.collectAsState()
                            val trip = (albums + hiddenAlbums).find { it.tripKey == tripKey }
                            val tripLinks by videoViewModel.tripLinks.collectAsState()
                            val activeDateKeys = trip?.dateKeys?.split(",") ?: dateKeys
                            
                            val overrides by photoViewModel.allOverrides.collectAsState()
                            val tripOverrides = overrides.filter { it.tripKey == tripKey }
                            
                            PhotoGridScreen(
                                title = trip?.displayTitle ?: getString(R.string.trip_album_title),
                                photos = photoViewModel.allPhotos,
                                filter = { photo ->
                                    val isBase = photo.dateKey in activeDateKeys
                                    val isExcluded = tripOverrides.any { it.photoMediaStoreId == photo.mediaStoreId && it.overrideType == OverrideType.EXCLUDE }
                                    val isIncluded = tripOverrides.any { it.photoMediaStoreId == photo.mediaStoreId && it.overrideType == OverrideType.INCLUDE }
                                    !photo.isScreenshot && (isBase || isIncluded) && !isExcluded
                                },
                                tripKey = tripKey,
                                onBackClick = { navController.popBackStack() },
                                onPhotoClick = { list, index ->
                                    photoViewModel.setViewerPhotos(list)
                                    navController.navigate("photo-viewer/$index")
                                },
                                onExcludePhotos = { photoIds ->
                                    photoViewModel.excludePhotosFromTrip(tripKey, photoIds)
                                },
                                onSetRepresentative = { uri ->
                                    trip?.let { photoViewModel.setRepresentativePhoto(it.id, uri) }
                                },
                                onAddPhotosClick = {
                                    navController.navigate("photos/add-to-trip/$tripKey")
                                },
                                videos = videoViewModel.allVideos,
                                videoFilter = { video ->
                                    video.dateKey in activeDateKeys || tripLinks.any {
                                        it.tripKey == tripKey && it.videoMediaStoreId == video.mediaStoreId
                                    }
                                },
                                onVideoClick = { video ->
                                    videoViewModel.selectVideo(video)
                                    navController.navigate("video-player")
                                }
                            )
                        }
                        composable("photos/add-to-trip/{tripKey}") { entry ->
                            val tripKey = entry.arguments?.getString("tripKey").orEmpty()
                            PhotoGridScreen(
                                title = getString(R.string.select_photos_to_add),
                                photos = photoViewModel.allPhotos,
                                filter = { !it.isScreenshot },
                                isSelectionMode = true,
                                onBackClick = { navController.popBackStack() },
                                onPhotosSelected = { photoIds ->
                                    photoViewModel.addPhotosToTrip(tripKey, photoIds)
                                    navController.popBackStack()
                                },
                                onPhotoClick = { _, _ -> }
                            )
                        }
                        composable("photo-viewer/{index}") { entry ->
                            val index = entry.arguments?.getString("index")?.toIntOrNull() ?: 0
                            PhotoViewerScreen(
                                viewModel = photoViewModel,
                                initialIndex = index,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
