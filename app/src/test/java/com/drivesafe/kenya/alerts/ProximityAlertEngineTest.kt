package com.drivesafe.kenya.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityAlertEngineTest {

    private fun makeAlert(
        id: String = "a1",
        lat: Double = -1.2921,
        lng: Double = 36.8219,
        status: PolicePresenceStatus = PolicePresenceStatus.ACTIVE
    ): PolicePresenceAlert {
        val now = System.currentTimeMillis()
        return PolicePresenceAlert(
            id = id,
            latitude = lat,
            longitude = lng,
            reportedAtEpochMillis = now,
            expiresAtEpochMillis = now + 50 * 60_000,
            confirmationRequiredAfterEpochMillis = now + 15 * 60_000,
            status = status
        )
    }

    @Test
    fun `distanceMeters matches known Nairobi pair`() {
        // -1.2921,36.8219 (CBD) to -1.2471,36.8219 (~5km north)
        val d = ProximityAlertEngine.distanceMeters(-1.2921, 36.8219, -1.2471, 36.8219)
        assertTrue("expected ~5000m, got $d", d in 4900.0..5100.0)
    }

    @Test
    fun `bearingTo is 0 degrees due north`() {
        val bearing = ProximityAlertEngine.bearingTo(-1.2921, 36.8219, -1.2471, 36.8219)
        assertTrue("expected ~0 deg, got $bearing", bearing < 1.0 || bearing > 359.0)
    }

    @Test
    fun `bearingTo is 90 degrees due east`() {
        val bearing = ProximityAlertEngine.bearingTo(-1.2921, 36.8219, -1.2921, 36.8719)
        assertTrue("expected ~90 deg, got $bearing", bearing in 89.0..91.0)
    }

    @Test
    fun `angularDifference wraps around 0-360 boundary`() {
        assertEquals(20.0, ProximityAlertEngine.angularDifference(350.0, 10.0), 0.001)
    }

    @Test
    fun `angularDifference of identical bearings is zero`() {
        assertEquals(0.0, ProximityAlertEngine.angularDifference(180.0, 180.0), 0.001)
    }

    @Test
    fun `triggers initial alert within 2km when alert is dead ahead`() {
        val engine = ProximityAlertEngine()
        // Alert ~1.5km due north; driver heading due north (bearing 0) at 10 m/s
        val alert = makeAlert(lat = -1.2786, lng = 36.8219)
        val triggers = engine.evaluate(
            driverLat = -1.2921, driverLng = 36.8219,
            speedMps = 10f, bearingDeg = 0f, hasBearing = true,
            alerts = listOf(alert)
        )
        assertEquals(1, triggers.size)
        assertTrue(triggers[0] is ProximityAlertEngine.Trigger.Initial)
    }

    @Test
    fun `does not trigger within 2km when alert is behind driver`() {
        val engine = ProximityAlertEngine()
        // Alert ~1.5km due north; driver heading due SOUTH (bearing 180) — alert is behind
        val alert = makeAlert(lat = -1.2786, lng = 36.8219)
        val triggers = engine.evaluate(
            driverLat = -1.2921, driverLng = 36.8219,
            speedMps = 10f, bearingDeg = 180f, hasBearing = true,
            alerts = listOf(alert)
        )
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `falls back to distance-only trigger at 1000m when no reliable course`() {
        val engine = ProximityAlertEngine()
        // Alert ~800m away, driver stationary (speed below threshold) so course is unreliable
        val alert = makeAlert(lat = -1.2849, lng = 36.8219)
        val triggers = engine.evaluate(
            driverLat = -1.2921, driverLng = 36.8219,
            speedMps = 0f, bearingDeg = 0f, hasBearing = false,
            alerts = listOf(alert)
        )
        assertEquals(1, triggers.size)
        assertTrue(triggers[0] is ProximityAlertEngine.Trigger.Initial)
    }

    @Test
    fun `distance-only fallback does not trigger beyond 1000m`() {
        val engine = ProximityAlertEngine()
        // Alert ~1.5km away, no reliable course
        val alert = makeAlert(lat = -1.2786, lng = 36.8219)
        val triggers = engine.evaluate(
            driverLat = -1.2921, driverLng = 36.8219,
            speedMps = 0f, bearingDeg = 0f, hasBearing = false,
            alerts = listOf(alert)
        )
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `escalates once at 400m after initial trigger`() {
        val engine = ProximityAlertEngine()
        val alert = makeAlert(lat = -1.2786, lng = 36.8219)
        // First tick: 1.5km ahead, triggers Initial
        engine.evaluate(-1.2921, 36.8219, 10f, 0f, true, listOf(alert))
        // Second tick: now 300m away (still ahead), should escalate
        val closeAlert = makeAlert(lat = -1.2948, lng = 36.8219)
        val triggers = engine.evaluate(-1.2921, 36.8219, 10f, 0f, true, listOf(closeAlert.copy(id = alert.id)))
        assertEquals(1, triggers.size)
        assertTrue(triggers[0] is ProximityAlertEngine.Trigger.Escalation)
    }

    @Test
    fun `never re-alerts the same id twice in initial phase`() {
        val engine = ProximityAlertEngine()
        val alert = makeAlert(lat = -1.2786, lng = 36.8219)
        val first = engine.evaluate(-1.2921, 36.8219, 10f, 0f, true, listOf(alert))
        val second = engine.evaluate(-1.2921, 36.8219, 10f, 0f, true, listOf(alert))
        assertEquals(1, first.size)
        assertTrue(second.isEmpty())
    }

    @Test
    fun `hasAlerted reflects prior trigger`() {
        val engine = ProximityAlertEngine()
        val alert = makeAlert(lat = -1.2786, lng = 36.8219)
        assertTrue(!engine.hasAlerted(alert.id))
        engine.evaluate(-1.2921, 36.8219, 10f, 0f, true, listOf(alert))
        assertTrue(engine.hasAlerted(alert.id))
    }

    @Test
    fun `reset clears alerted and escalated state`() {
        val engine = ProximityAlertEngine()
        val alert = makeAlert(lat = -1.2786, lng = 36.8219)
        engine.evaluate(-1.2921, 36.8219, 10f, 0f, true, listOf(alert))
        engine.reset()
        assertTrue(!engine.hasAlerted(alert.id))
    }

    @Test
    fun `ignores alerts that are not active`() {
        val engine = ProximityAlertEngine()
        val alert = makeAlert(lat = -1.2786, lng = 36.8219, status = PolicePresenceStatus.EXPIRED)
        val triggers = engine.evaluate(-1.2921, 36.8219, 10f, 0f, true, listOf(alert))
        assertTrue(triggers.isEmpty())
    }
}
