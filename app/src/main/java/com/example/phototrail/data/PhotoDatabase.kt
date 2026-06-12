package com.example.phototrail.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PhotoItemEntity::class, TripAlbumEntity::class, TripPhotoOverrideEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoItemDao(): PhotoItemDao
    abstract fun tripAlbumDao(): TripAlbumDao
    abstract fun tripPhotoOverrideDao(): TripPhotoOverrideDao

    companion object {
        @Volatile
        private var instance: PhotoDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN mediaStoreId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN dateAdded INTEGER")
                db.execSQL("ALTER TABLE photos ADD COLUMN dateModified INTEGER")
                db.execSQL("ALTER TABLE photos ADD COLUMN indexedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE photos ADD COLUMN lastSeenAt INTEGER NOT NULL DEFAULT 0")
                
                // Set mediaStoreId to id for existing records if id was the MediaStore ID
                db.execSQL("UPDATE photos SET mediaStoreId = id")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trip_albums (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        startDateKey TEXT NOT NULL,
                        endDateKey TEXT NOT NULL,
                        dateKeys TEXT NOT NULL,
                        photoCount INTEGER NOT NULL,
                        locationPhotoCount INTEGER NOT NULL,
                        noLocationPhotoCount INTEGER NOT NULL,
                        placeGroupCount INTEGER NOT NULL,
                        representativePhotoUri TEXT,
                        centerLatitude REAL,
                        centerLongitude REAL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop and recreate because schema changed significantly and it's a new feature
                db.execSQL("DROP TABLE IF EXISTS trip_albums")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trip_albums (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tripKey TEXT NOT NULL,
                        generatedTitle TEXT NOT NULL,
                        customTitle TEXT,
                        isUserEdited INTEGER NOT NULL DEFAULT 0,
                        isHidden INTEGER NOT NULL DEFAULT 0,
                        startDateKey TEXT NOT NULL,
                        endDateKey TEXT NOT NULL,
                        dateKeys TEXT NOT NULL,
                        photoCount INTEGER NOT NULL,
                        locationPhotoCount INTEGER NOT NULL,
                        noLocationPhotoCount INTEGER NOT NULL,
                        placeGroupCount INTEGER NOT NULL,
                        representativePhotoUri TEXT,
                        centerLatitude REAL,
                        centerLongitude REAL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trip_albums ADD COLUMN isManual INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trip_albums ADD COLUMN sourceTripKeys TEXT")
                db.execSQL("ALTER TABLE trip_albums ADD COLUMN mergedIntoTripKey TEXT")
                db.execSQL("ALTER TABLE trip_albums ADD COLUMN isSplitFromTripKey TEXT")
                db.execSQL("ALTER TABLE trip_albums ADD COLUMN customRepresentativePhotoUri TEXT")
                db.execSQL("ALTER TABLE trip_albums ADD COLUMN isRepresentativeUserSelected INTEGER NOT NULL DEFAULT 0")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trip_photo_overrides (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tripKey TEXT NOT NULL,
                        photoMediaStoreId INTEGER NOT NULL,
                        overrideType TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): PhotoDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotoDatabase::class.java,
                    "photo-trail.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration() // Backup if migration fails
                    .build().also { instance = it }
            }
    }
}
