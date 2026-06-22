package com.drivesafe.kenya.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraZoneDao {

    @Query("SELECT * FROM camera_zones WHERE status = 'active'")
    fun getAllActive(): Flow<List<CameraZoneEntity>>

    @Query("SELECT COUNT(*) FROM camera_zones")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(zones: List<CameraZoneEntity>)
}
