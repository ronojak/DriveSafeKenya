package com.drivesafe.kenya.alerts

import com.drivesafe.kenya.data.CameraZone
import org.junit.Assert.assertEquals
import org.junit.Test

class OverspeedDetectorTest {

    @Test
    fun speedBelowLimit_isWithinLimit() {
        val result = OverspeedDetector.check(
            currentSpeedKmh = 79.0,
            zone = cameraZone(speedLimitKmh = 80),
            toleranceKmh = 5
        )

        assertEquals(OverspeedStatus.WITHIN_LIMIT, result.status)
    }

    @Test
    fun speedEqualToLimitPlusTolerance_isWithinLimit() {
        val result = OverspeedDetector.check(
            currentSpeedKmh = 85.0,
            zone = cameraZone(speedLimitKmh = 80),
            toleranceKmh = 5
        )

        assertEquals(OverspeedStatus.WITHIN_LIMIT, result.status)
    }

    @Test
    fun speedAboveLimitPlusTolerance_isOverspeeding() {
        val result = OverspeedDetector.check(
            currentSpeedKmh = 86.0,
            zone = cameraZone(speedLimitKmh = 80),
            toleranceKmh = 5
        )

        assertEquals(OverspeedStatus.OVERSPEED, result.status)
        assertEquals(80, result.applicableLimitKmh)
        assertEquals(5, result.toleranceKmh)
    }

    @Test
    fun variableSpeedLimit_usesMaximumLimit() {
        val result = OverspeedDetector.check(
            currentSpeedKmh = 106.0,
            zone = cameraZone(
                speedLimitKmh = 100,
                minSpeedLimitKmh = 80,
                maxSpeedLimitKmh = 100
            ),
            toleranceKmh = 5
        )

        assertEquals(OverspeedStatus.OVERSPEED, result.status)
        assertEquals(100, result.applicableLimitKmh)
    }

    @Test
    fun missingSpeedLimit_isWithinLimit() {
        val result = OverspeedDetector.check(
            currentSpeedKmh = 120.0,
            zone = cameraZone(speedLimitKmh = null, maxSpeedLimitKmh = null),
            toleranceKmh = 5
        )

        assertEquals(OverspeedStatus.WITHIN_LIMIT, result.status)
    }

    private fun cameraZone(
        speedLimitKmh: Int?,
        minSpeedLimitKmh: Int? = null,
        maxSpeedLimitKmh: Int? = speedLimitKmh
    ) = CameraZone(
        id = "zone",
        roadName = "Thika Superhighway",
        locationName = "Unit Test Zone",
        latitude = -1.22529,
        longitude = 36.88343,
        speedLimitKmh = speedLimitKmh,
        minSpeedLimitKmh = minSpeedLimitKmh,
        maxSpeedLimitKmh = maxSpeedLimitKmh,
        warningRadiusMeters = 3000,
        cameraType = "speed_camera_zone",
        direction = "unknown",
        status = "active",
        verified = true,
        source = "unit_test",
        lastUpdated = "2026-06-29"
    )
}
