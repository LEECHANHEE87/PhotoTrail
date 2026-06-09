package com.example.phototrail.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round

data class IndexStats(
    val totalCount: Int,
    val locationCount: Int,
    val noLocationCount: Int,
    val dateGroupCount: Int,
    val locationGroupCount: Int
)

class PhotoRepository(
    private val context: Context,
    private val database: PhotoDatabase = PhotoDatabase.getInstance(context)
) {
    private val dao = database.photoItemDao()

    val allPhotos = dao.getAllPhotos()
    val photosWithLocation = dao.getPhotosWithLocation()
    val photosWithoutLocation = dao.getPhotosWithoutLocation()

    fun getPhotosByDate(dateKey: String) = dao.getPhotosByDate(dateKey)

    fun getPhotosByBucketKey(bucketKey: String) = dao.getPhotosByBucketKey(bucketKey)

    suspend fun indexPhotos(onProgress: (processed: Int, total: Int) -> Unit): IndexStats {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )
        val photos = mutableListOf<PhotoItemEntity>()

        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val total = cursor.count
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val mediaTakenAt = cursor.longOrNull(takenColumn)
                val dateAdded = cursor.longOrNull(addedColumn)?.times(1_000L)
                val exifData = runCatching {
                    resolver.openInputStream(uri)?.use { input ->
                        val exif = ExifInterface(input)
                        ExifData(
                            takenAt = parseExifDate(
                                exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                            ),
                            location = exif.latLong?.let { it[0] to it[1] }
                        )
                    }
                }.getOrNull()

                val takenAt = mediaTakenAt ?: exifData?.takenAt ?: dateAdded
                val dateKey = DATE_KEY_FORMAT.get()!!.format(Date(takenAt ?: 0L))
                val latitude = exifData?.location?.first
                val longitude = exifData?.location?.second
                val hasLocation = latitude != null && longitude != null
                val bucketKey = if (hasLocation) {
                    val roundedLatitude = round(latitude / BUCKET_SIZE) * BUCKET_SIZE
                    val roundedLongitude = round(longitude / BUCKET_SIZE) * BUCKET_SIZE
                    String.format(
                        Locale.US,
                        "%s_%.3f_%.3f",
                        dateKey,
                        roundedLatitude,
                        roundedLongitude
                    )
                } else {
                    "${dateKey}_NO_LOCATION"
                }

                photos += PhotoItemEntity(
                    id = id,
                    uri = uri.toString(),
                    displayName = cursor.getString(nameColumn),
                    takenAt = takenAt,
                    latitude = latitude,
                    longitude = longitude,
                    hasLocation = hasLocation,
                    bucketKey = bucketKey,
                    dateKey = dateKey
                )
                onProgress(cursor.position + 1, total)
            }
        }

        database.withTransaction {
            dao.clearAll()
            photos.chunked(500).forEach { dao.upsertAll(it) }
        }

        return IndexStats(
            totalCount = photos.size,
            locationCount = photos.count(PhotoItemEntity::hasLocation),
            noLocationCount = photos.count { !it.hasLocation },
            dateGroupCount = photos.map(PhotoItemEntity::dateKey).distinct().size,
            locationGroupCount = photos.filter(PhotoItemEntity::hasLocation)
                .map(PhotoItemEntity::bucketKey)
                .distinct()
                .size
        )
    }

    private fun parseExifDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { EXIF_DATE_FORMAT.get()!!.parse(value)?.time }.getOrNull()
    }

    private fun android.database.Cursor.longOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index).takeIf { it > 0L }

    private data class ExifData(
        val takenAt: Long?,
        val location: Pair<Double, Double>?
    )

    companion object {
        private const val BUCKET_SIZE = 0.004
        private val DATE_KEY_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }
        private val EXIF_DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat(
                "yyyy:MM:dd HH:mm:ss",
                Locale.US
            )
        }
    }
}
