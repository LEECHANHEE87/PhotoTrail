package com.example.phototrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import com.example.phototrail.ui.DateListScreen
import com.example.phototrail.ui.HomeScreen
import com.example.phototrail.ui.MapScreen
import com.example.phototrail.ui.PhotoGridScreen
import com.example.phototrail.ui.PhotoViewModel
import com.example.phototrail.ui.theme.PhotoTrailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                }
                            )
                        }
                        composable("map") {
                            MapScreen(
                                viewModel = photoViewModel,
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
                                }
                            )
                        }
                        composable("photos/no-location") {
                            PhotoGridScreen(
                                title = getString(R.string.view_no_location),
                                photos = photoViewModel.photosWithoutLocation,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("photos/date/{dateKey}") { entry ->
                            val dateKey = entry.arguments?.getString("dateKey").orEmpty()
                            PhotoGridScreen(
                                title = dateKey,
                                photos = photoViewModel.allPhotos,
                                filter = { it.dateKey == dateKey },
                                onBackClick = { navController.popBackStack() }
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
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
