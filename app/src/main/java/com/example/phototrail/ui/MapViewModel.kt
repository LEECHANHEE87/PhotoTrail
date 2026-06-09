package com.example.phototrail.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.phototrail.model.PhotoItem
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel : ViewModel() {
    private val _photos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val photos: StateFlow<List<PhotoItem>> = _photos

    private val _noLocationCount = MutableStateFlow(0)
    val noLocationCount: StateFlow<Int> = _noLocationCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadPhotos(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            val fetchedPhotos = withContext(Dispatchers.IO) {
                fetchPhotosFromMediaStore(context)
            }
            _photos.value = fetchedPhotos.filter { it.location != null }
            _noLocationCount.value = fetchedPhotos.count { it.location == null }
            _isLoading.value = false
        }
    }

    private fun fetchPhotosFromMediaStore(context: Context): List<PhotoItem> {
        val photoList = mutableListOf<PhotoItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val query = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val date = cursor.getLong(dateColumn)
                val name = cursor.getString(nameColumn)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                val location = getExifLocation(context.contentResolver, contentUri)
                photoList.add(PhotoItem(contentUri, date, location, name))
            }
        }
        return photoList
    }

    private fun getExifLocation(contentResolver: ContentResolver, uri: Uri): LatLng? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val latLong = exif.latLong
                if (latLong != null) {
                    LatLng(latLong[0], latLong[1])
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
