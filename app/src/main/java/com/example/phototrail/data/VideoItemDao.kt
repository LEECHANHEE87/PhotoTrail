package com.example.phototrail.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoItemDao {
    @Upsert
    suspend fun upsertAll(videos: List<VideoItemEntity>)

    @Query("SELECT * FROM videos ORDER BY takenAt DESC, id DESC")
    fun getAllVideos(): Flow<List<VideoItemEntity>>

    @Query("SELECT * FROM videos ORDER BY takenAt DESC, id DESC")
    suspend fun getAllVideosSync(): List<VideoItemEntity>

    @Query("SELECT * FROM videos WHERE mediaStoreId = :mediaStoreId LIMIT 1")
    suspend fun getByMediaStoreId(mediaStoreId: Long): VideoItemEntity?

    @Query("UPDATE videos SET favorite = :favorite WHERE mediaStoreId = :mediaStoreId")
    suspend fun updateFavorite(mediaStoreId: Long, favorite: Boolean)
}
