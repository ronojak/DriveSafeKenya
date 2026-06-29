package com.drivesafe.kenya.alerts

class CameraWarningStateTracker(
    private val maxProximityWarningsPerEntry: Int = 2
) {
    private val proximityWarningCounts = mutableMapOf<String, Int>()
    private val activeCameraZoneIds = mutableSetOf<String>()

    fun shouldWarnProximity(cameraId: String): Boolean {
        val count = proximityWarningCounts.getOrDefault(cameraId, 0)
        if (count >= maxProximityWarningsPerEntry) return false
        proximityWarningCounts[cameraId] = count + 1
        activeCameraZoneIds.add(cameraId)
        return true
    }

    fun updateNearbyZones(currentNearbyCameraIds: Set<String>) {
        val exitedZones = activeCameraZoneIds - currentNearbyCameraIds
        exitedZones.forEach { id -> proximityWarningCounts.remove(id) }
        activeCameraZoneIds.removeAll(exitedZones)
    }

    fun reset() {
        proximityWarningCounts.clear()
        activeCameraZoneIds.clear()
    }
}
