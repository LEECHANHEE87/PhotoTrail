package com.example.phototrail.data

import android.content.Context
import androidx.room.withTransaction
import com.example.phototrail.camera.CapturedMedia

class MakingRepository(
    private val context: Context,
    private val database: PhotoDatabase = PhotoDatabase.getInstance(context)
) {
    private val projectDao = database.makingProjectDao()
    private val linkDao = database.tripVideoLinkDao()
    private val videoRepository = VideoRepository(context, database)

    val allProjects = projectDao.getAllProjects()
    val allLinks = linkDao.getAllLinks()

    suspend fun getOrCreateProject(tripKey: String): MakingProjectEntity =
        projectDao.getProject(tripKey) ?: MakingProjectEntity(tripKey = tripKey)

    suspend fun saveProject(project: MakingProjectEntity) {
        val existing = projectDao.getProject(project.tripKey)
        projectDao.upsert(
            project.copy(
                createdAt = existing?.createdAt ?: project.createdAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun registerCapturedVideo(tripKey: String, captured: CapturedMedia): VideoItemEntity? {
        val video = videoRepository.registerCapturedVideo(captured) ?: return null
        database.withTransaction {
            linkDao.upsert(
                TripVideoLinkEntity(
                    tripKey = tripKey,
                    videoMediaStoreId = video.mediaStoreId
                )
            )
        }
        return video
    }
}
