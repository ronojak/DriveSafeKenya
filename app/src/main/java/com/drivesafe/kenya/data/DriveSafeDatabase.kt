package com.drivesafe.kenya.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CameraZoneEntity::class, SyncMetadata::class],
    version = 2,
    exportSchema = false
)
abstract class DriveSafeDatabase : RoomDatabase() {

    abstract fun cameraZoneDao(): CameraZoneDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        @Volatile
        private var instance: DriveSafeDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS sync_metadata (
                        id INTEGER NOT NULL PRIMARY KEY,
                        dataVersion TEXT NOT NULL,
                        lastSyncAt INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        zoneCount INTEGER NOT NULL
                    )"""
                )
            }
        }

        fun getInstance(context: Context): DriveSafeDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DriveSafeDatabase::class.java,
                    "drivesafe.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
