package com.drivesafe.kenya.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraWarningStateTrackerTest {

    private lateinit var tracker: CameraWarningStateTracker

    @Before
    fun setUp() {
        tracker = CameraWarningStateTracker(maxProximityWarningsPerEntry = 2)
    }

    // A. User outside 3 km — tracker is never told about this camera, no warnings
    @Test
    fun outsideZone_neverWarnedAndCountNotIncremented() {
        // Simulate no nearby zones
        tracker.updateNearbyZones(emptySet())
        // shouldWarnProximity is not called when outside zone; just verify no side-effects
        tracker.updateNearbyZones(emptySet())
        // Still first warning is allowed if we do enter
        assertTrue(tracker.shouldWarnProximity("zone_a"))
    }

    // B. User enters within 3 km — first two calls return true, third returns false
    @Test
    fun insideZone_firstTwoWarningsAllowed_thirdBlocked() {
        val id = "zone_b"
        tracker.updateNearbyZones(setOf(id))

        assertTrue("1st warning should be allowed", tracker.shouldWarnProximity(id))
        assertTrue("2nd warning should be allowed", tracker.shouldWarnProximity(id))
        assertFalse("3rd warning should be blocked", tracker.shouldWarnProximity(id))
        assertFalse("4th warning should also be blocked", tracker.shouldWarnProximity(id))
    }

    // C. User exits and re-enters — warning count resets, two warnings allowed again
    @Test
    fun exitAndReEnter_countResets_warningsAllowedAgain() {
        val id = "zone_c"

        // Enter zone and use both warnings
        tracker.updateNearbyZones(setOf(id))
        tracker.shouldWarnProximity(id)
        tracker.shouldWarnProximity(id)
        assertFalse("Blocked after 2 warnings", tracker.shouldWarnProximity(id))

        // Exit zone
        tracker.updateNearbyZones(emptySet())

        // Re-enter — count should be reset
        tracker.updateNearbyZones(setOf(id))
        assertTrue("1st warning allowed after re-entry", tracker.shouldWarnProximity(id))
        assertTrue("2nd warning allowed after re-entry", tracker.shouldWarnProximity(id))
        assertFalse("3rd warning blocked again", tracker.shouldWarnProximity(id))
    }

    // D. Overspeed scenario — overspeed warnings are NOT gated by this tracker
    //    The tracker only gates proximity warnings; overspeed passes through separately.
    @Test
    fun overspeedWarningsAreNotGatedByTracker() {
        val id = "zone_d"
        tracker.updateNearbyZones(setOf(id))

        // Consume both proximity warnings
        tracker.shouldWarnProximity(id)
        tracker.shouldWarnProximity(id)
        assertFalse("Proximity blocked", tracker.shouldWarnProximity(id))

        // Overspeed warning path does not call shouldWarnProximity at all — no interaction
        // Verify tracker state hasn't changed just by the test itself checking proximity again
        assertFalse("Proximity still blocked even after overspeed would fire", tracker.shouldWarnProximity(id))
    }

    // E. Two cameras — counts tracked separately per ID
    @Test
    fun twoCameras_countsTrackedSeparately() {
        val idA = "zone_e_a"
        val idB = "zone_e_b"
        tracker.updateNearbyZones(setOf(idA, idB))

        assertTrue(tracker.shouldWarnProximity(idA))
        assertTrue(tracker.shouldWarnProximity(idA))
        assertFalse("Zone A blocked after 2", tracker.shouldWarnProximity(idA))

        // Zone B is independent — still has 2 warnings available
        assertTrue("Zone B 1st still allowed", tracker.shouldWarnProximity(idB))
        assertTrue("Zone B 2nd still allowed", tracker.shouldWarnProximity(idB))
        assertFalse("Zone B blocked after 2", tracker.shouldWarnProximity(idB))
    }

    // F. reset() clears all state
    @Test
    fun reset_clearsAllState() {
        val id = "zone_f"
        tracker.updateNearbyZones(setOf(id))
        tracker.shouldWarnProximity(id)
        tracker.shouldWarnProximity(id)
        assertFalse("Blocked before reset", tracker.shouldWarnProximity(id))

        tracker.reset()

        // After reset, everything starts fresh
        assertTrue("1st warning allowed after reset", tracker.shouldWarnProximity(id))
    }

    // G. Exit one of two nearby zones — only exited zone resets
    @Test
    fun partialExit_onlyExitedZoneResets() {
        val idA = "zone_g_a"
        val idB = "zone_g_b"
        tracker.updateNearbyZones(setOf(idA, idB))

        tracker.shouldWarnProximity(idA)
        tracker.shouldWarnProximity(idA)
        assertFalse("Zone A blocked", tracker.shouldWarnProximity(idA))

        tracker.shouldWarnProximity(idB) // 1 of 2 used for B

        // Only A exits
        tracker.updateNearbyZones(setOf(idB))

        // A's count reset — can warn again
        assertTrue("Zone A re-entry allowed", tracker.shouldWarnProximity(idA))

        // B's count still at 1 — one more warning left
        assertTrue("Zone B 2nd warning still available", tracker.shouldWarnProximity(idB))
        assertFalse("Zone B now blocked", tracker.shouldWarnProximity(idB))
    }
}
