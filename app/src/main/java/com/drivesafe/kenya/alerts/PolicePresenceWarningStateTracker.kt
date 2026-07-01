package com.drivesafe.kenya.alerts

class PolicePresenceWarningStateTracker(
    private val maxWarningsPerEntry: Int = 2
) {
    private val warningCounts = mutableMapOf<String, Int>()
    private val activeAlertIds = mutableSetOf<String>()

    fun shouldWarn(alertId: String): Boolean {
        val count = warningCounts.getOrDefault(alertId, 0)
        if (count >= maxWarningsPerEntry) return false
        warningCounts[alertId] = count + 1
        activeAlertIds.add(alertId)
        return true
    }

    fun updateNearbyAlerts(currentNearbyAlertIds: Set<String>) {
        val exited = activeAlertIds - currentNearbyAlertIds
        exited.forEach { id ->
            warningCounts.remove(id)
            activeAlertIds.remove(id)
        }
    }

    fun reset() {
        warningCounts.clear()
        activeAlertIds.clear()
    }
}
