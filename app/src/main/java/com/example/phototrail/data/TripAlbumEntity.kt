package com.example.phototrail.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_albums")
data class TripAlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripKey: String,
    val generatedTitle: String,
    val customTitle: String? = null,
    val isUserEdited: Boolean = false,
    val isHidden: Boolean = false,
    val startDateKey: String,
    val endDateKey: String,
    val dateKeys: String, // Comma-separated dateKeys
    val photoCount: Int,
    val locationPhotoCount: Int,
    val noLocationPhotoCount: Int,
    val placeGroupCount: Int,
    val representativePhotoUri: String?,
    val centerLatitude: Double?,
    val centerLongitude: Double?,
    val isManual: Boolean = false,
    val sourceTripKeys: String? = null,
    val mergedIntoTripKey: String? = null,
    val isSplitFromTripKey: String? = null,
    val customRepresentativePhotoUri: String? = null,
    val isRepresentativeUserSelected: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
) {
    val displayTitle: String
        get() = customTitle ?: generatedTitle

    val finalRepresentativeUri: String?
        get() = if (isRepresentativeUserSelected) customRepresentativePhotoUri else representativePhotoUri
}
