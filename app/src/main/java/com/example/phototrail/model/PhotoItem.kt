package com.example.phototrail.model

import android.net.Uri
import com.google.android.gms.maps.model.LatLng

data class PhotoItem(
    val uri: Uri,
    val dateTaken: Long,
    val location: LatLng?,
    val fileName: String? = null
)
