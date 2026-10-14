package com.example.phototrail.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PhotoItemEntity::class,
        VideoItemEntity::class,
        TripAlbumEntity::class,
        TripPhotoOverrideEntity::class,
        MakingProjectEntity::class,
        TripVideoLinkEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PhotoDatabase : RoomDatabase() {
    abstract fun photoItemDao(): PhotoItemDao
    abstract fun tripAlbumDao(): TripAlbumDao
    abstract fun tripPhotoOverrideDao(): TripPhotoOverrideDao
    abstract fun videoItemDao(): VideoItemDao
    abstract fun makingProjectDao(): MakingProjectDao
    abstract fun tripVideoLinkDao(): TripVideoLinkDao

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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS videos (
                        id INTEGER NOT NULL PRIMARY KEY,
                        mediaStoreId INTEGER NOT NULL,
                        uri TEXT NOT NULL,
                        displayName TEXT,
                        takenAt INTEGER,
                        latitude REAL,
                        longitude REAL,
                        hasLocation INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        fps REAL,
                        codec TEXT,
                        fileSize INTEGER NOT NULL,
                        dateKey TEXT NOT NULL,
                        bucketKey TEXT NOT NULL,
                        favorite INTEGER NOT NULL,
                        syncStatus TEXT NOT NULL,
                        remotePath TEXT,
                        checksum TEXT,
                        dateAdded INTEGER,
                        dateModified INTEGER,
                        indexedAt INTEGER NOT NULL,
                        lastSeenAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS making_projects (
                        tripKey TEXT NOT NULL PRIMARY KEY,
                        videoQuality TEXT NOT NULL,
                        orientation TEXT NOT NULL,
                        audioEnabled INTEGER NOT NULL,
                        showGrid INTEGER NOT NULL,
                        clipDurationMs INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trip_video_links (
                        tripKey TEXT NOT NULL,
                        videoMediaStoreId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(tripKey, videoMediaStoreId)
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build().also { instance = it }
            }
    }
}
