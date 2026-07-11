package com.drivesafe.kenya.alerts

/**
 * Detects when a driver has entered [ENTRY_RADIUS_METERS] of an already-alerted
 * checkpoint and then started moving away from it — the moment to show the
 * "is it still there?" drive-through confirmation prompt. Session-scoped, fires
 * at most once per alert id.
 */
class DriveThroughTracker {

    private val minDistanceSeen = mutableMapOf<String, Float>()
    private val prompted = mutableSetOf<String>()

    fun onTick(
        driverLat: Double,
        driverLng: Double,
        alertedIds: Set<String>,
        alerts: List<PolicePresenceAlert>
    ): String? {
        for (alert in alerts) {
            if (alert.id !in alertedIds || alert.id in prompted) continue
            val distance = ProximityAlertEngine.distanceMeters(
                driverLat, driverLng, alert.latitude, alert.longitude
            ).toFloat()
            val seenMin = minDistanceSeen[alert.id]

            if (distance <= ENTRY_RADIUS_METERS) {
                if (seenMin == null || distance < seenMin) {
                    minDistanceSeen[alert.id] = distance
                }
            }
            if (seenMin != null && distance > seenMin + RECEDING_HYSTERESIS_METERS) {
                prompted.add(alert.id)
                return alert.id
            }
        }
        return null
    }

    fun reset() {
        minDistanceSeen.clear()
        prompted.clear()
    }

    companion object {
        const val ENTRY_RADIUS_METERS = 250f
        const val RECEDING_HYSTERESIS_METERS = 15f
    }
}
