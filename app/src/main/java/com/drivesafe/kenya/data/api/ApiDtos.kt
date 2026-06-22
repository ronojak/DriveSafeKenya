package com.drivesafe.kenya.data.api

import com.drivesafe.kenya.data.CameraZone

data class VersionResponse(
    val version: String?,
    val publishedAt: String?,
    val zoneCount: Int
)

data class CameraZonesResponse(
    val dataVersion: String,
    val lastUpdated: String?,
    val zones: List<CameraZoneDto>
)

data class CameraZoneDto(
    val id: String,
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

fun CameraZoneDto.toDomain() = CameraZone(
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
