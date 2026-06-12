package com.example.phototrail.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoItemEntity(
    @PrimaryKey val id: Long, // Use MediaStore ID as the primary key for stability
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String?,
    val takenAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val hasLocation: Boolean,
    val bucketKey: String,
    val dateKey: String,
    val dateAdded: Long?,
    val dateModified: Long?,
    val indexedAt: Long,
    val lastSeenAt: Long
)
