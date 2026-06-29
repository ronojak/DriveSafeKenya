package com.drivesafe.kenya.alerts

import com.drivesafe.kenya.data.CameraZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object CameraProximityDetector {

    fun findNearest(
        userLat: Double,
        userLng: Double,
        zones: List<CameraZone>
    ): NearbyCameraResult? {
        if (!isValidCoordinate(userLat, userLng)) return null

        val activeZones = zones.filter {
            it.status == "active" && isValidCoordinate(it.latitude, it.longitude)
        }
        if (activeZones.isEmpty()) return null

        var nearestZone: CameraZone? = null
        var nearestDistance = Double.MAX_VALUE

        for (zone in activeZones) {
            val distance = distanceMeters(userLat, userLng, zone.latitude, zone.longitude)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestZone = zone
            }
        }

        val zone = nearestZone ?: return null
        return NearbyCameraResult(
            zone = zone,
            distanceMeters = nearestDistance.toFloat(),
            isInWarningRadius = nearestDistance <= zone.warningRadiusMeters
        )
    }

    private fun distanceMeters(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): Double {
        val latDistance = Math.toRadians(endLat - startLat)
        val lngDistance = Math.toRadians(endLng - startLng)
        val startLatRad = Math.toRadians(startLat)
        val endLatRad = Math.toRadians(endLat)

        val a = sin(latDistance / 2).pow(2) +
            cos(startLatRad) * cos(endLatRad) * sin(lngDistance / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
