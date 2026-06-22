package com.drivesafe.kenya.alerts

import android.location.Location
import com.drivesafe.kenya.data.CameraZone

object CameraProximityDetector {

    fun findNearest(
        userLat: Double,
        userLng: Double,
        zones: List<CameraZone>,
        warningDistanceMeters: Int = 700
    ): NearbyCameraResult? {
        val activeZones = zones.filter { it.status == "active" }
        if (activeZones.isEmpty()) return null

        val userLocation = Location("user").apply {
            latitude = userLat
            longitude = userLng
        }

        var nearestZone: CameraZone? = null
        var nearestDistance = Float.MAX_VALUE

        for (zone in activeZones) {
            val zoneLocation = Location("zone").apply {
                latitude = zone.latitude
                longitude = zone.longitude
            }
            val distance = userLocation.distanceTo(zoneLocation)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestZone = zone
            }
        }

        val zone = nearestZone ?: return null
        return NearbyCameraResult(
            zone = zone,
            distanceMeters = nearestDistance,
            isInWarningRadius = nearestDistance <= warningDistanceMeters
        )
    }
}
