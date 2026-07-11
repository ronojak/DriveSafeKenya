package com.drivesafe.kenya.alerts

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Session-scoped, bearing-aware proximity trigger engine.
 * Pure Kotlin (no Android framework deps) so it's directly unit-testable,
 * matching the style of [PolicePresenceProximityDetector].
 */
class ProximityAlertEngine {

    private val alertedIds = mutableSetOf<String>()
    private val escalatedIds = mutableSetOf<String>()

    sealed class Trigger {
        data class Initial(val alertId: String, val distanceMeters: Float) : Trigger()
        data class Escalation(val alertId: String, val distanceMeters: Float) : Trigger()
    }

    fun evaluate(
        driverLat: Double,
        driverLng: Double,
        speedMps: Float,
        bearingDeg: Float,
        hasBearing: Boolean,
        alerts: List<PolicePresenceAlert>
    ): List<Trigger> {
        val triggers = mutableListOf<Trigger>()
        val hasReliableCourse = hasBearing && speedMps >= MIN_SPEED_FOR_BEARING_MPS

        for (alert in alerts) {
            if (!alert.isActive()) continue
            val distance = distanceMeters(driverLat, driverLng, alert.latitude, alert.longitude)

            if (alert.id !in alertedIds) {
                val shouldTrigger = if (hasReliableCourse) {
                    val bearingToAlert = bearingTo(driverLat, driverLng, alert.latitude, alert.longitude)
                    val withinCone = angularDifference(bearingToAlert, bearingDeg.toDouble()) <= BEARING_CONE_DEGREES
                    distance <= CONE_TRIGGER_RADIUS_METERS && withinCone
                } else {
                    distance <= FALLBACK_TRIGGER_RADIUS_METERS
                }
                if (shouldTrigger) {
                    alertedIds.add(alert.id)
                    triggers.add(Trigger.Initial(alert.id, distance.toFloat()))
                }
            } else if (alert.id !in escalatedIds && distance <= ESCALATION_RADIUS_METERS) {
                escalatedIds.add(alert.id)
                triggers.add(Trigger.Escalation(alert.id, distance.toFloat()))
            }
        }
        return triggers
    }

    fun hasAlerted(alertId: String): Boolean = alertId in alertedIds

    fun reset() {
        alertedIds.clear()
        escalatedIds.clear()
    }

    companion object {
        const val CONE_TRIGGER_RADIUS_METERS = 2_000.0
        const val FALLBACK_TRIGGER_RADIUS_METERS = 1_000.0
        const val ESCALATION_RADIUS_METERS = 400.0
        const val BEARING_CONE_DEGREES = 60.0
        const val MIN_SPEED_FOR_BEARING_MPS = 3f
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
        }

        fun bearingTo(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val deltaLambda = Math.toRadians(lng2 - lng1)
            val y = sin(deltaLambda) * cos(phi2)
            val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
            val theta = Math.toDegrees(atan2(y, x))
            return (theta + 360.0) % 360.0
        }

        fun angularDifference(a: Double, b: Double): Double {
            var diff = Math.abs(a - b) % 360.0
            if (diff > 180.0) diff = 360.0 - diff
            return diff
        }
    }
}
