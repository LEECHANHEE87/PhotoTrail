package com.example.phototrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.phototrail.ui.DateListScreen
import com.example.phototrail.ui.HomeScreen
import com.example.phototrail.ui.MapScreen
import com.example.phototrail.ui.PhotoGridScreen
import com.example.phototrail.ui.PhotoViewModel
import com.example.phototrail.ui.PhotoViewerScreen
import com.example.phototrail.ui.TripAlbumScreen
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
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                viewModel = photoViewModel,
                                onNavigateToMap = { navController.navigate("map") },
                                onNavigateToDates = { navController.navigate("dates") },
                                onNavigateToNoLocation = {
                                    navController.navigate("photos/no-location")
                                },
                                onNavigateToTrips = { navController.navigate("trips") }
                            )
                        }
                        composable("trips") {
                            TripAlbumScreen(
                                viewModel = photoViewModel,
                                showHiddenOnly = false,
                                onBackClick = { navController.popBackStack() },
                                onNavigateToHidden = { navController.navigate("trips/hidden") },
                                onOpenTrip = { album ->
                                    navController.navigate("photos/trip/${album.tripKey}?dates=${album.dateKeys}")
                                },
                                onOpenTripOnMap = { album ->
                                    navController.navigate("map?date=${album.dateKeys}")
                                }
                            )
                        }
                        composable("trips/hidden") {
                            TripAlbumScreen(
                                viewModel = photoViewModel,
                                showHiddenOnly = true,
                                onBackClick = { navController.popBackStack() },
                                onOpenTrip = { album ->
                                    navController.navigate("photos/trip/${album.tripKey}?dates=${album.dateKeys}")
                                },
                                onOpenTripOnMap = { album ->
                                    navController.navigate("map?date=${album.dateKeys}")
                                }
                            )
                        }
                        composable("map?date={date}") { entry ->
                            val dateFilter = entry.arguments?.getString("date")
                            MapScreen(
                                viewModel = photoViewModel,
                                dateFilter = dateFilter,
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
                            val trip = albums.find { it.tripKey == tripKey }
                            
                            val overrides by photoViewModel.allOverrides.collectAsState()
                            val tripOverrides = overrides.filter { it.tripKey == tripKey }
                            
                            PhotoGridScreen(
                                title = trip?.displayTitle ?: getString(R.string.trip_album_title),
                                photos = photoViewModel.allPhotos,
                                filter = { photo ->
                                    val isBase = photo.dateKey in dateKeys
                                    val isExcluded = tripOverrides.any { it.photoMediaStoreId == photo.mediaStoreId && it.overrideType == OverrideType.EXCLUDE }
                                    val isIncluded = tripOverrides.any { it.photoMediaStoreId == photo.mediaStoreId && it.overrideType == OverrideType.INCLUDE }
                                    (isBase || isIncluded) && !isExcluded
                                },
                                tripKey = tripKey,
                                onBackClick = { navController.popBackStack() },
                                onPhotoClick = { list, index ->
                                    photoViewModel.setViewerPhotos(list)
                                    navController.navigate("photo-viewer/$index")
                                },
                                onExcludePhoto = { photoId ->
                                    photoViewModel.excludePhotoFromTrip(tripKey, photoId)
                                },
                                onSetRepresentative = { uri ->
                                    trip?.let { photoViewModel.setRepresentativePhoto(it.id, uri) }
                                },
                                onAddPhotosClick = {
                                    navController.navigate("photos/add-to-trip/$tripKey")
                                }
                            )
                        }
                        composable("photos/add-to-trip/{tripKey}") { entry ->
                            val tripKey = entry.arguments?.getString("tripKey").orEmpty()
                            PhotoGridScreen(
                                title = getString(R.string.select_photos_to_add),
                                photos = photoViewModel.allPhotos,
                                isSelectionMode = true,
                                onBackClick = { navController.popBackStack() },
                                onPhotosSelected = { photoIds ->
                                    photoIds.forEach { photoId ->
                                        photoViewModel.addPhotoToTrip(tripKey, photoId)
                                    }
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
