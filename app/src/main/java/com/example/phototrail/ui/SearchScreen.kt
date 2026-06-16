package com.example.phototrail.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.phototrail.R
import com.example.phototrail.data.PhotoItemEntity
import com.example.phototrail.data.TripAlbumEntity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: PhotoViewModel,
    onBackClick: () -> Unit,
    onNavigateToTrip: (TripAlbumEntity) -> Unit,
    onNavigateToDate: (String) -> Unit,
    onNavigateToPhoto: (List<PhotoItemEntity>, Int) -> Unit,
    onNavigateToMap: (String) -> Unit
) {
    val searchState by viewModel.searchState.collectAsState()
    val tripResults by viewModel.searchTripResults.collectAsState()
    val dayResults by viewModel.searchDayResults.collectAsState()
    val photoResults by viewModel.searchPhotoResults.collectAsState()

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchState.query,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            trailingIcon = {
                                if (searchState.query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                )
                FilterRow(viewModel = viewModel, searchState = searchState)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (tripResults.isEmpty() && dayResults.isEmpty() && photoResults.isEmpty()) {
                PhotoTrailEmptyState(
                    icon = Icons.Default.Search,
                    title = stringResource(R.string.no_search_results),
                    description = stringResource(R.string.no_search_results_description),
                    action = {
                        Button(onClick = { viewModel.resetFilters() }) {
                            Text(stringResource(R.string.reset_filters))
                        }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (searchState.contentType == SearchContentType.ALL || searchState.contentType == SearchContentType.TRIP) {
                        if (tripResults.isNotEmpty()) {
                            item { PhotoTrailSectionTitle(stringResource(R.string.filter_trips)) }
                            items(tripResults, key = { "trip_${it.id}" }) { trip ->
                                TripResultCard(trip = trip, onClick = { onNavigateToTrip(trip) })
                            }
                        }
                    }

                    if (searchState.contentType == SearchContentType.ALL || searchState.contentType == SearchContentType.DAILY) {
                        if (dayResults.isNotEmpty()) {
                            item { PhotoTrailSectionTitle(stringResource(R.string.filter_daily)) }
                            items(dayResults, key = { "day_${it.dateKey}" }) { day ->
                                DayResultCard(
                                    day = day,
                                    onClick = { onNavigateToDate(day.dateKey) },
                                    onMapClick = { onNavigateToMap(day.dateKey) }
                                )
                            }
                        }
                    }

                    if (searchState.contentType == SearchContentType.ALL || searchState.contentType == SearchContentType.PHOTO) {
                        if (photoResults.isNotEmpty()) {
                            item { PhotoTrailSectionTitle(stringResource(R.string.filter_photos)) }
                            val columns = if (isWideScreen) 6 else 3
                            val chunkedPhotos = photoResults.chunked(columns)
                            items(chunkedPhotos) { rowPhotos ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rowPhotos.forEach { photo ->
                                        Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                                            AsyncImage(
                                                model = Uri.parse(photo.uri),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable {
                                                        onNavigateToPhoto(photoResults, photoResults.indexOf(photo))
                                                    },
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                    // Fill empty space in the last row
                                    repeat(columns - rowPhotos.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(viewModel: PhotoViewModel, searchState: SearchState) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Content Type Filters
        FilterChip(
            selected = searchState.contentType == SearchContentType.ALL,
            onClick = { viewModel.updateSearchContentType(SearchContentType.ALL) },
            label = { Text(stringResource(R.string.filter_all)) }
        )
        FilterChip(
            selected = searchState.contentType == SearchContentType.TRIP,
            onClick = { viewModel.updateSearchContentType(SearchContentType.TRIP) },
            label = { Text(stringResource(R.string.filter_trips)) }
        )
        FilterChip(
            selected = searchState.contentType == SearchContentType.DAILY,
            onClick = { viewModel.updateSearchContentType(SearchContentType.DAILY) },
            label = { Text(stringResource(R.string.filter_daily)) }
        )
        FilterChip(
            selected = searchState.contentType == SearchContentType.PHOTO,
            onClick = { viewModel.updateSearchContentType(SearchContentType.PHOTO) },
            label = { Text(stringResource(R.string.filter_photos)) }
        )

        // Location Filters
        FilterChip(
            selected = searchState.locationFilter == SearchLocationFilter.WITH_LOCATION,
            onClick = {
                val next = if (searchState.locationFilter == SearchLocationFilter.WITH_LOCATION) SearchLocationFilter.ALL else SearchLocationFilter.WITH_LOCATION
                viewModel.updateLocationFilter(next)
            },
            label = { Text(stringResource(R.string.filter_with_location)) },
            leadingIcon = { if (searchState.locationFilter == SearchLocationFilter.WITH_LOCATION) Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
        )
        FilterChip(
            selected = searchState.locationFilter == SearchLocationFilter.WITHOUT_LOCATION,
            onClick = {
                val next = if (searchState.locationFilter == SearchLocationFilter.WITHOUT_LOCATION) SearchLocationFilter.ALL else SearchLocationFilter.WITHOUT_LOCATION
                viewModel.updateLocationFilter(next)
            },
            label = { Text(stringResource(R.string.filter_without_location)) },
            leadingIcon = { if (searchState.locationFilter == SearchLocationFilter.WITHOUT_LOCATION) Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
        )

        // Date Filters
        FilterChip(
            selected = searchState.dateFilter == SearchDateFilter.LAST_7_DAYS,
            onClick = {
                val next = if (searchState.dateFilter == SearchDateFilter.LAST_7_DAYS) SearchDateFilter.ALL else SearchDateFilter.LAST_7_DAYS
                viewModel.updateDateFilter(next)
            },
            label = { Text(stringResource(R.string.filter_last_7_days)) }
        )
        FilterChip(
            selected = searchState.dateFilter == SearchDateFilter.LAST_30_DAYS,
            onClick = {
                val next = if (searchState.dateFilter == SearchDateFilter.LAST_30_DAYS) SearchDateFilter.ALL else SearchDateFilter.LAST_30_DAYS
                viewModel.updateDateFilter(next)
            },
            label = { Text(stringResource(R.string.filter_last_30_days)) }
        )
        FilterChip(
            selected = searchState.dateFilter == SearchDateFilter.THIS_YEAR,
            onClick = {
                val next = if (searchState.dateFilter == SearchDateFilter.THIS_YEAR) SearchDateFilter.ALL else SearchDateFilter.THIS_YEAR
                viewModel.updateDateFilter(next)
            },
            label = { Text(stringResource(R.string.filter_this_year)) }
        )
    }
}

@Composable
private fun TripResultCard(trip: TripAlbumEntity, onClick: () -> Unit) {
    PhotoTrailCard(onClick = onClick) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = Uri.parse(trip.finalRepresentativeUri),
                contentDescription = null,
                modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = trip.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${trip.startDateKey} ~ ${trip.endDateKey}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.trip_stats, trip.photoCount, trip.placeGroupCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun DayResultCard(day: DatePhotoGroup, onClick: () -> Unit, onMapClick: () -> Unit) {
    PhotoTrailCard(onClick = onClick) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = Uri.parse(day.representativePhotoUri),
                contentDescription = null,
                modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = day.dateKey, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(R.string.date_group_detailed_stats, day.locationCount, day.noLocationCount, day.locationGroupCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (day.locationCount > 0) {
                IconButton(onClick = onMapClick) {
                    Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.view_on_map), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
