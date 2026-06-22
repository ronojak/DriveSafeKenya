package com.drivesafe.kenya.data

data class CameraZone(
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
