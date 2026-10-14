package com.example.phototrail.data

import androidx.room.Entity

@Entity(tableName = "making_projects", primaryKeys = ["tripKey"])
data class MakingProjectEntity(
    val tripKey: String,
    val videoQuality: String = "FHD_30",
    val orientation: String = "PORTRAIT",
    val audioEnabled: Boolean = true,
    val showGrid: Boolean = true,
    val clipDurationMs: Long = 5_000L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trip_video_links", primaryKeys = ["tripKey", "videoMediaStoreId"])
data class TripVideoLinkEntity(
    val tripKey: String,
    val videoMediaStoreId: Long,
    val createdAt: Long = System.currentTimeMillis()
)
