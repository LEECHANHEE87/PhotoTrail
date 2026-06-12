package com.example.phototrail.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_photo_overrides")
data class TripPhotoOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripKey: String,
    val photoMediaStoreId: Long,
    val overrideType: OverrideType,
    val createdAt: Long = System.currentTimeMillis()
)

enum class OverrideType {
    INCLUDE,
    EXCLUDE
}
