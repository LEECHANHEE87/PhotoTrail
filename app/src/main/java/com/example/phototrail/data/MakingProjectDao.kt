package com.example.phototrail.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MakingProjectDao {
    @Query("SELECT * FROM making_projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<MakingProjectEntity>>

    @Query("SELECT * FROM making_projects WHERE tripKey = :tripKey LIMIT 1")
    suspend fun getProject(tripKey: String): MakingProjectEntity?

    @Upsert
    suspend fun upsert(project: MakingProjectEntity)
}

@Dao
interface TripVideoLinkDao {
    @Query("SELECT * FROM trip_video_links ORDER BY createdAt ASC")
    fun getAllLinks(): Flow<List<TripVideoLinkEntity>>

    @Query("SELECT * FROM trip_video_links WHERE tripKey = :tripKey ORDER BY createdAt ASC")
    fun getLinksForTrip(tripKey: String): Flow<List<TripVideoLinkEntity>>

    @Upsert
    suspend fun upsert(link: TripVideoLinkEntity)
}
