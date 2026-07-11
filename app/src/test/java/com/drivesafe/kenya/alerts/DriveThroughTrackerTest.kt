package com.drivesafe.kenya.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveThroughTrackerTest {

    private fun makeAlert(id: String = "a1", lat: Double, lng: Double): PolicePresenceAlert {
        val now = System.currentTimeMillis()
        return PolicePresenceAlert(
            id = id,
            latitude = lat,
            longitude = lng,
            reportedAtEpochMillis = now,
            expiresAtEpochMillis = now + 50 * 60_000,
            confirmationRequiredAfterEpochMillis = now + 15 * 60_000,
            status = PolicePresenceStatus.ACTIVE
        )
    }

    @Test
    fun `does not prompt before entering 250m`() {
        val tracker = DriveThroughTracker()
        // Alert ~500m north — outside entry radius the whole time
        val alert = makeAlert(lat = -1.2876, lng = 36.8219)
        val result = tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(alert))
        assertNull(result)
    }

    @Test
    fun `does not prompt while still approaching inside 250m`() {
        val tracker = DriveThroughTracker()
        val approaching1 = makeAlert(lat = -1.29255, lng = 36.8219) // ~50m
        tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(approaching1))
        val approaching2 = makeAlert(lat = -1.29215, lng = 36.8219) // ~5m, still closing
        val result = tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(approaching2))
        assertNull(result)
    }

    @Test
    fun `prompts once distance increases past hysteresis after entering 250m`() {
        val tracker = DriveThroughTracker()
        // Tick 1: 50m away (inside 250m entry radius)
        val near = makeAlert(lat = -1.29255, lng = 36.8219)
        tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(near))
        // Tick 2: now 80m away (receded by 30m, past the hysteresis margin) — still within 250m band conceptually but moving away
        val receding = makeAlert(lat = -1.29283, lng = 36.8219)
        val result = tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(receding))
        assertEquals("a1", result)
    }

    @Test
    fun `does not re-prompt for the same alert after it already fired`() {
        val tracker = DriveThroughTracker()
        val near = makeAlert(lat = -1.29255, lng = 36.8219)
        tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(near))
        val receding = makeAlert(lat = -1.29283, lng = 36.8219)
        tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(receding))
        val stillReceding = makeAlert(lat = -1.293, lng = 36.8219)
        val second = tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(stillReceding))
        assertNull(second)
    }

    @Test
    fun `ignores alerts not in the alertedIds set`() {
        val tracker = DriveThroughTracker()
        val near = makeAlert(lat = -1.29255, lng = 36.8219)
        tracker.onTick(-1.2921, 36.8219, emptySet(), listOf(near))
        val receding = makeAlert(lat = -1.29283, lng = 36.8219)
        val result = tracker.onTick(-1.2921, 36.8219, emptySet(), listOf(receding))
        assertNull(result)
    }

    @Test
    fun `reset clears tracked state so a repeat approach can prompt again`() {
        val tracker = DriveThroughTracker()
        val near = makeAlert(lat = -1.29255, lng = 36.8219)
        tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(near))
        val receding = makeAlert(lat = -1.29283, lng = 36.8219)
        tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(receding))
        tracker.reset()
        val nearAgain = makeAlert(lat = -1.29255, lng = 36.8219)
        tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(nearAgain))
        val recedingAgain = makeAlert(lat = -1.29283, lng = 36.8219)
        val result = tracker.onTick(-1.2921, 36.8219, setOf("a1"), listOf(recedingAgain))
        assertEquals("a1", result)
    }
}
