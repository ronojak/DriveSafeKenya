package com.drivesafe.kenya.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadata(
    @PrimaryKey val id: Int = 1,
    val dataVersion: String,
    val lastSyncAt: Long,
    val source: String,
    val zoneCount: Int
)
