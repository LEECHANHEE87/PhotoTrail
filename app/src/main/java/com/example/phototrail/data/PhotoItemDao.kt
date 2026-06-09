package com.example.phototrail.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoItemDao {
    @Upsert
    suspend fun upsertAll(photos: List<PhotoItemEntity>)

    @Query("SELECT * FROM photos ORDER BY takenAt DESC, id DESC")
    fun getAllPhotos(): Flow<List<PhotoItemEntity>>

    @Query("SELECT * FROM photos WHERE hasLocation = 1 ORDER BY takenAt DESC, id DESC")
    fun getPhotosWithLocation(): Flow<List<PhotoItemEntity>>

    @Query("SELECT * FROM photos WHERE hasLocation = 0 ORDER BY takenAt DESC, id DESC")
    fun getPhotosWithoutLocation(): Flow<List<PhotoItemEntity>>

    @Query("SELECT * FROM photos WHERE dateKey = :dateKey ORDER BY takenAt DESC, id DESC")
    fun getPhotosByDate(dateKey: String): Flow<List<PhotoItemEntity>>

    @Query("SELECT * FROM photos WHERE bucketKey = :bucketKey ORDER BY takenAt DESC, id DESC")
    fun getPhotosByBucketKey(bucketKey: String): Flow<List<PhotoItemEntity>>

    @Query("DELETE FROM photos")
    suspend fun clearAll()
}
