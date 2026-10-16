package com.example.phototrail.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

class PhotoRepository(
    private val context: Context,
    private val database: PhotoDatabase = PhotoDatabase.getInstance(context)
) {
    private val dao = database.photoItemDao()
    private val tripDao = database.tripAlbumDao()
    private val overrideDao = database.tripPhotoOverrideDao()

    val allPhotos = dao.getAllPhotos()
    val photosWithLocation = dao.getPhotosWithLocation()
    val photosWithoutLocation = dao.getPhotosWithoutLocation()
    val tripAlbums = tripDao.getVisibleTripAlbums()
    val hiddenTripAlbums = tripDao.getHiddenTripAlbums()
    val allOverrides = overrideDao.getAllOverridesFlow()

    fun getPhotosByDate(dateKey: String) = dao.getPhotosByDate(dateKey)

    fun getPhotosByBucketKey(bucketKey: String) = dao.getPhotosByBucketKey(bucketKey)

    suspend fun indexPhotos(onProgress: (processed: Int, total: Int) -> Unit): IndexStats {
        val resolver = context.contentResolver
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val currentDbPhotos = dao.getAllPhotosSync().associateBy { it.mediaStoreId }
        val mediaStoreIds = mutableSetOf<Long>()
        
        var newCount = 0
        var updatedCount = 0
        var unchangedCount = 0
        val currentTime = System.currentTimeMillis()
        
        val photosToUpsert = mutableListOf<PhotoItemEntity>()

        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val total = cursor.count
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH).takeIf { it >= 0 }
            } else {
                null
            }

            while (cursor.moveToNext()) {
                val msId = cursor.getLong(idColumn)
                mediaStoreIds.add(msId)
                
                val dateAdded = cursor.longOrNull(addedColumn)?.times(1_000L)
                val dateModified = cursor.longOrNull(modifiedColumn)?.times(1_000L)
                val displayName = cursor.getString(nameColumn)
                val relativePath = relativePathColumn?.let(cursor::getString)
                val isScreenshot = isScreenshotMedia(displayName, relativePath)
                
                val existingPhoto = currentDbPhotos[msId]
                
                val shouldUpdate = existingPhoto == null || 
                                   existingPhoto.dateModified != dateModified ||
                                   existingPhoto.uri.isEmpty() ||
                                   existingPhoto.isScreenshot != isScreenshot

                if (shouldUpdate) {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        msId
                    )
                    val mediaTakenAt = cursor.longOrNull(takenColumn)
                    
                    val exifData = runCatching {
                        resolver.openInputStream(uri)?.use { input ->
                            val exif = ExifInterface(input)
                            ExifData(
                                takenAt = parseExifDate(
                                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                                ),
                                location = exif.latLong?.let { it[0] to it[1] }
                            )
                        }
                    }.getOrNull()

                    val takenAt = mediaTakenAt ?: exifData?.takenAt ?: dateAdded ?: currentTime
                    val dateKey = DATE_KEY_FORMAT.get()!!.format(Date(takenAt))
                    val latitude = exifData?.location?.first
                    val longitude = exifData?.location?.second
                    val hasLocation = latitude != null && longitude != null
                    val bucketKey = if (latitude != null && longitude != null) {
                        val roundedLatitude = round(latitude / BUCKET_SIZE) * BUCKET_SIZE
                        val roundedLongitude = round(longitude / BUCKET_SIZE) * BUCKET_SIZE
                        String.format(
                            Locale.US,
                            "%s_%.3f_%.3f",
                            dateKey,
                            roundedLatitude,
                            roundedLongitude
                        )
                    } else {
                        "${dateKey}_NO_LOCATION"
                    }

                    photosToUpsert += PhotoItemEntity(
                        id = msId,
                        mediaStoreId = msId,
                        uri = uri.toString(),
                        displayName = displayName,
                        takenAt = takenAt,
                        latitude = latitude,
                        longitude = longitude,
                        hasLocation = hasLocation,
                        bucketKey = bucketKey,
                        dateKey = dateKey,
                        dateAdded = dateAdded,
                        dateModified = dateModified,
                        isScreenshot = isScreenshot,
                        indexedAt = existingPhoto?.indexedAt ?: currentTime,
                        lastSeenAt = currentTime
                    )
                    
                    if (existingPhoto == null) newCount++ else updatedCount++
                } else {
                    unchangedCount++
                }
                onProgress(cursor.position + 1, total)
            }
        }

        val deletedIds = currentDbPhotos.keys.filter { it !in mediaStoreIds }
        val deletedCount = deletedIds.size

        database.withTransaction {
            if (photosToUpsert.isNotEmpty()) {
                photosToUpsert.chunked(500).forEach { dao.upsertAll(it) }
            }
            if (deletedIds.isNotEmpty()) {
                dao.deletePhotosByMediaStoreId(deletedIds)
            }
        }

        // Auto generate trips after indexing
        generateTripAlbums()

        val finalPhotos = dao.getAllPhotosSync()
        return IndexStats(
            mediaStoreCount = mediaStoreIds.size,
            newCount = newCount,
            updatedCount = updatedCount,
            deletedCount = deletedCount,
            unchangedCount = unchangedCount,
            locationCount = finalPhotos.count { it.hasLocation },
            noLocationCount = finalPhotos.count { !it.hasLocation }
        )
    }

    suspend fun generateTripAlbums() {
        val allPhotos = dao.getAllPhotosSync()
            .asSequence()
            .filterNot { it.isScreenshot }
            .sortedBy { it.takenAt }
            .toList()
        if (allPhotos.isEmpty()) {
            tripDao.deleteAllAutoTrips()
            refreshManualTrips(emptyList())
            return
        }

        val photosByDate = allPhotos.groupBy { it.dateKey }
        val sortedDateKeys = photosByDate.keys.sorted()
        val allOverrides = overrideDao.getAllOverridesSync().groupBy { it.tripKey }

        val dailySummaries = sortedDateKeys.map { dateKey ->
            val photos = photosByDate[dateKey] ?: emptyList()
            val locationPhotos = photos.filter { it.hasLocation }.sortedBy { it.takenAt }
            val locationBucketKeys = locationPhotos.map { it.bucketKey }.distinct()
            
            val placeGroupCenters = photos.filter { it.hasLocation }
                .groupBy { it.bucketKey }
                .map { (_, group) ->
                    group.map { it.latitude!! }.average() to group.map { it.longitude!! }.average()
                }

            DaySummary(
                dateKey = dateKey,
                totalCount = photos.size,
                locationCount = locationPhotos.size,
                noLocationCount = photos.size - locationPhotos.size,
                placeGroupCount = locationBucketKeys.size,
                centerLat = if (locationPhotos.isNotEmpty()) locationPhotos.map { it.latitude!! }.average() else null,
                centerLng = if (locationPhotos.isNotEmpty()) locationPhotos.map { it.longitude!! }.average() else null,
                firstLat = locationPhotos.firstOrNull()?.latitude,
                firstLng = locationPhotos.firstOrNull()?.longitude,
                lastLat = locationPhotos.lastOrNull()?.latitude,
                lastLng = locationPhotos.lastOrNull()?.longitude,
                startTime = photos.minOfOrNull { it.takenAt ?: Long.MAX_VALUE } ?: 0L,
                endTime = photos.maxOfOrNull { it.takenAt ?: 0L } ?: 0L,
                representativeUri = photos.maxByOrNull { it.takenAt ?: 0L }?.uri,
                placeGroupCenters = placeGroupCenters
            )
        }

        val existingTrips = tripDao.getAllTripAlbumsSync().associateBy { it.tripKey }
        val manuallyAssignedDates = existingTrips.values
            .filter { it.isManual && !it.isHidden }
            .flatMap { it.dateKeys.split(",") }
            .toSet()
        val generator = TripAlbumGeneratorV2()
        val trips = generator.groupDays(dailySummaries.filter { it.dateKey !in manuallyAssignedDates })
        
        Log.d("TripGrouping", "Daily summaries: ${dailySummaries.size}, Final trips: ${trips.size}")

        val autoExistingTrips = existingTrips.values.filter { !it.isManual }

        // 1. Generate Automatic Trips
        val finalTripEntities = trips.filter { it.isNotEmpty() }.map { tripDays ->
            val startDate = tripDays.first().dateKey
            val endDate = tripDays.last().dateKey
            val dateKeysList = tripDays.map { it.dateKey }
            val dateKeysSet = dateKeysList.toSet()
            val dateKeysString = dateKeysList.joinToString(",")
            
            // Date ranges can change after re-indexing. The overlap match below preserves
            // the stable trip key so user edits and media links are not lost.
            val tripKey = "${startDate}_${endDate}_${dateKeysString.hashCode()}"
            var existing = existingTrips[tripKey]
            
            if (existing == null) {
                // Try to find a matching existing trip by overlap (70% rule)
                existing = findMatchingExistingTrip(dateKeysSet, autoExistingTrips)
            }
            val persistedTripKey = existing?.tripKey ?: tripKey

            // Calculate photos considering overrides
            val overrides = allOverrides[persistedTripKey] ?: emptyList()
            val basePhotos = allPhotos.filter { it.dateKey in dateKeysSet }
            val finalPhotos = applyOverrides(basePhotos, overrides, allPhotos)
            
            val locPhotos = finalPhotos.filter { it.hasLocation }
            val placeGroups = locPhotos.map { it.bucketKey }.distinct().size

            val genTitle = buildGeneratedTripTitle(startDate, endDate)

            TripAlbumEntity(
                id = existing?.id ?: 0,
                tripKey = persistedTripKey,
                generatedTitle = genTitle,
                customTitle = existing?.customTitle,
                isUserEdited = existing?.isUserEdited ?: false,
                isHidden = existing?.isHidden ?: (existing?.mergedIntoTripKey != null),
                startDateKey = startDate,
                endDateKey = endDate,
                dateKeys = dateKeysString,
                photoCount = finalPhotos.size,
                locationPhotoCount = locPhotos.size,
                noLocationPhotoCount = finalPhotos.size - locPhotos.size,
                placeGroupCount = placeGroups,
                representativePhotoUri = finalPhotos.maxByOrNull { it.takenAt ?: 0L }?.uri,
                centerLatitude = locPhotos.mapNotNull { it.latitude }.average().takeIf { !it.isNaN() },
                centerLongitude = locPhotos.mapNotNull { it.longitude }.average().takeIf { !it.isNaN() },
                isManual = false,
                sourceTripKeys = existing?.sourceTripKeys,
                mergedIntoTripKey = existing?.mergedIntoTripKey,
                isSplitFromTripKey = existing?.isSplitFromTripKey,
                customRepresentativePhotoUri = existing?.customRepresentativePhotoUri,
                isRepresentativeUserSelected = existing?.isRepresentativeUserSelected ?: false,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        // 2. Preserve Manual Trips
        val manualTrips = existingTrips.values.filter { it.isManual }.map { existing ->
            val dateKeysList = existing.dateKeys.split(",")
            val overrides = allOverrides[existing.tripKey] ?: emptyList()
            val basePhotos = allPhotos.filter { it.dateKey in dateKeysList }
            val finalPhotos = applyOverrides(basePhotos, overrides, allPhotos)
            
            val locPhotos = finalPhotos.filter { it.hasLocation }
            val placeGroups = locPhotos.map { it.bucketKey }.distinct().size

            existing.copy(
                photoCount = finalPhotos.size,
                locationPhotoCount = locPhotos.size,
                noLocationPhotoCount = finalPhotos.size - locPhotos.size,
                placeGroupCount = placeGroups,
                representativePhotoUri = finalPhotos.maxByOrNull { it.takenAt ?: 0L }?.uri,
                centerLatitude = locPhotos.mapNotNull { it.latitude }.average().takeIf { !it.isNaN() },
                centerLongitude = locPhotos.mapNotNull { it.longitude }.average().takeIf { !it.isNaN() },
                updatedAt = System.currentTimeMillis()
            )
        }

        database.withTransaction {
            if (finalTripEntities.isEmpty()) {
                tripDao.deleteAllAutoTrips()
            } else {
                tripDao.deleteAutoTripsExcept(finalTripEntities.map { it.tripKey })
            }
            tripDao.insertAll(finalTripEntities + manualTrips)
        }
    }

    suspend fun createManualTrip(title: String, startDateKey: String, endDateKey: String) {
        val dateKeys = buildInclusiveDateKeys(startDateKey, endDateKey)
        require(dateKeys.isNotEmpty()) { "Invalid trip date range" }
        val now = System.currentTimeMillis()
        val trip = TripAlbumEntity(
            tripKey = "manual_${now}_${dateKeys.hashCode()}",
            generatedTitle = buildGeneratedTripTitle(startDateKey, endDateKey),
            customTitle = title.trim().takeIf { it.isNotEmpty() },
            isUserEdited = title.isNotBlank(),
            startDateKey = startDateKey,
            endDateKey = endDateKey,
            dateKeys = dateKeys.joinToString(","),
            photoCount = 0,
            locationPhotoCount = 0,
            noLocationPhotoCount = 0,
            placeGroupCount = 0,
            representativePhotoUri = null,
            centerLatitude = null,
            centerLongitude = null,
            isManual = true,
            createdAt = now,
            updatedAt = now
        )
        tripDao.insertAll(listOf(trip))
        generateTripAlbums()
    }

    suspend fun updateTripSchedule(
        id: Long,
        title: String,
        startDateKey: String,
        endDateKey: String
    ) {
        val existing = tripDao.getTripAlbumById(id) ?: return
        val dateKeys = buildInclusiveDateKeys(startDateKey, endDateKey)
        require(dateKeys.isNotEmpty()) { "Invalid trip date range" }
        tripDao.insertAll(
            listOf(
                existing.copy(
                    generatedTitle = buildGeneratedTripTitle(startDateKey, endDateKey),
                    customTitle = title.trim().takeIf { it.isNotEmpty() },
                    isUserEdited = title.isNotBlank(),
                    startDateKey = startDateKey,
                    endDateKey = endDateKey,
                    dateKeys = dateKeys.joinToString(","),
                    isManual = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
        )
        generateTripAlbums()
    }

    private suspend fun refreshManualTrips(allPhotos: List<PhotoItemEntity>) {
        val manualTrips = tripDao.getAllTripAlbumsSync().filter { it.isManual }.map { existing ->
            val dateKeys = existing.dateKeys.split(",").toSet()
            val overrides = overrideDao.getOverridesForTrip(existing.tripKey)
            val finalPhotos = applyOverrides(
                allPhotos.filter { it.dateKey in dateKeys },
                overrides,
                allPhotos
            )
            val located = finalPhotos.filter { it.hasLocation }
            existing.copy(
                photoCount = finalPhotos.size,
                locationPhotoCount = located.size,
                noLocationPhotoCount = finalPhotos.size - located.size,
                placeGroupCount = located.map { it.bucketKey }.distinct().size,
                representativePhotoUri = finalPhotos.maxByOrNull { it.takenAt ?: 0L }?.uri,
                centerLatitude = located.mapNotNull { it.latitude }.average().takeIf { !it.isNaN() },
                centerLongitude = located.mapNotNull { it.longitude }.average().takeIf { !it.isNaN() },
                updatedAt = System.currentTimeMillis()
            )
        }
        if (manualTrips.isNotEmpty()) tripDao.insertAll(manualTrips)
    }

    private fun findMatchingExistingTrip(
        newDateKeys: Set<String>,
        existingTrips: Collection<TripAlbumEntity>
    ): TripAlbumEntity? {
        val matches = existingTrips.filter { existing ->
            val existingDateKeys = existing.dateKeys.split(",").toSet()
            val intersection = newDateKeys.intersect(existingDateKeys)
            if (intersection.isEmpty()) return@filter false
            
            val maxCount = maxOf(newDateKeys.size, existingDateKeys.size)
            val overlapRatio = intersection.size.toDouble() / maxCount
            overlapRatio >= 0.7
        }
        return if (matches.size == 1) matches[0] else null
    }

    private fun applyOverrides(
        basePhotos: List<PhotoItemEntity>,
        overrides: List<TripPhotoOverrideEntity>,
        allPhotos: List<PhotoItemEntity>
    ): List<PhotoItemEntity> {
        val excludeIds = overrides.filter { it.overrideType == OverrideType.EXCLUDE }.map { it.photoMediaStoreId }.toSet()
        val includeIds = overrides.filter { it.overrideType == OverrideType.INCLUDE }.map { it.photoMediaStoreId }.toSet()
        
        val afterExclude = basePhotos.filter { it.mediaStoreId !in excludeIds }
        val includedPhotos = allPhotos.filter { it.mediaStoreId in includeIds }
        
        return (afterExclude + includedPhotos).distinctBy { it.mediaStoreId }.sortedBy { it.takenAt }
    }

    suspend fun mergeTrips(ids: List<Long>) {
        val selectedTrips = ids.mapNotNull { tripDao.getTripAlbumById(it) }
        if (selectedTrips.size < 2) return

        val allDateKeys = selectedTrips.flatMap { it.dateKeys.split(",") }.distinct().sorted()
        val startDate = allDateKeys.first()
        val endDate = allDateKeys.last()
        val dateKeys = allDateKeys.joinToString(",")
        val sourceTripKeys = selectedTrips.joinToString(",") { it.tripKey }
        
        val tripKey = "manual_merge_${System.currentTimeMillis()}"
        
        val newTrip = TripAlbumEntity(
            tripKey = tripKey,
            generatedTitle = buildMergedTripTitle(startDate, endDate),
            startDateKey = startDate,
            endDateKey = endDate,
            dateKeys = dateKeys,
            photoCount = 0, // Will be updated in generateTripAlbums
            locationPhotoCount = 0,
            noLocationPhotoCount = 0,
            placeGroupCount = 0,
            representativePhotoUri = selectedTrips.first().representativePhotoUri,
            centerLatitude = null,
            centerLongitude = null,
            isManual = true,
            sourceTripKeys = sourceTripKeys,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        database.withTransaction {
            tripDao.insertAll(listOf(newTrip))
            selectedTrips.forEach {
                tripDao.insertAll(listOf(it.copy(isHidden = true, mergedIntoTripKey = tripKey)))
            }
        }
        generateTripAlbums()
    }

    // TODO: Add merge undo by clearing mergedIntoTripKey and restoring source trips.

    suspend fun splitTrip(id: Long, dateKeysToSplit: List<String>) {
        val originalTrip = tripDao.getTripAlbumById(id) ?: return
        val originalDateKeys = originalTrip.dateKeys.split(",")
        val remainingDateKeys = originalDateKeys.filter { it !in dateKeysToSplit }

        if (dateKeysToSplit.isEmpty() || remainingDateKeys.isEmpty()) return

        val createTrip = { keys: List<String> ->
            val sorted = keys.sorted()
            val start = sorted.first()
            val end = sorted.last()
            TripAlbumEntity(
                tripKey = "manual_split_${System.currentTimeMillis()}_${keys.hashCode()}",
                generatedTitle = buildSplitTripTitle(start, end),
                startDateKey = start,
                endDateKey = end,
                dateKeys = keys.joinToString(","),
                photoCount = 0,
                locationPhotoCount = 0,
                noLocationPhotoCount = 0,
                placeGroupCount = 0,
                representativePhotoUri = null,
                centerLatitude = null,
                centerLongitude = null,
                isManual = true,
                isSplitFromTripKey = originalTrip.tripKey,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        val trip1 = createTrip(dateKeysToSplit)
        val trip2 = createTrip(remainingDateKeys)

        database.withTransaction {
            tripDao.insertAll(listOf(trip1, trip2))
            tripDao.insertAll(listOf(originalTrip.copy(isHidden = true)))
        }
        generateTripAlbums()
    }

    // TODO: Support splitting by selected media in addition to whole dates.

    suspend fun addPhotoToTrip(tripKey: String, photoMediaStoreId: Long) {
        addPhotosToTrip(tripKey, listOf(photoMediaStoreId))
    }

    suspend fun addPhotosToTrip(tripKey: String, photoMediaStoreIds: List<Long>) {
        updateTripPhotoOverrides(tripKey, photoMediaStoreIds, OverrideType.INCLUDE)
    }

    suspend fun excludePhotoFromTrip(tripKey: String, photoMediaStoreId: Long) {
        excludePhotosFromTrip(tripKey, listOf(photoMediaStoreId))
    }

    suspend fun excludePhotosFromTrip(tripKey: String, photoMediaStoreIds: List<Long>) {
        updateTripPhotoOverrides(tripKey, photoMediaStoreIds, OverrideType.EXCLUDE)
    }

    private suspend fun updateTripPhotoOverrides(
        tripKey: String,
        photoMediaStoreIds: List<Long>,
        overrideType: OverrideType
    ) {
        val distinctIds = photoMediaStoreIds.distinct()
        if (distinctIds.isEmpty()) return

        val now = System.currentTimeMillis()
        val overrides = distinctIds.map { photoMediaStoreId ->
            TripPhotoOverrideEntity(
                tripKey = tripKey,
                photoMediaStoreId = photoMediaStoreId,
                overrideType = overrideType,
                createdAt = now
            )
        }

        database.withTransaction {
            overrideDao.deleteAll(tripKey, distinctIds)
            overrideDao.insertAll(overrides)
        }
        generateTripAlbums()
    }

    suspend fun setRepresentativePhoto(id: Long, photoUri: String) {
        val trip = tripDao.getTripAlbumById(id) ?: return
        tripDao.insertAll(listOf(trip.copy(
            customRepresentativePhotoUri = photoUri,
            isRepresentativeUserSelected = true,
            updatedAt = System.currentTimeMillis()
        )))
    }

    suspend fun renameTripAlbum(id: Long, newTitle: String) {
        tripDao.updateCustomTitle(id, newTitle, System.currentTimeMillis())
    }

    suspend fun hideTripAlbum(id: Long) {
        tripDao.updateHidden(id, true, System.currentTimeMillis())
    }

    suspend fun unhideTripAlbum(id: Long) {
        tripDao.updateHidden(id, false, System.currentTimeMillis())
    }

    private fun parseExifDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { EXIF_DATE_FORMAT.get()!!.parse(value)?.time }.getOrNull()
    }

    private fun android.database.Cursor.longOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index).takeIf { it > 0L }

    private data class ExifData(
        val takenAt: Long?,
        val location: Pair<Double, Double>?
    )

    companion object {
        private const val BUCKET_SIZE = 0.004
        private val DATE_KEY_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }
        private val EXIF_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat(
                "yyyy:MM:dd HH:mm:ss",
                Locale.US
            )
        }
    }
}

data class IndexStats(
    val mediaStoreCount: Int,
    val newCount: Int,
    val updatedCount: Int,
    val deletedCount: Int,
    val unchangedCount: Int,
    val locationCount: Int,
    val noLocationCount: Int
)

internal fun isScreenshotMedia(displayName: String?, relativePath: String?): Boolean {
    val normalized = sequenceOf(displayName, relativePath)
        .filterNotNull()
        .joinToString("/")
        .lowercase(Locale.ROOT)
        .replace("_", "")
        .replace("-", "")
        .replace(" ", "")

    return SCREENSHOT_MARKERS.any(normalized::contains)
}

private val SCREENSHOT_MARKERS = listOf(
    "screenshot",
    "screencapture",
    "스크린샷",
    "화면캡처",
    "スクリーンショット"
)
