package com.example.phototrail.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri

data class ExtractedVideoMetadata(
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Float? = null,
    val codec: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

class VideoMetadataReader(private val context: Context) {
    fun read(uri: Uri): ExtractedVideoMetadata {
        val retrieverValues = readRetrieverValues(uri)
        val trackValues = readTrackValues(uri)
        return ExtractedVideoMetadata(
            durationMs = retrieverValues.durationMs,
            width = retrieverValues.width ?: trackValues.width,
            height = retrieverValues.height ?: trackValues.height,
            fps = trackValues.fps,
            codec = trackValues.codec,
            latitude = retrieverValues.latitude,
            longitude = retrieverValues.longitude
        )
    }

    private fun readRetrieverValues(uri: Uri): ExtractedVideoMetadata = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val location = parseLocation(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            )
            ExtractedVideoMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull(),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull(),
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull(),
                latitude = location?.first,
                longitude = location?.second
            )
        } finally {
            retriever.release()
        }
    }.getOrDefault(ExtractedVideoMetadata())

    private fun readTrackValues(uri: Uri): ExtractedVideoMetadata = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var result = ExtractedVideoMetadata()
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.stringOrNull(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    result = ExtractedVideoMetadata(
                        width = format.intOrNull(MediaFormat.KEY_WIDTH),
                        height = format.intOrNull(MediaFormat.KEY_HEIGHT),
                        fps = format.floatOrNull(MediaFormat.KEY_FRAME_RATE),
                        codec = mime
                    )
                    break
                }
            }
            result
        } finally {
            extractor.release()
        }
    }.getOrDefault(ExtractedVideoMetadata())

    private fun parseLocation(value: String?): Pair<Double, Double>? {
        if (value.isNullOrBlank()) return null
        val match = LOCATION_PATTERN.find(value) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        return latitude to longitude
    }

    private fun MediaFormat.stringOrNull(key: String): String? =
        if (containsKey(key)) getString(key) else null

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun MediaFormat.floatOrNull(key: String): Float? {
        if (!containsKey(key)) return null
        return runCatching { getFloat(key) }.getOrElse {
            runCatching { getInteger(key).toFloat() }.getOrNull()
        }
    }

    private companion object {
        val LOCATION_PATTERN = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)")
    }
}
