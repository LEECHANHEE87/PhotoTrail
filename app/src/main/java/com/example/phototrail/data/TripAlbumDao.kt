package com.example.phototrail.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripAlbumDao {
    @Query("SELECT * FROM trip_albums WHERE isHidden = 0 ORDER BY startDateKey DESC")
    fun getVisibleTripAlbums(): Flow<List<TripAlbumEntity>>

    @Query("SELECT * FROM trip_albums WHERE isHidden = 1 ORDER BY startDateKey DESC")
    fun getHiddenTripAlbums(): Flow<List<TripAlbumEntity>>

    @Query("SELECT * FROM trip_albums ORDER BY startDateKey DESC")
    fun getAllTripAlbums(): Flow<List<TripAlbumEntity>>

    @Query("SELECT * FROM trip_albums")
    suspend fun getAllTripAlbumsSync(): List<TripAlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<TripAlbumEntity>)

    @Query("DELETE FROM trip_albums")
    suspend fun clearAll()

    @Query("DELETE FROM trip_albums WHERE isManual = 0 AND tripKey NOT IN (:tripKeys)")
    suspend fun deleteAutoTripsExcept(tripKeys: List<String>)

    @Query("DELETE FROM trip_albums WHERE isManual = 0")
    suspend fun deleteAllAutoTrips()

    @Query("SELECT * FROM trip_albums WHERE id = :id")
    suspend fun getTripAlbumById(id: Long): TripAlbumEntity?

    @Query("SELECT * FROM trip_albums WHERE tripKey = :tripKey LIMIT 1")
    suspend fun getTripAlbumByKey(tripKey: String): TripAlbumEntity?

    @Query("UPDATE trip_albums SET customTitle = :customTitle, isUserEdited = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCustomTitle(id: Long, customTitle: String, updatedAt: Long)

    @Query("UPDATE trip_albums SET isHidden = :isHidden, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateHidden(id: Long, isHidden: Boolean, updatedAt: Long)
}
