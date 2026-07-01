package com.drivesafe.kenya.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicePresenceProximityDetectorTest {

    private fun makeAlert(
        lat: Double = -1.2921,
        lng: Double = 36.8219,
        id: String = "test-1",
        status: PolicePresenceStatus = PolicePresenceStatus.ACTIVE
    ): PolicePresenceAlert {
        val now = System.currentTimeMillis()
        return PolicePresenceAlert(
            id = id,
            latitude = lat,
            longitude = lng,
            reportedAtEpochMillis = now,
            expiresAtEpochMillis = now + 30 * 60_000,
            confirmationRequiredAfterEpochMillis = now + 15 * 60_000,
            status = status
        )
    }

    @Test
    fun `returns null when list is empty`() {
        val result = PolicePresenceProximityDetector.findNearest(-1.2921, 36.8219, emptyList())
        assertNull(result)
    }

    @Test
    fun `returns null when all alerts are expired`() {
        val expiredAlert = makeAlert(status = PolicePresenceStatus.EXPIRED)
        val result = PolicePresenceProximityDetector.findNearest(-1.2921, 36.8219, listOf(expiredAlert))
        assertNull(result)
    }

    @Test
    fun `returns null when all alerts are not_present`() {
        val alert = makeAlert(status = PolicePresenceStatus.NOT_PRESENT)
        val result = PolicePresenceProximityDetector.findNearest(-1.2921, 36.8219, listOf(alert))
        assertNull(result)
    }

    @Test
    fun `returns active alert at same location`() {
        val alert = makeAlert(lat = -1.2921, lng = 36.8219)
        val result = PolicePresenceProximityDetector.findNearest(-1.2921, 36.8219, listOf(alert))
        assertNotNull(result)
        assertEquals("test-1", result!!.alert.id)
        assertTrue(result.isWithinWarningRadius)
    }

    @Test
    fun `alert 5km away is within 10km radius`() {
        // ~5 km north of Nairobi CBD
        val alert = makeAlert(lat = -1.2471, lng = 36.8219)
        val result = PolicePresenceProximityDetector.findNearest(-1.2921, 36.8219, listOf(alert))
        assertNotNull(result)
        assertTrue(result!!.isWithinWarningRadius)
        assertTrue(result.distanceMeters < 10_000f)
    }

    @Test
    fun `alert 15km away is outside 10km radius`() {
        // ~15 km north of Nairobi CBD
        val alert = makeAlert(lat = -1.1571, lng = 36.8219)
        val result = PolicePresenceProximityDetector.findNearest(-1.2921, 36.8219, listOf(alert))
        assertNotNull(result)        // still returned as nearest
        assertTrue(!result!!.isWithinWarningRadius)
    }

    @Test
    fun `returns nearest of two alerts`() {
        val close = makeAlert(lat = -1.2871, lng = 36.8219, id = "close")
        val far = makeAlert(lat = -1.1571, lng = 36.8219, id = "far")
        val result = PolicePresenceProximityDetector.findNearest(-1.2921, 36.8219, listOf(far, close))
        assertEquals("close", result!!.alert.id)
    }

    @Test
    fun `needsConfirmation is true when status is NEEDS_CONFIRMATION`() {
        val alert = makeAlert(status = PolicePresenceStatus.NEEDS_CONFIRMATION)
        val result = PolicePresenceProximityDetector.findNearest(-1.2921, 36.8219, listOf(alert))
        assertNotNull(result)
        assertTrue(result!!.needsConfirmation)
    }

    @Test
    fun `needsConfirmation is true when confirmationRequired time has passed`() {
        val now = System.currentTimeMillis()
        val alert = PolicePresenceAlert(
            id = "past-confirm",
            latitude = -1.2921,
            longitude = 36.8219,
            reportedAtEpochMillis = now - 20 * 60_000,
            expiresAtEpochMillis = now + 10 * 60_000,
            confirmationRequiredAfterEpochMillis = now - 5 * 60_000,  // 5 min ago
            status = PolicePresenceStatus.ACTIVE
        )
        val result = PolicePresenceProximityDetector.findNearest(-1.2921, 36.8219, listOf(alert))
        assertNotNull(result)
        assertTrue(result!!.needsConfirmation)
    }
}
