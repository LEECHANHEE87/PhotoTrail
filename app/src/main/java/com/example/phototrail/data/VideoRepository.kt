package com.example.phototrail.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.example.phototrail.camera.CapturedMedia
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

class VideoRepository(
    private val context: Context,
    database: PhotoDatabase = PhotoDatabase.getInstance(context)
) {
    private val dao = database.videoItemDao()
    private val metadataReader = VideoMetadataReader(context)

    val allVideos = dao.getAllVideos()

    suspend fun indexVideos(onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }): VideoIndexStats {
        val existing = dao.getAllVideosSync().associateBy { it.mediaStoreId }
        val videos = mutableListOf<VideoItemEntity>()
        val resolver = context.contentResolver

        resolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            PROJECTION,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val total = cursor.count
            while (cursor.moveToNext()) {
                val mediaStoreId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
                videos += cursor.toEntity(
                    uri = uri,
                    existing = existing[mediaStoreId],
                    captured = null
                )
                onProgress(cursor.position + 1, total)
            }
        }

        if (videos.isNotEmpty()) videos.chunked(200).forEach { dao.upsertAll(it) }
        return VideoIndexStats(indexedCount = videos.size)
    }

    suspend fun registerCapturedVideo(captured: CapturedMedia): VideoItemEntity? {
        if (captured.type != com.example.phototrail.camera.CaptureType.VIDEO) return null
        val entity = context.contentResolver.query(captured.uri, PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val mediaStoreId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
            cursor.toEntity(
                uri = captured.uri,
                existing = dao.getByMediaStoreId(mediaStoreId),
                captured = captured
            )
        } ?: return null
        dao.upsertAll(listOf(entity))
        return entity
    }

    suspend fun setFavorite(mediaStoreId: Long, favorite: Boolean) {
        dao.updateFavorite(mediaStoreId, favorite)
    }

    private fun Cursor.toEntity(
        uri: Uri,
        existing: VideoItemEntity?,
        captured: CapturedMedia?
    ): VideoItemEntity {
        val now = System.currentTimeMillis()
        val mediaStoreId = getLong(getColumnIndexOrThrow(MediaStore.Video.Media._ID))
        val dateAdded = longOrNull(MediaStore.Video.Media.DATE_ADDED)?.times(1_000L)
        val dateModified = longOrNull(MediaStore.Video.Media.DATE_MODIFIED)?.times(1_000L)
        val takenAt = longOrNull(MediaStore.Video.Media.DATE_TAKEN) ?: dateAdded ?: now
        val extracted = metadataReader.read(uri)
        val latitude = captured?.latitude ?: extracted.latitude
        val longitude = captured?.longitude ?: extracted.longitude
        val dateKey = DATE_FORMAT.get()!!.format(Date(takenAt))
        val hasLocation = latitude != null && longitude != null
        val bucketKey = if (hasLocation) {
            val roundedLatitude = round(latitude / BUCKET_SIZE) * BUCKET_SIZE
            val roundedLongitude = round(longitude / BUCKET_SIZE) * BUCKET_SIZE
            String.format(Locale.US, "%s_%.3f_%.3f", dateKey, roundedLatitude, roundedLongitude)
        } else {
            "${dateKey}_NO_LOCATION"
        }

        return VideoItemEntity(
            id = mediaStoreId,
            mediaStoreId = mediaStoreId,
            uri = uri.toString(),
            displayName = stringOrNull(MediaStore.Video.Media.DISPLAY_NAME),
            takenAt = takenAt,
            latitude = latitude,
            longitude = longitude,
            hasLocation = hasLocation,
            durationMs = captured?.durationMs
                ?: longOrNull(MediaStore.Video.Media.DURATION)
                ?: extracted.durationMs
                ?: 0L,
            width = intOrNull(MediaStore.Video.Media.WIDTH) ?: extracted.width ?: 0,
            height = intOrNull(MediaStore.Video.Media.HEIGHT) ?: extracted.height ?: 0,
            fps = extracted.fps,
            codec = extracted.codec,
            fileSize = longOrNull(MediaStore.Video.Media.SIZE) ?: 0L,
            dateKey = dateKey,
            bucketKey = bucketKey,
            favorite = existing?.favorite ?: false,
            syncStatus = existing?.syncStatus ?: "LOCAL_ONLY",
            remotePath = existing?.remotePath,
            checksum = existing?.checksum,
            dateAdded = dateAdded,
            dateModified = dateModified,
            indexedAt = existing?.indexedAt ?: now,
            lastSeenAt = now
        )
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getLong(index).takeIf { it > 0L }
    }

    private fun Cursor.intOrNull(column: String): Int? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getInt(index).takeIf { it > 0 }
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private companion object {
        const val BUCKET_SIZE = 0.004
        val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }
        val PROJECTION = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE
        )
    }
}

data class VideoIndexStats(val indexedCount: Int)
