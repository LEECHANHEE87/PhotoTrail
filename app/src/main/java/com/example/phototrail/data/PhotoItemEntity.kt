package com.example.phototrail.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoItemEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val displayName: String?,
    val takenAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val hasLocation: Boolean,
    val bucketKey: String,
    val dateKey: String
)
