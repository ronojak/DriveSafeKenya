package com.drivesafe.kenya.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PolicePresenceWarningStateTrackerTest {

    private lateinit var tracker: PolicePresenceWarningStateTracker

    @Before
    fun setUp() {
        tracker = PolicePresenceWarningStateTracker(maxWarningsPerEntry = 2)
    }

    @Test
    fun `first warn for new alert returns true`() {
        assertTrue(tracker.shouldWarn("alert-1"))
    }

    @Test
    fun `second warn for same alert returns true`() {
        tracker.shouldWarn("alert-1")
        assertTrue(tracker.shouldWarn("alert-1"))
    }

    @Test
    fun `third warn for same alert returns false`() {
        tracker.shouldWarn("alert-1")
        tracker.shouldWarn("alert-1")
        assertFalse(tracker.shouldWarn("alert-1"))
    }

    @Test
    fun `after exiting and re-entering alert zone count resets`() {
        tracker.shouldWarn("alert-1")
        tracker.shouldWarn("alert-1")
        // exit the zone
        tracker.updateNearbyAlerts(emptySet())
        // re-enter
        assertTrue(tracker.shouldWarn("alert-1"))
    }

    @Test
    fun `two different alerts are tracked independently`() {
        assertTrue(tracker.shouldWarn("alert-1"))
        assertTrue(tracker.shouldWarn("alert-2"))
        assertTrue(tracker.shouldWarn("alert-1"))
        assertFalse(tracker.shouldWarn("alert-1"))  // exhausted
        assertTrue(tracker.shouldWarn("alert-2"))   // second still has one left
    }

    @Test
    fun `reset clears all state`() {
        tracker.shouldWarn("alert-1")
        tracker.shouldWarn("alert-1")
        tracker.reset()
        assertTrue(tracker.shouldWarn("alert-1"))
    }

    @Test
    fun `updateNearbyAlerts only removes exited alerts`() {
        tracker.shouldWarn("alert-1")
        tracker.shouldWarn("alert-2")
        // alert-2 exits
        tracker.updateNearbyAlerts(setOf("alert-1"))
        // alert-1 still at 1 warning
        assertTrue(tracker.shouldWarn("alert-1"))
        // alert-2 count reset
        assertTrue(tracker.shouldWarn("alert-2"))
    }
}
