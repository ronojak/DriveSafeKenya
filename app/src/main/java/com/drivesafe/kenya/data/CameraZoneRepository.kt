package com.drivesafe.kenya.data

import com.drivesafe.kenya.data.api.DriveSafeApiService
import com.drivesafe.kenya.data.api.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CameraZoneRepository(
    private val dao: CameraZoneDao,
    private val syncDao: SyncMetadataDao,
    private val jsonDataSource: LocalJsonDataSource,
    private val apiService: DriveSafeApiService? = null
) {

    val activeZones: Flow<List<CameraZone>> =
        dao.getAllActive().map { entities -> entities.map { it.toDomain() } }

    val syncMetadata: Flow<SyncMetadata?> = syncDao.get()

    suspend fun seedIfEmpty() {
        if (dao.count() == 0) {
            val zones = jsonDataSource.loadCameraZones()
            dao.insertAll(zones.map { it.toEntity() })
            syncDao.upsert(
                SyncMetadata(
                    dataVersion = "1.0",
                    lastSyncAt = 0L,
                    source = "local_seed",
                    zoneCount = zones.size
                )
            )
        }
    }

    suspend fun syncFromApi(): SyncResult {
        val api = apiService ?: return SyncResult.Failed("API not configured")
        return try {
            val versionResponse = api.getVersion()
            val backendVersion = versionResponse.version
                ?: return SyncResult.Failed("No active dataset on server")

            val local = syncDao.getOnce()
            if (local != null && local.dataVersion == backendVersion) {
                return SyncResult.AlreadyUpToDate
            }

            val zonesResponse = api.getCameraZones()
            val zones = zonesResponse.zones.map { it.toDomain() }
            dao.insertAll(zones.map { it.toEntity() })
            syncDao.upsert(
                SyncMetadata(
                    dataVersion = backendVersion,
                    lastSyncAt = System.currentTimeMillis(),
                    source = "api",
                    zoneCount = zones.size
                )
            )
            SyncResult.Updated(zoneCount = zones.size, version = backendVersion)
        } catch (e: Exception) {
            SyncResult.Failed(e.message ?: "Unknown error")
        }
    }
}
