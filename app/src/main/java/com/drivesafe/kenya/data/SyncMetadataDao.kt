package com.drivesafe.kenya.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE id = 1")
    fun get(): Flow<SyncMetadata?>

    @Query("SELECT * FROM sync_metadata WHERE id = 1")
    suspend fun getOnce(): SyncMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: SyncMetadata)
}
