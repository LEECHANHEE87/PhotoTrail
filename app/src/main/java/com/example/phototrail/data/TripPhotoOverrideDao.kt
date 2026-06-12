package com.example.phototrail.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripPhotoOverrideDao {
    @Query("SELECT * FROM trip_photo_overrides WHERE tripKey = :tripKey")
    suspend fun getOverridesForTrip(tripKey: String): List<TripPhotoOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(override: TripPhotoOverrideEntity)

    @Query("DELETE FROM trip_photo_overrides WHERE tripKey = :tripKey AND photoMediaStoreId = :photoMediaStoreId")
    suspend fun delete(tripKey: String, photoMediaStoreId: Long)

    @Query("DELETE FROM trip_photo_overrides WHERE tripKey = :tripKey")
    suspend fun clearForTrip(tripKey: String)

    @Query("SELECT * FROM trip_photo_overrides")
    fun getAllOverridesFlow(): Flow<List<TripPhotoOverrideEntity>>

    @Query("SELECT * FROM trip_photo_overrides")
    suspend fun getAllOverridesSync(): List<TripPhotoOverrideEntity>
}
