package com.drivesafe.kenya.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "camera_zones")
data class CameraZoneEntity(
    @PrimaryKey val id: String,
    val roadName: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val speedLimitKmh: Int?,
    val minSpeedLimitKmh: Int?,
    val maxSpeedLimitKmh: Int?,
    val warningRadiusMeters: Int,
    val cameraType: String,
    val direction: String?,
    val status: String,
    val verified: Boolean,
    val source: String,
    val lastUpdated: String
)

fun CameraZoneEntity.toDomain() = CameraZone(
    id = id,
    roadName = roadName,
    locationName = locationName,
    latitude = latitude,
    longitude = longitude,
    speedLimitKmh = speedLimitKmh,
    minSpeedLimitKmh = minSpeedLimitKmh,
    maxSpeedLimitKmh = maxSpeedLimitKmh,
    warningRadiusMeters = warningRadiusMeters,
    cameraType = cameraType,
    direction = direction,
    status = status,
    verified = verified,
    source = source,
    lastUpdated = lastUpdated
)

fun CameraZone.toEntity() = CameraZoneEntity(
    id = id,
    roadName = roadName,
    locationName = locationName,
    latitude = latitude,
    longitude = longitude,
    speedLimitKmh = speedLimitKmh,
    minSpeedLimitKmh = minSpeedLimitKmh,
    maxSpeedLimitKmh = maxSpeedLimitKmh,
    warningRadiusMeters = warningRadiusMeters,
    cameraType = cameraType,
    direction = direction,
    status = status,
    verified = verified,
    source = source,
    lastUpdated = lastUpdated
)
