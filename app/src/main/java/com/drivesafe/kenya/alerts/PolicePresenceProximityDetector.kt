package com.drivesafe.kenya.alerts

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object PolicePresenceProximityDetector {

    private const val WARNING_RADIUS_METERS = 10_000
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun findNearest(
        userLat: Double,
        userLng: Double,
        alerts: List<PolicePresenceAlert>
    ): NearbyPolicePresenceResult? {
        if (!isValidCoordinate(userLat, userLng)) return null

        val eligibleAlerts = alerts.filter { it.isActive() && isValidCoordinate(it.latitude, it.longitude) }
        if (eligibleAlerts.isEmpty()) return null

        var nearest: PolicePresenceAlert? = null
        var nearestDistance = Double.MAX_VALUE

        for (alert in eligibleAlerts) {
            val dist = distanceMeters(userLat, userLng, alert.latitude, alert.longitude)
            if (dist < nearestDistance) {
                nearestDistance = dist
                nearest = alert
            }
        }

        val alert = nearest ?: return null
        return NearbyPolicePresenceResult(
            alert = alert,
            distanceMeters = nearestDistance.toFloat(),
            isWithinWarningRadius = nearestDistance <= WARNING_RADIUS_METERS,
            warningRadiusMeters = WARNING_RADIUS_METERS,
            needsConfirmation = alert.needsConfirmation()
        )
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun isValidCoordinate(lat: Double, lng: Double) =
        lat in -90.0..90.0 && lng in -180.0..180.0
}
