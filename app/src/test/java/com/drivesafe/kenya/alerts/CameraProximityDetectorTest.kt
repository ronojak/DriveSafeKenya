package com.drivesafe.kenya.alerts

import com.drivesafe.kenya.data.CameraZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraProximityDetectorTest {

    @Test
    fun userInsideWarningRadius_returnsNearbyCameraInRange() {
        val zone = cameraZone(
            id = "safari_park",
            latitude = -1.22529,
            longitude = 36.88343,
            warningRadiusMeters = 3000
        )

        val result = CameraProximityDetector.findNearest(
            userLat = -1.22629,
            userLng = 36.88443,
            zones = listOf(zone)
        )

        assertNotNull(result)
        assertTrue(result!!.isInWarningRadius)
    }

    @Test
    fun userOutsideWarningRadius_returnsNearestCameraOutsideRange() {
        val zone = cameraZone(
            id = "safari_park",
            latitude = -1.22529,
            longitude = 36.88343,
            warningRadiusMeters = 3000
        )

        val result = CameraProximityDetector.findNearest(
            userLat = -1.28529,
            userLng = 36.88343,
            zones = listOf(zone)
        )

        assertNotNull(result)
        assertFalse(result!!.isInWarningRadius)
    }

    @Test
    fun nearestCameraIsSelectedFromActiveZones() {
        val fartherZone = cameraZone(
            id = "farther",
            latitude = -1.26000,
            longitude = 36.84000
        )
        val nearerZone = cameraZone(
            id = "nearer",
            latitude = -1.22529,
            longitude = 36.88343
        )

        val result = CameraProximityDetector.findNearest(
            userLat = -1.22600,
            userLng = 36.88400,
            zones = listOf(fartherZone, nearerZone)
        )

        assertEquals("nearer", result?.zone?.id)
    }

    @Test
    fun emptyCameraList_returnsNull() {
        assertNull(CameraProximityDetector.findNearest(-1.22529, 36.88343, emptyList()))
    }

    @Test
    fun invalidCameraCoordinatesAreIgnored() {
        val invalidZone = cameraZone(
            id = "invalid",
            latitude = 999.0,
            longitude = 999.0
        )

        val result = CameraProximityDetector.findNearest(
            userLat = -1.22529,
            userLng = 36.88343,
            zones = listOf(invalidZone)
        )

        assertNull(result)
    }

    private fun cameraZone(
        id: String,
        latitude: Double,
        longitude: Double,
        warningRadiusMeters: Int = 3000
    ) = CameraZone(
        id = id,
        roadName = "Thika Superhighway",
        locationName = id,
        latitude = latitude,
        longitude = longitude,
        speedLimitKmh = 80,
        minSpeedLimitKmh = null,
        maxSpeedLimitKmh = 80,
        warningRadiusMeters = warningRadiusMeters,
        cameraType = "speed_camera_zone",
        direction = "unknown",
        status = "active",
        verified = true,
        source = "unit_test",
        lastUpdated = "2026-06-29"
    )
}
