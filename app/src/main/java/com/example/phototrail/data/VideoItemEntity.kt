package com.example.phototrail.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoItemEntity(
    @PrimaryKey val id: Long,
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String?,
    val takenAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val hasLocation: Boolean,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Float?,
    val codec: String?,
    val fileSize: Long,
    val dateKey: String,
    val bucketKey: String,
    val favorite: Boolean = false,
    val syncStatus: String = "LOCAL_ONLY",
    val remotePath: String? = null,
    val checksum: String? = null,
    val dateAdded: Long?,
    val dateModified: Long?,
    val indexedAt: Long,
    val lastSeenAt: Long
)
