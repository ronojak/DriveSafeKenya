package com.drivesafe.kenya.alerts

import com.drivesafe.kenya.data.CameraZone

object OverspeedDetector {

    fun check(currentSpeedKmh: Double, zone: CameraZone, toleranceKmh: Int = 5): OverspeedResult {
        val applicableLimit = getApplicableLimit(zone)
            ?: return OverspeedResult(OverspeedStatus.WITHIN_LIMIT, null, null)
        val isOverspeed = currentSpeedKmh > applicableLimit + toleranceKmh
        return OverspeedResult(
            status = if (isOverspeed) OverspeedStatus.OVERSPEED else OverspeedStatus.WITHIN_LIMIT,
            applicableLimitKmh = applicableLimit,
            toleranceKmh = toleranceKmh
        )
    }

    private fun getApplicableLimit(zone: CameraZone): Int? {
        val min = zone.minSpeedLimitKmh
        val max = zone.maxSpeedLimitKmh
        if (min != null && max != null && min != max) return max
        return zone.speedLimitKmh ?: max
    }
}
