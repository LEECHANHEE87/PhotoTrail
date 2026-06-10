package com.example.phototrail.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ThumbnailManager(private val context: Context) {
    private val thumbDir = File(context.cacheDir, "marker_thumbs").apply { 
        if (!exists()) mkdirs() 
    }

    suspend fun saveThumbnail(bucketKey: String, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val fileName = "thumb_${bucketKey.hashCode()}.png"
        val file = File(thumbDir, fileName)
        return@withContext try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getThumbnail(bucketKey: String): Bitmap? = withContext(Dispatchers.IO) {
        val fileName = "thumb_${bucketKey.hashCode()}.png"
        val file = File(thumbDir, fileName)
        if (!file.exists()) return@withContext null
        return@withContext try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        thumbDir.deleteRecursively()
        thumbDir.mkdirs()
    }
}
