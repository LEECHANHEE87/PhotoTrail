package com.example.phototrail.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PhotoItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoItemDao(): PhotoItemDao

    companion object {
        @Volatile
        private var instance: PhotoDatabase? = null

        fun getInstance(context: Context): PhotoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotoDatabase::class.java,
                    "photo-trail.db"
                ).build().also { instance = it }
            }
    }
}
