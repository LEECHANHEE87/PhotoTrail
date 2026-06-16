package com.example.phototrail.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.phototrail.data.IndexStats
import com.example.phototrail.data.PhotoItemEntity
import com.example.phototrail.data.PhotoRepository
import com.example.phototrail.data.TripAlbumEntity
import com.example.phototrail.data.TripPhotoOverrideEntity
import kotlinx.coroutines.Dispatchers
import com.example.phototrail.data.OverrideType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class IndexUiState(
    val isIndexing: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val stats: IndexStats? = null,
    val errorMessage: String? = null
)

enum class SearchContentType { ALL, TRIP, DAILY, PHOTO }
enum class SearchLocationFilter { ALL, WITH_LOCATION, WITHOUT_LOCATION }
enum class SearchDateFilter { ALL, LAST_7_DAYS, LAST_30_DAYS, THIS_YEAR, CUSTOM }

data class SearchState(
    val query: String = "",
    val contentType: SearchContentType = SearchContentType.ALL,
    val locationFilter: SearchLocationFilter = SearchLocationFilter.ALL,
    val dateFilter: SearchDateFilter = SearchDateFilter.ALL,
    val startDate: String? = null, // yyyy-MM-dd
    val endDate: String? = null,   // yyyy-MM-dd
    val minPhotoCount: Int = 0,
    val showHiddenTrips: Boolean = false
)

data class DatePhotoGroup(
    val dateKey: String,
    val totalCount: Int,
    val locationCount: Int,
    val noLocationCount: Int,
    val locationGroupCount: Int,
    val representativePhotoUri: String?
)

@OptIn(FlowPreview::class)
class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository(application)

    val allPhotos: StateFlow<List<PhotoItemEntity>> = repository.allPhotos.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val photosWithLocation: StateFlow<List<PhotoItemEntity>> =
        repository.photosWithLocation.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )
    val photosWithoutLocation: StateFlow<List<PhotoItemEntity>> =
        repository.photosWithoutLocation.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val tripAlbums: StateFlow<List<TripAlbumEntity>> = repository.tripAlbums.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val hiddenTripAlbums: StateFlow<List<TripAlbumEntity>> = repository.hiddenTripAlbums.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val allOverrides: StateFlow<List<TripPhotoOverrideEntity>> = repository.allOverrides.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _isGeneratingTrips = MutableStateFlow(false)
    val isGeneratingTrips: StateFlow<Boolean> = _isGeneratingTrips

    private val _viewerPhotos = MutableStateFlow<List<PhotoItemEntity>>(emptyList())
    val viewerPhotos: StateFlow<List<PhotoItemEntity>> = _viewerPhotos

    fun setViewerPhotos(photos: List<PhotoItemEntity>) {
        _viewerPhotos.value = photos
    }

    private val _indexUiState = MutableStateFlow(IndexUiState())
    val indexUiState: StateFlow<IndexUiState> = _indexUiState

    private var hasPerformedInitialSync = false

    fun indexPhotos(isAutoSync: Boolean = false) {
        if (_indexUiState.value.isIndexing) return
        if (isAutoSync && hasPerformedInitialSync) return

        viewModelScope.launch {
            _indexUiState.value = _indexUiState.value.copy(isIndexing = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.indexPhotos { processed, total ->
                        _indexUiState.value = _indexUiState.value.copy(
                            processed = processed,
                            total = total
                        )
                    }
                }
            }.onSuccess { stats ->
                _indexUiState.value = IndexUiState(stats = stats)
                hasPerformedInitialSync = true
            }.onFailure { error ->
                _indexUiState.value = IndexUiState(
                    errorMessage = error.message ?: "Photo indexing failed"
                )
            }
        }
    }

    fun generateTrips() {
        if (_isGeneratingTrips.value) return
        viewModelScope.launch {
            _isGeneratingTrips.value = true
            try {
                withContext(Dispatchers.IO) {
                    repository.generateTripAlbums()
                }
            } catch (e: Exception) {
                // Log or handle error
            } finally {
                _isGeneratingTrips.value = false
            }
        }
    }

    fun renameTripAlbum(id: Long, newTitle: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.renameTripAlbum(id, newTitle)
            }
        }
    }

    fun hideTripAlbum(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.hideTripAlbum(id)
            }
        }
    }

    fun unhideTripAlbum(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.unhideTripAlbum(id)
            }
        }
    }

    fun mergeTrips(ids: List<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.mergeTrips(ids)
            }
        }
    }

    fun splitTrip(id: Long, dateKeysToSplit: List<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.splitTrip(id, dateKeysToSplit)
            }
        }
    }

    fun addPhotoToTrip(tripKey: String, photoMediaStoreId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.addPhotoToTrip(tripKey, photoMediaStoreId)
            }
        }
    }

    fun excludePhotoFromTrip(tripKey: String, photoMediaStoreId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.excludePhotoFromTrip(tripKey, photoMediaStoreId)
            }
        }
    }

    // TODO: 제외한 사진을 다시 복구하는(Include로 변경하거나 Override 삭제) 화면/기능 구현 필요

    fun setRepresentativePhoto(id: Long, photoUri: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.setRepresentativePhoto(id, photoUri)
            }
        }
    }

    // --- Search Logic ---

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState

    fun updateSearchQuery(query: String) {
        _searchState.value = _searchState.value.copy(query = query)
    }

    fun updateSearchContentType(type: SearchContentType) {
        _searchState.value = _searchState.value.copy(contentType = type)
    }

    fun updateLocationFilter(filter: SearchLocationFilter) {
        _searchState.value = _searchState.value.copy(locationFilter = filter)
    }

    fun updateDateFilter(filter: SearchDateFilter, start: String? = null, end: String? = null) {
        _searchState.value = _searchState.value.copy(
            dateFilter = filter,
            startDate = start,
            endDate = end
        )
    }

    fun resetFilters() {
        _searchState.value = SearchState(query = _searchState.value.query)
    }

    private val debouncedQuery = _searchState
        .map { it.query }
        .debounce(300)

    val searchTripResults: StateFlow<List<TripAlbumEntity>> = combine(
        debouncedQuery,
        _searchState,
        tripAlbums,
        hiddenTripAlbums
    ) { query, state, albums, hiddenAlbums ->
        val source = if (state.showHiddenTrips) albums + hiddenAlbums else albums
        source.filter { trip ->
            val matchesQuery = query.isBlank() || 
                trip.displayTitle.contains(query, ignoreCase = true) ||
                trip.dateKeys.contains(query, ignoreCase = true)
            
            val matchesDate = matchesDateFilter(trip.startDateKey, trip.endDateKey, state)
            val matchesLocation = when (state.locationFilter) {
                SearchLocationFilter.ALL -> true
                SearchLocationFilter.WITH_LOCATION -> trip.locationPhotoCount > 0
                SearchLocationFilter.WITHOUT_LOCATION -> trip.noLocationPhotoCount > 0
            }
            val matchesCount = trip.photoCount >= state.minPhotoCount

            matchesQuery && matchesDate && matchesLocation && matchesCount
        }.sortedByDescending { it.startDateKey }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchDayResults: StateFlow<List<DatePhotoGroup>> = combine(
        debouncedQuery,
        _searchState,
        allPhotos
    ) { query, state, photos ->
        photos.groupBy { it.dateKey }
            .map { (dateKey, items) ->
                val representativePhoto = items.find { it.hasLocation } ?: items.firstOrNull()
                DatePhotoGroup(
                    dateKey = dateKey,
                    totalCount = items.size,
                    locationCount = items.count { it.hasLocation },
                    noLocationCount = items.count { !it.hasLocation },
                    locationGroupCount = items.filter { it.hasLocation }
                        .map { it.bucketKey }
                        .distinct()
                        .size,
                    representativePhotoUri = representativePhoto?.uri
                )
            }
            .filter { group ->
                val matchesQuery = query.isBlank() || group.dateKey.contains(query, ignoreCase = true)
                val matchesDate = matchesDateFilter(group.dateKey, group.dateKey, state)
                val matchesLocation = when (state.locationFilter) {
                    SearchLocationFilter.ALL -> true
                    SearchLocationFilter.WITH_LOCATION -> group.locationCount > 0
                    SearchLocationFilter.WITHOUT_LOCATION -> group.noLocationCount > 0
                }
                val matchesCount = group.totalCount >= state.minPhotoCount

                matchesQuery && matchesDate && matchesLocation && matchesCount
            }
            .sortedByDescending { it.dateKey }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchPhotoResults: StateFlow<List<PhotoItemEntity>> = combine(
        debouncedQuery,
        _searchState,
        allPhotos
    ) { query, state, photos ->
        photos.filter { photo ->
            val matchesQuery = query.isBlank() || 
                (photo.displayName?.contains(query, ignoreCase = true) ?: false) ||
                photo.bucketKey.contains(query, ignoreCase = true) ||
                photo.dateKey.contains(query, ignoreCase = true)
            
            val matchesDate = matchesDateFilter(photo.dateKey, photo.dateKey, state)
            val matchesLocation = when (state.locationFilter) {
                SearchLocationFilter.ALL -> true
                SearchLocationFilter.WITH_LOCATION -> photo.hasLocation
                SearchLocationFilter.WITHOUT_LOCATION -> !photo.hasLocation
            }

            matchesQuery && matchesDate && matchesLocation
        }.sortedByDescending { it.takenAt ?: 0L }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun matchesDateFilter(startKey: String, endKey: String, state: SearchState): Boolean {
        return when (state.dateFilter) {
            SearchDateFilter.ALL -> true
            SearchDateFilter.LAST_7_DAYS -> {
                val limit = getNDaysAgoKey(7)
                endKey >= limit
            }
            SearchDateFilter.LAST_30_DAYS -> {
                val limit = getNDaysAgoKey(30)
                endKey >= limit
            }
            SearchDateFilter.THIS_YEAR -> {
                val yearPrefix = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()
                startKey.startsWith(yearPrefix) || endKey.startsWith(yearPrefix)
            }
            SearchDateFilter.CUSTOM -> {
                val start = state.startDate ?: "0000-00-00"
                val end = state.endDate ?: "9999-99-99"
                !(endKey < start || startKey > end)
            }
        }
    }

    private fun getNDaysAgoKey(days: Int): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -days)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(cal.time)
    }

    fun getTripPhotos(trip: TripAlbumEntity): List<PhotoItemEntity> {
        val all = allPhotos.value
        val dateKeys = trip.dateKeys.split(",")
        // This is a simplified version for UI, ideally we'd fetch overrides from repository/DB
        // but for immediate UI response we can just use the dateKeys for now,
        // or we can make this a StateFlow that observes overrides.
        // Given the constraints, let's just use dateKeys for basic filtering 
        // and handle overrides by refreshing the list when they change.
        return all.filter { it.dateKey in dateKeys }.sortedBy { it.takenAt }
    }
}
