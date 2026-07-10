# Instant Proximity Alerts for Police Presence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drivers in driving mode get an instant, bearing-aware heads-up TTS alert as they approach an active police-presence report, a second escalation alert at 400m, and a "drive-through" prompt to confirm whether the checkpoint is still there after passing it — all without FCM, Celery, or websockets.

**Architecture:** Android already has a distance-only, 10km, TTS-on-poll system (`AlertManager.evaluatePolicePresence`) and a passive "needs confirmation" card shown any time within 10km (`PoliceConfirmCard` in `DrivingScreen.kt`). This plan **replaces** the TTS trigger with a new bearing-filtered `ProximityAlertEngine` (2km cone / 1000m distance-only fallback / 400m escalation, never re-alerts), **adds** a new `DriveThroughTracker` that detects "entered 250m then receded" to show a confirmation overlay, and **unifies** the backend's two existing confirm endpoints (`confirm-present`, `not-present`, cache-based dedup) into one endpoint backed by a durable `PolicePresenceConfirmation` model with 500m proximity validation and self-confirm rejection. The existing passive `PoliceConfirmCard` is kept (per hybrid decision) but gated to only show its action buttons within 500m, matching the new backend validation, so both confirm surfaces (passive card + drive-through prompt) write to the same unified endpoint/model.

**Tech Stack:** Kotlin, Jetpack Compose, FusedLocationProviderClient, Retrofit/Gson, plain JUnit4 (no Robolectric/mockk in this repo). Django, Django REST Framework, `django.core.cache` (LocMemCache, no Celery/Redis configured).

## Global Constraints

- No new infrastructure: no FCM, no Celery, no websockets, no new services.
- Do not increase GPS polling frequency (`LocationService` stays at `INTERVAL_MS = 2000L`); proximity checks are pure math on existing GPS ticks.
- Keep reports/confirmations anonymous (device hash only) — no Django `User` linkage.
- Don't break the existing 2-minute report rate limit or Kenya bounding-box validation in `report_police_presence`.
- Follow existing repo conventions: pure/testable logic lives in plain Kotlin classes under `alerts/` (no Android framework deps), matching `PolicePresenceProximityDetector`; Android tests are plain JUnit4 (`org.junit.Assert.*`), no mocking library available; Django tests use `django.test.TestCase` + `rest_framework.test.APIClient`, matching `backend/police_presence/tests.py`.
- The route `POST police-presence/<uuid:id>/confirm` already exists (`confirm_police_present`) — this plan repurposes that exact path to the new unified contract rather than adding a colliding new one.
- No `values-sw/` resource directory and no app-locale setting exist anywhere in the codebase. TTS messages stay English-only (matching the only existing TTS precedent, `Locale.US`). The drive-through prompt's two button labels use the literal bilingual text the spec provided ("Bado wapo / Still there", "Wameondoka / Gone") as plain hardcoded strings — there is no locale-switching mechanism to plug into.

---

## Phase A — Android proximity engine (pure, testable, no backend dependency)

### Task 1: Expose bearing + speed from LocationService

**Files:**
- Modify: `app/src/main/java/com/drivesafe/kenya/location/LocationService.kt`

**Interfaces:**
- Produces: `LocationService.GpsFix(latitude: Double, longitude: Double, speedMps: Float, hasSpeed: Boolean, bearingDeg: Float, hasBearing: Boolean)` and `LocationService.gpsFix: StateFlow<GpsFix?>` — consumed by Task 4 (`AlertManager`) and Task 11 (`MainActivity`).

`LocationService` currently only exposes `speedKmh: StateFlow<Double?>` and `userLocation: StateFlow<Pair<Double,Double>?>` — no bearing. The new proximity engine needs `Location.hasBearing()`/`Location.bearing` to do the cone-filter check, so this must be added first. This is additive (existing `speedKmh`/`userLocation` stay untouched) to avoid touching every existing call site.

- [ ] **Step 1: Add the `GpsFix` data class and `gpsFix` StateFlow**

Edit `app/src/main/java/com/drivesafe/kenya/location/LocationService.kt`:

```kotlin
class LocationService(context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    data class GpsFix(
        val latitude: Double,
        val longitude: Double,
        val speedMps: Float,
        val hasSpeed: Boolean,
        val bearingDeg: Float,
        val hasBearing: Boolean
    )

    private val _speedKmh = MutableStateFlow<Double?>(null)
    val speedKmh: StateFlow<Double?> = _speedKmh.asStateFlow()

    private val _userLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val userLocation: StateFlow<Pair<Double, Double>?> = _userLocation.asStateFlow()

    private val _gpsFix = MutableStateFlow<GpsFix?>(null)
    val gpsFix: StateFlow<GpsFix?> = _gpsFix.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
```

- [ ] **Step 2: Populate `_gpsFix` in the location callback**

In the same file, replace the `locationCallback` body:

```kotlin
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            _speedKmh.value = if (location.hasSpeed()) {
                location.speed.toDouble() * MS_TO_KMH
            } else {
                null
            }
            _userLocation.value = Pair(location.latitude, location.longitude)
            _gpsFix.value = GpsFix(
                latitude = location.latitude,
                longitude = location.longitude,
                speedMps = if (location.hasSpeed()) location.speed else 0f,
                hasSpeed = location.hasSpeed(),
                bearingDeg = if (location.hasBearing()) location.bearing else 0f,
                hasBearing = location.hasBearing()
            )
        }
    }
```

- [ ] **Step 3: Reset `_gpsFix` on stop**

In `stopUpdates()`:

```kotlin
    fun stopUpdates() {
        client.removeLocationUpdates(locationCallback)
        _isActive.value = false
        _speedKmh.value = null
        _userLocation.value = null
        _gpsFix.value = null
        Log.d(TAG, "Location updates stopped")
    }
```

- [ ] **Step 4: Compile check**

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:compileDebugKotlin -q`
Expected: no output, exit code 0. (`LocationService` has no existing unit test in this repo — it's a thin Android-framework wrapper, same as before this change; the new logic it feeds is unit-tested in Task 2/3.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/drivesafe/kenya/location/LocationService.kt
git commit -m "feat: expose bearing and speed from LocationService via GpsFix"
```

---

### Task 2: ProximityAlertEngine — bearing-filtered trigger logic

**Files:**
- Create: `app/src/main/java/com/drivesafe/kenya/alerts/ProximityAlertEngine.kt`
- Test: `app/src/test/java/com/drivesafe/kenya/alerts/ProximityAlertEngineTest.kt`

**Interfaces:**
- Consumes: `PolicePresenceAlert` (existing, `alerts/PolicePresenceAlert.kt`) — `id`, `latitude`, `longitude`, `isActive()`.
- Produces: `ProximityAlertEngine` class with `fun evaluate(driverLat: Double, driverLng: Double, speedMps: Float, bearingDeg: Float, hasBearing: Boolean, alerts: List<PolicePresenceAlert>): List<ProximityAlertEngine.Trigger>`, `fun hasAlerted(alertId: String): Boolean`, `fun reset()`. `Trigger` is a sealed class: `Trigger.Initial(alertId: String, distanceMeters: Float)`, `Trigger.Escalation(alertId: String, distanceMeters: Float)`. Companion object exposes `distanceMeters(...)`, `bearingTo(...)`, `angularDifference(...)` as `@JvmStatic`-style top-level functions (plain Kotlin companion functions) — consumed by Task 3 (`DriveThroughTracker` reuses `distanceMeters`) and Task 4 (`AlertManager`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/drivesafe/kenya/alerts/ProximityAlertEngineTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail (class doesn't exist yet)**

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.drivesafe.kenya.alerts.ProximityAlertEngineTest"`
Expected: FAIL — compilation error, `ProximityAlertEngine` unresolved reference.

- [ ] **Step 3: Implement `ProximityAlertEngine`**

Create `app/src/main/java/com/drivesafe/kenya/alerts/ProximityAlertEngine.kt`:

```kotlin
package com.drivesafe.kenya.alerts

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Session-scoped, bearing-aware proximity trigger engine.
 * Pure Kotlin (no Android framework deps) so it's directly unit-testable,
 * matching the style of [PolicePresenceProximityDetector].
 */
class ProximityAlertEngine {

    private val alertedIds = mutableSetOf<String>()
    private val escalatedIds = mutableSetOf<String>()

    sealed class Trigger {
        data class Initial(val alertId: String, val distanceMeters: Float) : Trigger()
        data class Escalation(val alertId: String, val distanceMeters: Float) : Trigger()
    }

    fun evaluate(
        driverLat: Double,
        driverLng: Double,
        speedMps: Float,
        bearingDeg: Float,
        hasBearing: Boolean,
        alerts: List<PolicePresenceAlert>
    ): List<Trigger> {
        val triggers = mutableListOf<Trigger>()
        val hasReliableCourse = hasBearing && speedMps >= MIN_SPEED_FOR_BEARING_MPS

        for (alert in alerts) {
            if (!alert.isActive()) continue
            val distance = distanceMeters(driverLat, driverLng, alert.latitude, alert.longitude)

            if (alert.id !in alertedIds) {
                val shouldTrigger = if (hasReliableCourse) {
                    val bearingToAlert = bearingTo(driverLat, driverLng, alert.latitude, alert.longitude)
                    val withinCone = angularDifference(bearingToAlert, bearingDeg.toDouble()) <= BEARING_CONE_DEGREES
                    distance <= CONE_TRIGGER_RADIUS_METERS && withinCone
                } else {
                    distance <= FALLBACK_TRIGGER_RADIUS_METERS
                }
                if (shouldTrigger) {
                    alertedIds.add(alert.id)
                    triggers.add(Trigger.Initial(alert.id, distance.toFloat()))
                }
            } else if (alert.id !in escalatedIds && distance <= ESCALATION_RADIUS_METERS) {
                escalatedIds.add(alert.id)
                triggers.add(Trigger.Escalation(alert.id, distance.toFloat()))
            }
        }
        return triggers
    }

    fun hasAlerted(alertId: String): Boolean = alertId in alertedIds

    fun reset() {
        alertedIds.clear()
        escalatedIds.clear()
    }

    companion object {
        const val CONE_TRIGGER_RADIUS_METERS = 2_000.0
        const val FALLBACK_TRIGGER_RADIUS_METERS = 1_000.0
        const val ESCALATION_RADIUS_METERS = 400.0
        const val BEARING_CONE_DEGREES = 60.0
        const val MIN_SPEED_FOR_BEARING_MPS = 3f
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
        }

        fun bearingTo(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val deltaLambda = Math.toRadians(lng2 - lng1)
            val y = sin(deltaLambda) * cos(phi2)
            val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
            val theta = Math.toDegrees(atan2(y, x))
            return (theta + 360.0) % 360.0
        }

        fun angularDifference(a: Double, b: Double): Double {
            var diff = Math.abs(a - b) % 360.0
            if (diff > 180.0) diff = 360.0 - diff
            return diff
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.drivesafe.kenya.alerts.ProximityAlertEngineTest"`
Expected: PASS — `14 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/drivesafe/kenya/alerts/ProximityAlertEngine.kt app/src/test/java/com/drivesafe/kenya/alerts/ProximityAlertEngineTest.kt
git commit -m "feat: add bearing-filtered ProximityAlertEngine for instant police-presence alerts"
```

---

### Task 3: DriveThroughTracker — "entered 250m then receded" detector

**Files:**
- Create: `app/src/main/java/com/drivesafe/kenya/alerts/DriveThroughTracker.kt`
- Test: `app/src/test/java/com/drivesafe/kenya/alerts/DriveThroughTrackerTest.kt`

**Interfaces:**
- Consumes: `ProximityAlertEngine.distanceMeters(...)` (Task 2), `PolicePresenceAlert`.
- Produces: `DriveThroughTracker` with `fun onTick(driverLat: Double, driverLng: Double, alertedIds: Set<String>, alerts: List<PolicePresenceAlert>): String?` (returns the alert id to prompt for, or null) and `fun reset()`. Consumed by Task 11 (`MainActivity`).

A driver "drives through" a checkpoint when they get within 250m and then distance starts increasing again. GPS jitter means "increasing" needs a small hysteresis margin, or noise alone would fire the prompt. Only alerts already surfaced via `ProximityAlertEngine` (`alertedIds`) are eligible — don't prompt for confirmation on a report the driver was never told about.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/drivesafe/kenya/alerts/DriveThroughTrackerTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.drivesafe.kenya.alerts.DriveThroughTrackerTest"`
Expected: FAIL — `DriveThroughTracker` unresolved reference.

- [ ] **Step 3: Implement `DriveThroughTracker`**

Create `app/src/main/java/com/drivesafe/kenya/alerts/DriveThroughTracker.kt`:

```kotlin
package com.drivesafe.kenya.alerts

/**
 * Detects when a driver has entered [ENTRY_RADIUS_METERS] of an already-alerted
 * checkpoint and then started moving away from it — the moment to show the
 * "is it still there?" drive-through confirmation prompt. Session-scoped, fires
 * at most once per alert id.
 */
class DriveThroughTracker {

    private val minDistanceSeen = mutableMapOf<String, Float>()
    private val prompted = mutableSetOf<String>()

    fun onTick(
        driverLat: Double,
        driverLng: Double,
        alertedIds: Set<String>,
        alerts: List<PolicePresenceAlert>
    ): String? {
        for (alert in alerts) {
            if (alert.id !in alertedIds || alert.id in prompted) continue
            val distance = ProximityAlertEngine.distanceMeters(
                driverLat, driverLng, alert.latitude, alert.longitude
            ).toFloat()
            val seenMin = minDistanceSeen[alert.id]

            if (distance <= ENTRY_RADIUS_METERS) {
                if (seenMin == null || distance < seenMin) {
                    minDistanceSeen[alert.id] = distance
                }
            } else if (seenMin != null && distance > seenMin + RECEDING_HYSTERESIS_METERS) {
                prompted.add(alert.id)
                return alert.id
            }
        }
        return null
    }

    fun reset() {
        minDistanceSeen.clear()
        prompted.clear()
    }

    companion object {
        const val ENTRY_RADIUS_METERS = 250f
        const val RECEDING_HYSTERESIS_METERS = 15f
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:testDebugUnitTest --tests "com.drivesafe.kenya.alerts.DriveThroughTrackerTest"`
Expected: PASS — `6 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/drivesafe/kenya/alerts/DriveThroughTracker.kt app/src/test/java/com/drivesafe/kenya/alerts/DriveThroughTrackerTest.kt
git commit -m "feat: add DriveThroughTracker to detect drive-past-checkpoint confirmation moments"
```

---

### Task 4: Wire the new engine into AlertManager, remove the old distance-only trigger

**Files:**
- Modify: `app/src/main/java/com/drivesafe/kenya/alerts/AlertManager.kt`
- Delete: `app/src/main/java/com/drivesafe/kenya/alerts/PolicePresenceWarningStateTracker.kt`
- Delete: `app/src/test/java/com/drivesafe/kenya/alerts/PolicePresenceWarningStateTrackerTest.kt`

**Interfaces:**
- Consumes: `ProximityAlertEngine` (Task 2), `LocationService.GpsFix` (Task 1).
- Produces: `AlertManager.evaluatePolicePresence(gpsFix: LocationService.GpsFix?, cachedAlerts: List<PolicePresenceAlert>, voiceEnabled: Boolean = true, vibrationEnabled: Boolean = true)` — **replaces** the old signature `evaluatePolicePresence(nearbyPolice: NearbyPolicePresenceResult?, ...)`. Also produces `AlertManager.hasAlertedProximity(alertId: String): Boolean`. Consumed by Task 11 (`MainActivity`).

Per the hybrid integration decision, the old 10km distance-only TTS (`PolicePresenceWarningStateTracker`-backed) is fully removed — the new bearing-filtered engine is the only TTS trigger going forward. `PolicePresenceProximityDetector` (used for the passive UI card, unaffected) stays.

- [ ] **Step 1: Delete the obsolete tracker and its test**

```bash
git rm app/src/main/java/com/drivesafe/kenya/alerts/PolicePresenceWarningStateTracker.kt
git rm app/src/test/java/com/drivesafe/kenya/alerts/PolicePresenceWarningStateTrackerTest.kt
```

- [ ] **Step 2: Replace the police-presence section of AlertManager**

In `app/src/main/java/com/drivesafe/kenya/alerts/AlertManager.kt`, replace the field declaration:

```kotlin
    private val lastAlertTimes = mutableMapOf<AlertType, Long>()
    private var lastZoneId: String? = null
    private val warningStateTracker = CameraWarningStateTracker()
    private val proximityAlertEngine = ProximityAlertEngine()
```

(removes `policeWarningStateTracker`, adds `proximityAlertEngine`)

Replace the entire `evaluatePolicePresence` function:

```kotlin
    fun evaluatePolicePresence(
        gpsFix: com.drivesafe.kenya.location.LocationService.GpsFix?,
        cachedAlerts: List<PolicePresenceAlert>,
        voiceEnabled: Boolean = true,
        vibrationEnabled: Boolean = true
    ) {
        if (gpsFix == null) return

        val triggers = proximityAlertEngine.evaluate(
            driverLat = gpsFix.latitude,
            driverLng = gpsFix.longitude,
            speedMps = gpsFix.speedMps,
            bearingDeg = gpsFix.bearingDeg,
            hasBearing = gpsFix.hasBearing,
            alerts = cachedAlerts
        )

        for (trigger in triggers) {
            when (trigger) {
                is ProximityAlertEngine.Trigger.Initial -> {
                    if (voiceEnabled) speak(buildPoliceInitialMessage(trigger.distanceMeters))
                    if (vibrationEnabled) vibrate(AlertType.POLICE_PRESENCE)
                }
                is ProximityAlertEngine.Trigger.Escalation -> {
                    if (voiceEnabled) speak(buildPoliceEscalationMessage())
                    if (vibrationEnabled) vibrate(AlertType.POLICE_PRESENCE)
                }
            }
        }
    }

    fun hasAlertedProximity(alertId: String): Boolean = proximityAlertEngine.hasAlerted(alertId)

    private fun buildPoliceInitialMessage(distanceMeters: Float): String {
        val km = distanceMeters / 1000f
        return "Police reported ${"%.1f".format(km)} kilometres ahead."
    }

    private fun buildPoliceEscalationMessage(): String = "Police 400 metres ahead."
```

Update `reset()` and `shutdown()` — replace `policeWarningStateTracker.reset()` with `proximityAlertEngine.reset()` in both.

Leave `isCooldownExpired`, `COOLDOWN_POLICE_MS`, and the `vibrate()` function's `AlertType.POLICE_PRESENCE -> VIBRATE_NEARBY` branch untouched — `COOLDOWN_POLICE_MS`/`isCooldownExpired(AlertType.POLICE_PRESENCE)` is simply no longer called (the new engine's own alerted/escalated sets replace that cooldown), but leaving the `when` branch in place avoids having to add an `else` for Kotlin's exhaustiveness check elsewhere. `POLICE_VOICE_MESSAGE` companion constant is now unused — remove it.

- [ ] **Step 3: Compile check — expect exactly one known failure**

Gradle's Kotlin compilation is whole-module: because `MainActivity.kt` still calls the old `evaluatePolicePresence(nearbyPoliceAlert, ...)` signature (fixed in Task 11), `:app:compileDebugKotlin` — and therefore `:app:testDebugUnitTest`, which depends on it — **will fail to build at all** after this task, not just report a clean pass. That is expected. Do not attempt to run `testDebugUnitTest` at this point (Task 2/3's engine/tracker tests already cover this task's actual logic in isolation, and the full suite is re-verified in Task 11 Step 7 once the module compiles again).

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|e: "`
Expected: every reported error is in `MainActivity.kt` and references `evaluatePolicePresence` (unresolved reference or argument-type mismatch against the new signature). If any error appears in `AlertManager.kt`, `ProximityAlertEngine.kt`, or `DriveThroughTracker.kt`, that is a real defect in this task — fix it before continuing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/drivesafe/kenya/alerts/AlertManager.kt
git commit -m "feat: replace 10km distance-only police TTS with bearing-filtered ProximityAlertEngine"
```

(Note: this commit intentionally leaves `MainActivity.kt` non-compiling against the new signature — Task 11 fixes it. If your workflow requires every commit to compile standalone, squash Task 4 into Task 11 instead of committing separately.)

---

## Phase B — Django backend: unified confirm endpoint + clearing logic (independently testable via APIClient)

### Task 5: Model changes — PolicePresenceConfirmation, last_confirmed_at, 50-minute TTL

**Files:**
- Modify: `backend/police_presence/models.py`
- Create: migration via `makemigrations` (exact filename generated by Django — see Step 3)
- Modify: `backend/police_presence/tests.py` (add model tests)

**Interfaces:**
- Produces: `PolicePresenceConfirmation(alert: FK[PolicePresenceAlert], device_hash: str, present: bool, latitude: float, longitude: float, created_at: datetime)` with `unique_together = ("alert", "device_hash")`; `PolicePresenceAlert.last_confirmed_at: DateTimeField(null=True)`; `expires_at` default changes from 30 to 50 minutes. Consumed by Task 7 (`views.py`).

- [ ] **Step 1: Write the failing model tests**

Add to `backend/police_presence/tests.py`, inside `PolicePresenceModelTest`:

```python
    def test_default_expiry_is_fifty_minutes(self):
        before = timezone.now()
        alert = PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219)
        expected_min = before + timezone.timedelta(minutes=49)
        expected_max = before + timezone.timedelta(minutes=51)
        self.assertTrue(expected_min <= alert.expires_at <= expected_max)

    def test_last_confirmed_at_defaults_to_none(self):
        alert = PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219)
        self.assertIsNone(alert.last_confirmed_at)
```

Add a new test class at the bottom of the file:

```python
from .models import PolicePresenceConfirmation


class PolicePresenceConfirmationModelTest(TestCase):

    def test_unique_together_alert_and_device_hash(self):
        alert = PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219)
        PolicePresenceConfirmation.objects.create(
            alert=alert, device_hash="dev1", present=True,
            latitude=-1.2921, longitude=36.8219
        )
        with self.assertRaises(Exception):
            PolicePresenceConfirmation.objects.create(
                alert=alert, device_hash="dev1", present=False,
                latitude=-1.2921, longitude=36.8219
            )
```

(Move the `from .models import PolicePresenceConfirmation` import to the top of the file alongside the existing `from .models import PolicePresenceAlert` import instead of inline, if your linter complains — either works for `manage.py test`.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && source venv/bin/activate && python manage.py test police_presence -v 2`
Expected: FAIL — `ImportError: cannot import name 'PolicePresenceConfirmation'` and `expires_at` still defaults to 30 minutes.

- [ ] **Step 3: Implement the model changes**

Edit `backend/police_presence/models.py` — replace the whole file:

```python
import uuid
from django.db import models
from django.utils import timezone


def _fifteen_minutes_from_now():
    return timezone.now() + timezone.timedelta(minutes=15)


def _fifty_minutes_from_now():
    return timezone.now() + timezone.timedelta(minutes=50)


class PolicePresenceAlert(models.Model):
    STATUS_ACTIVE = "active"
    STATUS_NEEDS_CONFIRMATION = "needs_confirmation"
    STATUS_CONFIRMED_PRESENT = "confirmed_present"
    STATUS_NOT_PRESENT = "not_present"
    STATUS_EXPIRED = "expired"

    STATUS_CHOICES = [
        (STATUS_ACTIVE, "Active"),
        (STATUS_NEEDS_CONFIRMATION, "Needs Confirmation"),
        (STATUS_CONFIRMED_PRESENT, "Confirmed Present"),
        (STATUS_NOT_PRESENT, "Not Present"),
        (STATUS_EXPIRED, "Expired"),
    ]

    # Two distinct-device "gone" votes marks the alert as cleared
    NOT_PRESENT_THRESHOLD = 2

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    latitude = models.FloatField()
    longitude = models.FloatField()
    reported_at = models.DateTimeField(auto_now_add=True)
    confirmation_required_after = models.DateTimeField(default=_fifteen_minutes_from_now)
    expires_at = models.DateTimeField(default=_fifty_minutes_from_now)
    last_confirmed_at = models.DateTimeField(null=True, blank=True)
    status = models.CharField(max_length=30, choices=STATUS_CHOICES, default=STATUS_ACTIVE)
    present_confirmations = models.IntegerField(default=0)
    not_present_confirmations = models.IntegerField(default=0)
    source = models.CharField(max_length=100, default="anonymous_community_report")
    reported_by_device_hash = models.CharField(max_length=64, blank=True, default="")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ["-reported_at"]

    def __str__(self):
        return f"Police at ({self.latitude:.4f}, {self.longitude:.4f}) — {self.status}"

    def refresh_status(self):
        """Compute and persist the current status based on time and confirmations."""
        now = timezone.now()
        if self.status in (self.STATUS_EXPIRED, self.STATUS_NOT_PRESENT):
            return
        if now > self.expires_at:
            self.status = self.STATUS_EXPIRED
        elif self.not_present_confirmations >= self.NOT_PRESENT_THRESHOLD:
            self.status = self.STATUS_NOT_PRESENT
        elif now > self.confirmation_required_after and self.status == self.STATUS_ACTIVE:
            self.status = self.STATUS_NEEDS_CONFIRMATION
        self.save(update_fields=["status", "updated_at"])


class PolicePresenceConfirmation(models.Model):
    """One device's vote on whether a checkpoint is still present. A device
    may vote at most once per alert (see Meta.unique_together)."""

    alert = models.ForeignKey(
        PolicePresenceAlert, related_name="confirmations", on_delete=models.CASCADE
    )
    device_hash = models.CharField(max_length=64)
    present = models.BooleanField()
    latitude = models.FloatField()
    longitude = models.FloatField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        unique_together = ("alert", "device_hash")

    def __str__(self):
        vote = "present" if self.present else "gone"
        return f"{vote} vote on {self.alert_id} by {self.device_hash[:8]}"
```

- [ ] **Step 4: Generate the migration**

Run: `cd backend && source venv/bin/activate && python manage.py makemigrations police_presence`
Expected output (exact generated filename may vary slightly by Django version — note it and use it in Step 5 below):
```
Migrations for 'police_presence':
  police_presence/migrations/0002_<auto-generated-name>.py
    - Add field last_confirmed_at to policepresencealert
    - Alter field expires_at on policepresencealert
    - Create model PolicePresenceConfirmation
```

- [ ] **Step 5: Apply the migration and run tests**

Run: `cd backend && source venv/bin/activate && python manage.py migrate police_presence && python manage.py test police_presence -v 2`
Expected: migration applies cleanly, then PASS for `test_default_expiry_is_fifty_minutes`, `test_last_confirmed_at_defaults_to_none`, `test_unique_together_alert_and_device_hash`, and all pre-existing `police_presence` tests still pass.

- [ ] **Step 6: Commit**

```bash
git add backend/police_presence/models.py backend/police_presence/migrations/ backend/police_presence/tests.py
git commit -m "feat: add PolicePresenceConfirmation model, last_confirmed_at, 50-minute TTL"
```

---

### Task 6: Serializer — expose last_confirmed_at

**Files:**
- Modify: `backend/police_presence/serializers.py`

**Interfaces:**
- Produces: `PolicePresenceAlertSerializer` now includes `last_confirmed_at` (read-only, nullable). Consumed by Task 8 (Android DTO).

- [ ] **Step 1: Update the serializer**

Edit `backend/police_presence/serializers.py`:

```python
from rest_framework import serializers

from .models import PolicePresenceAlert


class PolicePresenceAlertSerializer(serializers.ModelSerializer):
    id = serializers.UUIDField(read_only=True)
    reported_at = serializers.DateTimeField(read_only=True)
    confirmation_required_after = serializers.DateTimeField(read_only=True)
    expires_at = serializers.DateTimeField(read_only=True)
    last_confirmed_at = serializers.DateTimeField(read_only=True, allow_null=True)
    present_confirmations = serializers.IntegerField(read_only=True)
    not_present_confirmations = serializers.IntegerField(read_only=True)

    class Meta:
        model = PolicePresenceAlert
        fields = [
            "id",
            "latitude",
            "longitude",
            "reported_at",
            "confirmation_required_after",
            "expires_at",
            "last_confirmed_at",
            "status",
            "present_confirmations",
            "not_present_confirmations",
            "source",
        ]
        read_only_fields = [
            "id",
            "reported_at",
            "confirmation_required_after",
            "expires_at",
            "last_confirmed_at",
            "status",
            "present_confirmations",
            "not_present_confirmations",
        ]
```

- [ ] **Step 2: Run the existing report/active tests to confirm the new field doesn't break serialization**

Run: `cd backend && source venv/bin/activate && python manage.py test police_presence.tests.ReportViewTest police_presence.tests.ActiveViewTest -v 2`
Expected: PASS — all existing assertions still hold, response now additionally includes `"last_confirmed_at": null` for fresh alerts.

- [ ] **Step 3: Commit**

```bash
git add backend/police_presence/serializers.py
git commit -m "feat: expose last_confirmed_at on the police-presence alert serializer"
```

---

### Task 7: Unified confirm endpoint with proximity validation, self-confirm rejection, and clearing logic

**Files:**
- Modify: `backend/police_presence/views.py`
- Modify: `backend/police_presence/urls.py`
- Modify: `backend/police_presence/tests.py` (rewrite `ConfirmViewTest`)

**Interfaces:**
- Consumes: `PolicePresenceConfirmation` (Task 5), `PolicePresenceAlertSerializer` (Task 6).
- Produces: `POST police-presence/<uuid:alert_id>/confirm` with body `{latitude, longitude, device_hash, present}` → 200 with serialized alert, or 400/404/429 on validation failure. **Replaces** the old `confirm_police_present`/`confirm_police_not_present` views and the `<uuid:alert_id>/not-present` route. Consumed by Task 8 (Android `PolicePresenceApi`).

- [ ] **Step 1: Write the failing tests — replace `ConfirmViewTest`**

In `backend/police_presence/tests.py`, replace the entire `ConfirmViewTest` class with:

```python
class ConfirmViewTest(TestCase):

    def setUp(self):
        self.client = APIClient()
        self.alert = PolicePresenceAlert.objects.create(
            latitude=-1.2921, longitude=36.8219, reported_by_device_hash="reporter-device"
        )

    def _confirm(self, device_hash, present, lat=-1.2921, lon=36.8219):
        return self.client.post(
            f"/api/police-presence/{self.alert.id}/confirm",
            {"latitude": lat, "longitude": lon, "device_hash": device_hash, "present": present},
            format="json",
        )

    def test_present_confirmation_sets_status_and_last_confirmed_at(self):
        res = self._confirm("voter1", True)
        self.assertEqual(res.status_code, 200)
        self.alert.refresh_from_db()
        self.assertEqual(self.alert.status, PolicePresenceAlert.STATUS_CONFIRMED_PRESENT)
        self.assertEqual(self.alert.present_confirmations, 1)
        self.assertIsNotNone(self.alert.last_confirmed_at)

    def test_two_distinct_devices_saying_gone_clears_the_alert(self):
        res1 = self._confirm("voter1", False)
        self.assertEqual(res1.status_code, 200)
        res2 = self._confirm("voter2", False)
        self.assertEqual(res2.status_code, 200)
        self.alert.refresh_from_db()
        self.assertEqual(self.alert.status, PolicePresenceAlert.STATUS_NOT_PRESENT)

    def test_cleared_alert_excluded_from_active(self):
        self._confirm("voter1", False)
        self._confirm("voter2", False)
        res = self.client.get("/api/police-presence/active?lat=-1.2921&lon=36.8219")
        self.assertEqual(len(res.data["alerts"]), 0)

    def test_present_vote_resets_gone_counter(self):
        self._confirm("voter1", False)
        self._confirm("voter2", True)
        self.alert.refresh_from_db()
        self.assertEqual(self.alert.not_present_confirmations, 0)
        self.assertEqual(self.alert.status, PolicePresenceAlert.STATUS_CONFIRMED_PRESENT)

    def test_reporter_cannot_confirm_own_alert(self):
        res = self._confirm("reporter-device", True)
        self.assertEqual(res.status_code, 400)

    def test_confirm_rejected_when_more_than_500m_away(self):
        # ~2km north of the alert
        res = self._confirm("voter1", True, lat=-1.2741, lon=36.8219)
        self.assertEqual(res.status_code, 400)

    def test_device_cannot_confirm_the_same_alert_twice(self):
        first = self._confirm("voter1", True)
        self.assertEqual(first.status_code, 200)
        second = self._confirm("voter1", False)
        self.assertEqual(second.status_code, 429)

    def test_confirm_requires_device_hash(self):
        res = self.client.post(
            f"/api/police-presence/{self.alert.id}/confirm",
            {"latitude": -1.2921, "longitude": 36.8219, "present": True},
            format="json",
        )
        self.assertEqual(res.status_code, 400)

    def test_confirm_on_unknown_alert_returns_404(self):
        res = self.client.post(
            "/api/police-presence/00000000-0000-0000-0000-000000000000/confirm",
            {"latitude": -1.2921, "longitude": 36.8219, "device_hash": "voter1", "present": True},
            format="json",
        )
        self.assertEqual(res.status_code, 404)

    def test_confirm_on_already_cleared_alert_returns_400(self):
        self._confirm("voter1", False)
        self._confirm("voter2", False)
        res = self._confirm("voter3", True)
        self.assertEqual(res.status_code, 400)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && source venv/bin/activate && python manage.py test police_presence.tests.ConfirmViewTest -v 2`
Expected: FAIL — old `confirm_police_present` view ignores `present`/`latitude`/`longitude` entirely, so proximity/self-confirm/reset-on-present assertions fail; `/not-present`-based old tests are gone so no collision.

- [ ] **Step 3: Implement the unified view**

Edit `backend/police_presence/views.py` — replace the whole file:

```python
import math

from django.core.cache import cache
from django.utils import timezone
from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status

from .models import PolicePresenceAlert, PolicePresenceConfirmation
from .serializers import PolicePresenceAlertSerializer

# ── Kenya bounding box ──────────────────────────────────────────────────────
KENYA_LAT_MIN, KENYA_LAT_MAX = -5.0, 5.5
KENYA_LON_MIN, KENYA_LON_MAX = 33.0, 42.5

CONFIRM_PROXIMITY_METERS = 500
CONFIRM_RATE_LIMIT_SECONDS = 120
ALERT_TTL_MINUTES = 50


def _validate_kenya_coordinates(lat, lon):
    if lat is None or lon is None:
        return False, "latitude and longitude are required"
    try:
        lat, lon = float(lat), float(lon)
    except (TypeError, ValueError):
        return False, "latitude and longitude must be numbers"
    if lat == 0.0 and lon == 0.0:
        return False, "coordinates cannot be 0.0, 0.0"
    if not (KENYA_LAT_MIN <= lat <= KENYA_LAT_MAX):
        return False, f"latitude must be between {KENYA_LAT_MIN} and {KENYA_LAT_MAX} (Kenya)"
    if not (KENYA_LON_MIN <= lon <= KENYA_LON_MAX):
        return False, f"longitude must be between {KENYA_LON_MIN} and {KENYA_LON_MAX} (Kenya)"
    return True, None


def _haversine_meters(lat1, lon1, lat2, lon2):
    R = 6_371_000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.atan2(math.sqrt(a), math.sqrt(1 - a))


# ── Rate limiting via Django cache ──────────────────────────────────────────

def _is_report_rate_limited(device_hash: str) -> bool:
    """Block more than one report per device every 2 minutes."""
    if not device_hash:
        return False
    key = f"pp_report_{device_hash}"
    if cache.get(key):
        return True
    cache.set(key, True, timeout=120)
    return False


def _is_confirm_rate_limited(device_hash: str) -> bool:
    """Block more than one confirmation per device every 2 minutes, across alerts."""
    if not device_hash:
        return False
    key = f"pp_confirm_rl_{device_hash}"
    if cache.get(key):
        return True
    cache.set(key, True, timeout=CONFIRM_RATE_LIMIT_SECONDS)
    return False


# ── Views ───────────────────────────────────────────────────────────────────

@api_view(["POST"])
def report_police_presence(request):
    lat = request.data.get("latitude")
    lon = request.data.get("longitude")
    device_hash = (request.data.get("device_hash") or "").strip()[:64]

    ok, err = _validate_kenya_coordinates(lat, lon)
    if not ok:
        return Response({"error": err}, status=status.HTTP_400_BAD_REQUEST)

    if _is_report_rate_limited(device_hash):
        return Response(
            {"error": "Too many reports. Please wait before reporting again."},
            status=status.HTTP_429_TOO_MANY_REQUESTS,
        )

    alert = PolicePresenceAlert.objects.create(
        latitude=float(lat),
        longitude=float(lon),
        reported_by_device_hash=device_hash,
    )
    return Response(PolicePresenceAlertSerializer(alert).data, status=status.HTTP_201_CREATED)


@api_view(["GET"])
def active_police_presence(request):
    try:
        lat = float(request.query_params["lat"])
        lon = float(request.query_params["lon"])
        radius = float(request.query_params.get("radius_meters", 10_000))
    except (KeyError, ValueError, TypeError):
        return Response(
            {"error": "lat, lon are required numeric query parameters"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    ok, err = _validate_kenya_coordinates(lat, lon)
    if not ok:
        return Response({"error": err}, status=status.HTTP_400_BAD_REQUEST)

    now = timezone.now()
    candidates = PolicePresenceAlert.objects.exclude(
        status__in=[PolicePresenceAlert.STATUS_NOT_PRESENT, PolicePresenceAlert.STATUS_EXPIRED]
    ).filter(expires_at__gt=now)

    # Refresh time-based status in place and collect those within radius
    nearby = []
    for alert in candidates:
        if alert.not_present_confirmations >= PolicePresenceAlert.NOT_PRESENT_THRESHOLD:
            alert.status = PolicePresenceAlert.STATUS_NOT_PRESENT
            alert.save(update_fields=["status", "updated_at"])
            continue
        if now > alert.confirmation_required_after and alert.status == PolicePresenceAlert.STATUS_ACTIVE:
            alert.status = PolicePresenceAlert.STATUS_NEEDS_CONFIRMATION
            alert.save(update_fields=["status", "updated_at"])

        dist = _haversine_meters(lat, lon, alert.latitude, alert.longitude)
        if dist <= radius:
            nearby.append(alert)

    serializer = PolicePresenceAlertSerializer(nearby, many=True)
    return Response({"alerts": serializer.data})


@api_view(["POST"])
def confirm_police_presence(request, alert_id):
    lat = request.data.get("latitude")
    lon = request.data.get("longitude")
    device_hash = (request.data.get("device_hash") or "").strip()[:64]
    present = request.data.get("present")

    if not device_hash:
        return Response({"error": "device_hash is required"}, status=status.HTTP_400_BAD_REQUEST)

    if not isinstance(present, bool):
        return Response({"error": "present must be a boolean"}, status=status.HTTP_400_BAD_REQUEST)

    ok, err = _validate_kenya_coordinates(lat, lon)
    if not ok:
        return Response({"error": err}, status=status.HTTP_400_BAD_REQUEST)

    try:
        alert = PolicePresenceAlert.objects.get(pk=alert_id)
    except (PolicePresenceAlert.DoesNotExist, Exception):
        return Response({"error": "Alert not found"}, status=status.HTTP_404_NOT_FOUND)

    if alert.status in (PolicePresenceAlert.STATUS_EXPIRED, PolicePresenceAlert.STATUS_NOT_PRESENT):
        return Response({"error": "Alert is no longer active"}, status=status.HTTP_400_BAD_REQUEST)

    if device_hash == alert.reported_by_device_hash:
        return Response(
            {"error": "The reporting device cannot confirm its own report"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    distance = _haversine_meters(float(lat), float(lon), alert.latitude, alert.longitude)
    if distance > CONFIRM_PROXIMITY_METERS:
        return Response(
            {"error": f"You must be within {CONFIRM_PROXIMITY_METERS}m of the location to confirm"},
            status=status.HTTP_400_BAD_REQUEST,
        )

    if PolicePresenceConfirmation.objects.filter(alert=alert, device_hash=device_hash).exists():
        return Response({"error": "Already confirmed"}, status=status.HTTP_429_TOO_MANY_REQUESTS)

    if _is_confirm_rate_limited(device_hash):
        return Response(
            {"error": "Too many confirmations. Please wait before confirming again."},
            status=status.HTTP_429_TOO_MANY_REQUESTS,
        )

    PolicePresenceConfirmation.objects.create(
        alert=alert,
        device_hash=device_hash,
        present=present,
        latitude=float(lat),
        longitude=float(lon),
    )

    now = timezone.now()
    if present:
        alert.present_confirmations += 1
        alert.not_present_confirmations = 0
        alert.status = PolicePresenceAlert.STATUS_CONFIRMED_PRESENT
        alert.last_confirmed_at = now
    else:
        alert.not_present_confirmations += 1
        if alert.not_present_confirmations >= PolicePresenceAlert.NOT_PRESENT_THRESHOLD:
            alert.status = PolicePresenceAlert.STATUS_NOT_PRESENT
    alert.expires_at = now + timezone.timedelta(minutes=ALERT_TTL_MINUTES)
    alert.save()

    return Response(PolicePresenceAlertSerializer(alert).data)
```

- [ ] **Step 4: Update urls.py to the single unified route**

Edit `backend/police_presence/urls.py`:

```python
from django.urls import path

from . import views

urlpatterns = [
    path("report", views.report_police_presence),
    path("active", views.active_police_presence),
    path("<uuid:alert_id>/confirm", views.confirm_police_presence),
]
```

- [ ] **Step 5: Run the full police_presence test suite**

Run: `cd backend && source venv/bin/activate && python manage.py test police_presence -v 2`
Expected: PASS — all tests in `PolicePresenceModelTest`, `PolicePresenceConfirmationModelTest`, `ReportViewTest`, `ActiveViewTest`, and the rewritten `ConfirmViewTest` (10 tests) pass. Total should be all-green with no failures or errors.

- [ ] **Step 6: Commit**

```bash
git add backend/police_presence/views.py backend/police_presence/urls.py backend/police_presence/tests.py
git commit -m "feat: unify police-presence confirm endpoint with 500m validation and self-confirm rejection"
```

---

## Phase C — Wire the Android client to the new endpoint and add the drive-through UI

### Task 8: Android DTOs, API, and repository — unified confirm call, radius-aware polling

**Files:**
- Modify: `app/src/main/java/com/drivesafe/kenya/data/police/PolicePresenceDtos.kt`
- Modify: `app/src/main/java/com/drivesafe/kenya/data/police/PolicePresenceApi.kt`
- Modify: `app/src/main/java/com/drivesafe/kenya/data/police/PolicePresenceRepository.kt`
- Modify: `app/src/main/java/com/drivesafe/kenya/alerts/PolicePresenceAlert.kt`

**Interfaces:**
- Consumes: backend contract from Task 7 (`{latitude, longitude, device_hash, present}` → `POST .../confirm`).
- Produces: `PolicePresenceRepository.confirm(alertId: String, lat: Double, lon: Double, present: Boolean): PolicePresenceRepository.ConfirmResult` (replaces `confirmPresent`/`confirmNotPresent`); `PolicePresenceRepository.fetchActiveAlerts(lat: Double, lon: Double, radiusMeters: Int = 10_000)` (now radius-aware). Consumed by Task 11 (`MainActivity`).

- [ ] **Step 1: Update the domain model**

Edit `app/src/main/java/com/drivesafe/kenya/alerts/PolicePresenceAlert.kt`:

```kotlin
package com.drivesafe.kenya.alerts

data class PolicePresenceAlert(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val reportedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val confirmationRequiredAfterEpochMillis: Long,
    val status: PolicePresenceStatus,
    val presentConfirmations: Int = 0,
    val notPresentConfirmations: Int = 0,
    val source: String = "anonymous_community_report",
    val lastConfirmedAtEpochMillis: Long? = null,
    val localOnly: Boolean = false
) {
    fun isActive(): Boolean = status == PolicePresenceStatus.ACTIVE ||
            status == PolicePresenceStatus.NEEDS_CONFIRMATION ||
            status == PolicePresenceStatus.CONFIRMED_PRESENT

    fun needsConfirmation(): Boolean =
        status == PolicePresenceStatus.NEEDS_CONFIRMATION ||
                (System.currentTimeMillis() > confirmationRequiredAfterEpochMillis &&
                        status == PolicePresenceStatus.ACTIVE)
}
```

- [ ] **Step 2: Update the DTOs**

Edit `app/src/main/java/com/drivesafe/kenya/data/police/PolicePresenceDtos.kt`:

```kotlin
package com.drivesafe.kenya.data.police

import com.google.gson.annotations.SerializedName

data class ReportPolicePresenceRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("device_hash") val deviceHash: String
)

data class ConfirmPresenceRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("device_hash") val deviceHash: String,
    @SerializedName("present") val present: Boolean
)

data class PolicePresenceAlertDto(
    val id: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("reported_at") val reportedAt: String,
    @SerializedName("confirmation_required_after") val confirmationRequiredAfter: String,
    @SerializedName("expires_at") val expiresAt: String,
    @SerializedName("last_confirmed_at") val lastConfirmedAt: String?,
    val status: String,
    @SerializedName("present_confirmations") val presentConfirmations: Int,
    @SerializedName("not_present_confirmations") val notPresentConfirmations: Int,
    val source: String = "anonymous_community_report"
)

data class ActiveAlertsDto(
    val alerts: List<PolicePresenceAlertDto>
)
```

(`ConfirmRequest` is removed — replaced by `ConfirmPresenceRequest`.)

- [ ] **Step 3: Update the Retrofit interface**

Edit `app/src/main/java/com/drivesafe/kenya/data/police/PolicePresenceApi.kt`:

```kotlin
package com.drivesafe.kenya.data.police

import com.drivesafe.kenya.data.api.ApiConfig
import com.google.gson.GsonBuilder
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PolicePresenceApi {

    @POST("police-presence/report")
    suspend fun report(
        @Body body: ReportPolicePresenceRequest
    ): Response<PolicePresenceAlertDto>

    @GET("police-presence/active")
    suspend fun getActive(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius_meters") radiusMeters: Int = 10_000
    ): Response<ActiveAlertsDto>

    @POST("police-presence/{alertId}/confirm")
    suspend fun confirm(
        @Path("alertId") alertId: String,
        @Body body: ConfirmPresenceRequest
    ): Response<PolicePresenceAlertDto>

    companion object {
        fun create(): PolicePresenceApi = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(PolicePresenceApi::class.java)
    }
}
```

- [ ] **Step 4: Update the repository**

Edit `app/src/main/java/com/drivesafe/kenya/data/police/PolicePresenceRepository.kt` — replace the whole file:

```kotlin
package com.drivesafe.kenya.data.police

import com.drivesafe.kenya.alerts.PolicePresenceAlert
import com.drivesafe.kenya.alerts.PolicePresenceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

class PolicePresenceRepository(
    private val api: PolicePresenceApi,
    val deviceHash: String
) {
    private val _alerts = MutableStateFlow<List<PolicePresenceAlert>>(emptyList())
    val alerts: StateFlow<List<PolicePresenceAlert>> = _alerts.asStateFlow()

    suspend fun fetchActiveAlerts(lat: Double, lon: Double, radiusMeters: Int = 10_000) {
        try {
            val response = api.getActive(lat, lon, radiusMeters)
            if (response.isSuccessful) {
                val body = response.body() ?: return
                _alerts.value = body.alerts.mapNotNull { it.toDomain() }
            }
        } catch (_: Exception) {
            // Offline — keep last-known alerts
        }
    }

    /**
     * Returns a human-readable result string: null on success, error description on failure.
     * Adds the new alert to the local cache immediately on success.
     */
    suspend fun report(lat: Double, lon: Double): ReportResult {
        return try {
            val response = api.report(ReportPolicePresenceRequest(lat, lon, deviceHash))
            if (response.isSuccessful) {
                response.body()?.toDomain()?.let { alert ->
                    _alerts.value = (_alerts.value + alert).filter { it.isActive() }
                }
                ReportResult.Success
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                val serverMsg = parseErrorField(errorBody)
                ReportResult.Failure(serverMsg)
            }
        } catch (e: Exception) {
            ReportResult.Failure("Network error. Check your connection.")
        }
    }

    sealed class ReportResult {
        object Success : ReportResult()
        data class Failure(val reason: String) : ReportResult()
    }

    suspend fun confirm(alertId: String, lat: Double, lon: Double, present: Boolean): ConfirmResult {
        return try {
            val response = api.confirm(alertId, ConfirmPresenceRequest(lat, lon, deviceHash, present))
            if (response.isSuccessful) {
                response.body()?.toDomain()?.let { updated ->
                    _alerts.value = _alerts.value.map { if (it.id == alertId) updated else it }
                        .filter { it.isActive() }
                }
                ConfirmResult.Success
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                ConfirmResult.Failure(parseErrorField(errorBody))
            }
        } catch (e: Exception) {
            ConfirmResult.Failure("Network error. Check your connection.")
        }
    }

    sealed class ConfirmResult {
        object Success : ConfirmResult()
        data class Failure(val reason: String) : ConfirmResult()
    }
}

// ── Error body parsing ───────────────────────────────────────────────────────

private fun parseErrorField(json: String): String {
    // Extract {"error":"..."} from DRF error responses
    val match = Regex(""""error"\s*:\s*"([^"]+)"""").find(json)
    return match?.groupValues?.get(1) ?: "Could not submit request."
}

// ── DTO → domain conversion ──────────────────────────────────────────────────

private fun PolicePresenceAlertDto.toDomain(): PolicePresenceAlert? = try {
    PolicePresenceAlert(
        id = id,
        latitude = latitude,
        longitude = longitude,
        reportedAtEpochMillis = reportedAt.toEpochMillis(),
        expiresAtEpochMillis = expiresAt.toEpochMillis(),
        confirmationRequiredAfterEpochMillis = confirmationRequiredAfter.toEpochMillis(),
        status = status.toStatus(),
        presentConfirmations = presentConfirmations,
        notPresentConfirmations = notPresentConfirmations,
        source = source,
        lastConfirmedAtEpochMillis = lastConfirmedAt?.toEpochMillis()
    )
} catch (_: Exception) { null }

// Django emits microseconds (e.g. 2026-06-29T10:30:00.123456Z).
// java.time.Instant.parse only handles milliseconds, so truncate to 3 decimal digits.
private fun String.toEpochMillis(): Long {
    val normalized = this
        .replace(Regex("(\\.\\d{3})\\d+(Z)$"), "$1$2")
        .replace(Regex("(:\\d{2})(Z)$"), "$1.000$2")
    return Instant.parse(normalized).toEpochMilli()
}

private fun String.toStatus(): PolicePresenceStatus = when (this) {
    "active" -> PolicePresenceStatus.ACTIVE
    "needs_confirmation" -> PolicePresenceStatus.NEEDS_CONFIRMATION
    "confirmed_present" -> PolicePresenceStatus.CONFIRMED_PRESENT
    "not_present" -> PolicePresenceStatus.NOT_PRESENT
    "expired" -> PolicePresenceStatus.EXPIRED
    else -> PolicePresenceStatus.ACTIVE
}
```

- [ ] **Step 5: Compile check — expect only known failures**

As in Task 4, the whole module fails to compile until Task 11 fixes every call site — do not run `testDebugUnitTest` here.

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|e: "`
Expected: every error is in `MainActivity.kt`, referencing `fetchActiveAlerts`, `confirmPresent`, `confirmNotPresent`, or the old `evaluatePolicePresence` signature — all fixed in Task 11. No errors inside `data/police/*` or `alerts/PolicePresenceAlert.kt`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/drivesafe/kenya/data/police/PolicePresenceDtos.kt app/src/main/java/com/drivesafe/kenya/data/police/PolicePresenceApi.kt app/src/main/java/com/drivesafe/kenya/data/police/PolicePresenceRepository.kt app/src/main/java/com/drivesafe/kenya/alerts/PolicePresenceAlert.kt
git commit -m "feat: unify Android confirm client onto the new endpoint contract"
```

---

### Task 9: DrivingScreen — gate the passive confirm card to 500m, add the drive-through overlay

**Files:**
- Modify: `app/src/main/java/com/drivesafe/kenya/ui/DrivingScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PolicePresenceAlert` (Task 8).
- Produces: `DrivingScreen`/`DriveSafeHomeScreen` gain two new parameters: `driveThroughPrompt: PolicePresenceAlert? = null`, `onDriveThroughAnswer: (String, Boolean) -> Unit = { _, _ -> }`. Consumed by Task 11 (`MainActivity`).

- [ ] **Step 1: Add the new strings**

In `app/src/main/res/values/strings.xml`, after the existing `police_report_failed` line (currently line 78), add:

```xml
    <string name="drive_through_prompt_question">Is the police checkpoint still there?</string>
    <string name="drive_through_still_there">Bado wapo / Still there</string>
    <string name="drive_through_gone">Wameondoka / Gone</string>
```

- [ ] **Step 2: Add the `PolicePresenceAlert` import**

In `app/src/main/java/com/drivesafe/kenya/ui/DrivingScreen.kt`, add to the import block (alongside the existing `com.drivesafe.kenya.alerts.NearbyPolicePresenceResult` import):

```kotlin
import com.drivesafe.kenya.alerts.PolicePresenceAlert
```

- [ ] **Step 3: Add new parameters and wrap the root in a Box**

The current `DrivingScreen` wrapper (around line 87-123) and `DriveSafeHomeScreen` (around line 126) both need the two new parameters threaded through, and `DriveSafeHomeScreen`'s root needs to become a `Box` so the overlay can float above the scrollable/footer content added in the previous screen-layout change.

Replace the `DrivingScreen` function signature and body:

```kotlin
@Composable
fun DrivingScreen(
    speedKmh: Double?,
    isLocationAvailable: Boolean,
    nearbyCamera: NearbyCameraResult?,
    overspeedResult: OverspeedResult?,
    cameraZoneCount: Int,
    keepScreenOn: Boolean,
    themeMode: AppThemeMode,
    onToggleTheme: () -> Unit,
    onStopDriving: () -> Unit,
    nearbyPoliceAlert: NearbyPolicePresenceResult? = null,
    onReportPolicePresence: () -> Unit = {},
    policeReportMessage: String? = null,
    onConfirmPolicePresent: (String) -> Unit = {},
    onConfirmPoliceNotPresent: (String) -> Unit = {},
    driveThroughPrompt: PolicePresenceAlert? = null,
    onDriveThroughAnswer: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (keepScreenOn) KeepScreenOn()

    DriveSafeHomeScreen(
        speedKmh = speedKmh,
        isLocationAvailable = isLocationAvailable,
        nearbyCamera = nearbyCamera,
        overspeedResult = overspeedResult,
        cameraZoneCount = cameraZoneCount,
        themeMode = themeMode,
        onToggleTheme = onToggleTheme,
        onStopDriving = onStopDriving,
        nearbyPoliceAlert = nearbyPoliceAlert,
        onReportPolicePresence = onReportPolicePresence,
        policeReportMessage = policeReportMessage,
        onConfirmPolicePresent = onConfirmPolicePresent,
        onConfirmPoliceNotPresent = onConfirmPoliceNotPresent,
        driveThroughPrompt = driveThroughPrompt,
        onDriveThroughAnswer = onDriveThroughAnswer,
        modifier = modifier
    )
}
```

Replace the `DriveSafeHomeScreen` function signature and root layout — it currently starts with `fun DriveSafeHomeScreen(...) { val isDark = ... Column(modifier = modifier.fillMaxSize()... ) { ... } }`. Add the two parameters and wrap the existing `Column` in a `Box`:

```kotlin
@Composable
fun DriveSafeHomeScreen(
    speedKmh: Double?,
    isLocationAvailable: Boolean,
    nearbyCamera: NearbyCameraResult?,
    overspeedResult: OverspeedResult?,
    cameraZoneCount: Int,
    themeMode: AppThemeMode,
    onToggleTheme: () -> Unit,
    onStopDriving: () -> Unit,
    nearbyPoliceAlert: NearbyPolicePresenceResult? = null,
    onReportPolicePresence: () -> Unit = {},
    policeReportMessage: String? = null,
    onConfirmPolicePresent: (String) -> Unit = {},
    onConfirmPoliceNotPresent: (String) -> Unit = {},
    driveThroughPrompt: PolicePresenceAlert? = null,
    onDriveThroughAnswer: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val isDark = themeMode == AppThemeMode.DARK
    val colors = dashboardColors(isDark)
    val speedLimitKmh = effectiveSpeedLimit(nearbyCamera, overspeedResult)
    val isOverspeed = overspeedResult?.status == OverspeedStatus.OVERSPEED
    val formattedDistance = formatDistanceParts(nearbyCamera?.distanceMeters)
    val formattedLimit = formatSpeedLimitParts(nearbyCamera)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
```

(All the existing content inside this `Column` — the inner scrollable `Column`, the pinned footer — stays exactly as-is; only the outer `modifier = modifier.fillMaxSize()...` line changes from being on the `Column` to being on the new wrapping `Box`, and the `Column`'s own modifier becomes plain `Modifier.fillMaxSize()...` as shown above.)

Then, just before the closing of the function (after the existing root `Column`'s closing `}`), add the overlay and close the `Box`:

```kotlin
        } // closes the root Column

        if (driveThroughPrompt != null) {
            DriveThroughConfirmOverlay(
                alert = driveThroughPrompt,
                onAnswer = { present -> onDriveThroughAnswer(driveThroughPrompt.id, present) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    } // closes the Box
}
```

Also gate the existing passive confirm card to only show actionable buttons within 500m (the new backend's proximity validation radius) — find this block (currently around line 223-233):

```kotlin
            if (nearbyPoliceAlert != null && nearbyPoliceAlert.isWithinWarningRadius) {
                if (nearbyPoliceAlert.needsConfirmation) {
                    PoliceConfirmCard(
                        result = nearbyPoliceAlert,
                        onConfirmPresent = { onConfirmPolicePresent(nearbyPoliceAlert.alert.id) },
                        onConfirmNotPresent = { onConfirmPoliceNotPresent(nearbyPoliceAlert.alert.id) },
                        colors = colors
                    )
                } else {
                    PoliceCheckpointCard(result = nearbyPoliceAlert, colors = colors)
                }
            }
```

Replace the condition:

```kotlin
            if (nearbyPoliceAlert != null && nearbyPoliceAlert.isWithinWarningRadius) {
                if (nearbyPoliceAlert.needsConfirmation &&
                    nearbyPoliceAlert.distanceMeters <= CONFIRM_ACTIONABLE_RADIUS_METERS
                ) {
                    PoliceConfirmCard(
                        result = nearbyPoliceAlert,
                        onConfirmPresent = { onConfirmPolicePresent(nearbyPoliceAlert.alert.id) },
                        onConfirmNotPresent = { onConfirmPoliceNotPresent(nearbyPoliceAlert.alert.id) },
                        colors = colors
                    )
                } else {
                    PoliceCheckpointCard(result = nearbyPoliceAlert, colors = colors)
                }
            }
```

Add the constant near the top of the file, alongside `GaugeMaxSpeedKmh`:

```kotlin
private const val GaugeMaxSpeedKmh = 140f
private const val CONFIRM_ACTIONABLE_RADIUS_METERS = 500f
```

- [ ] **Step 4: Add the DriveThroughConfirmOverlay composable**

Add this new private composable anywhere alongside the other card composables (e.g. right after `PoliceConfirmCard`'s closing brace):

```kotlin
@Composable
private fun DriveThroughConfirmOverlay(
    alert: PolicePresenceAlert,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2028)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.drive_through_prompt_question),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onAnswer(true) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text(
                        text = stringResource(R.string.drive_through_still_there),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { onAnswer(false) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667085))
                ) {
                    Text(
                        text = stringResource(R.string.drive_through_gone),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Leave the two `@Preview` composables untouched**

The default parameter values (`= null`, `= { _, _ -> }`) on `driveThroughPrompt`/`onDriveThroughAnswer` mean `DriveSafeHomeScreenLightPreview`/`DriveSafeHomeScreenDarkPreview` (which don't pass those arguments) keep compiling unchanged. No edit needed for this task.

- [ ] **Step 6: Compile check — expect only known failures**

As in Task 4, the whole module fails to compile until Task 11 fixes every call site — do not run `testDebugUnitTest` here.

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|e: "`
Expected: no errors originating in `DrivingScreen.kt`. Every reported error is in `MainActivity.kt` (known pending call-site errors, fixed in Task 11).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/drivesafe/kenya/ui/DrivingScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat: add drive-through confirmation overlay, gate passive card to 500m"
```

---

### Task 10: Widen the poll interval/radius constants

**Files:**
- Modify: `app/src/main/java/com/drivesafe/kenya/MainActivity.kt` (poll constants only — full wiring is Task 11)

**Interfaces:**
- Produces: poll runs every 45s or 1km (down from 120s), fetch radius 12,000m (up from 10,000m default). Sets up Task 11.

This is split out from Task 11 only so the numeric-constant change is independently reviewable; if you're doing subagent-driven execution, it's fine to merge this into Task 11's diff instead.

- [ ] **Step 1: Update the poll `LaunchedEffect` thresholds**

In `app/src/main/java/com/drivesafe/kenya/MainActivity.kt`, find the police poll block (currently lines 141-156):

```kotlin
                // Periodically fetch police alerts while driving: every 2 min or every 1 km
                LaunchedEffect(userLocation, isDriving) {
                    if (!isDriving || userLocation == null) return@LaunchedEffect
                    val (lat, lng) = userLocation!!
                    val now = System.currentTimeMillis()
                    val elapsedMs = now - lastPoliceFetchTime
                    val distanceMoved = lastPoliceFetchLocation?.let { (pLat, pLng) ->
                        sqrt((lat - pLat) * (lat - pLat) + (lng - pLng) * (lng - pLng)) * 111_000.0
                    } ?: Double.MAX_VALUE

                    if (elapsedMs >= 120_000L || distanceMoved >= 1_000.0) {
                        policePresenceRepository.fetchActiveAlerts(lat, lng)
                        lastPoliceFetchLocation = Pair(lat, lng)
                        lastPoliceFetchTime = now
                    }
                }
```

Replace with:

```kotlin
                // Periodically fetch police alerts while driving: every 45s or every 1 km
                LaunchedEffect(userLocation, isDriving) {
                    if (!isDriving || userLocation == null) return@LaunchedEffect
                    val (lat, lng) = userLocation!!
                    val now = System.currentTimeMillis()
                    val elapsedMs = now - lastPoliceFetchTime
                    val distanceMoved = lastPoliceFetchLocation?.let { (pLat, pLng) ->
                        sqrt((lat - pLat) * (lat - pLat) + (lng - pLng) * (lng - pLng)) * 111_000.0
                    } ?: Double.MAX_VALUE

                    if (elapsedMs >= POLICE_POLL_INTERVAL_MS || distanceMoved >= POLICE_POLL_DISTANCE_METERS) {
                        policePresenceRepository.fetchActiveAlerts(lat, lng, POLICE_FETCH_RADIUS_METERS)
                        lastPoliceFetchLocation = Pair(lat, lng)
                        lastPoliceFetchTime = now
                    }
                }
```

- [ ] **Step 2: Add the constants to the `MainActivity` companion**

`MainActivity` currently has no companion object. Add one at the bottom of the class, just before the final closing brace:

```kotlin
    companion object {
        private const val POLICE_POLL_INTERVAL_MS = 45_000L
        private const val POLICE_POLL_DISTANCE_METERS = 1_000.0
        private const val POLICE_FETCH_RADIUS_METERS = 12_000
    }
}
```

- [ ] **Step 3: Compile check — expect only known failures**

As in Task 4, the whole module fails to compile until Task 11 fixes every call site — do not run `testDebugUnitTest` here.

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|e: "`
Expected: same known pending errors as before this step (unified confirm callbacks, `evaluatePolicePresence` signature) — this task only touched poll-interval constants, so no new errors should appear beyond what Tasks 4/8/9 already left pending.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/drivesafe/kenya/MainActivity.kt
git commit -m "feat: widen police-presence poll to 45s/1km and fetch radius to 12km"
```

---

### Task 11: Wire GpsFix, ProximityAlertEngine, DriveThroughTracker, and the unified confirm callbacks into MainActivity

**Files:**
- Modify: `app/src/main/java/com/drivesafe/kenya/MainActivity.kt`

**Interfaces:**
- Consumes: `LocationService.gpsFix` (Task 1), `AlertManager.evaluatePolicePresence`/`hasAlertedProximity` (Task 4), `DriveThroughTracker` (Task 3), `PolicePresenceRepository.confirm`/`fetchActiveAlerts` (Task 8), `DrivingScreen`'s new `driveThroughPrompt`/`onDriveThroughAnswer` params (Task 9).
- This task makes the whole app compile again — it's the integration point for everything above.

- [ ] **Step 1: Add imports**

Add to the import block in `app/src/main/java/com/drivesafe/kenya/MainActivity.kt`:

```kotlin
import com.drivesafe.kenya.alerts.DriveThroughTracker
import kotlinx.coroutines.delay
```

- [ ] **Step 2: Add new remembered state**

Near the existing `var policeReportMessage by remember { mutableStateOf<String?>(null) }` block, add:

```kotlin
                var policeReportMessage by remember { mutableStateOf<String?>(null) }
                var lastPoliceFetchTime by remember { mutableLongStateOf(0L) }
                var lastPoliceFetchLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
                val driveThroughTracker = remember { DriveThroughTracker() }
                var driveThroughPromptAlertId by remember { mutableStateOf<String?>(null) }
```

- [ ] **Step 3: Add the `gpsFix` state collection**

Near `val userLocation by locationService.userLocation.collectAsState()`, add:

```kotlin
                val userLocation by locationService.userLocation.collectAsState()
                val gpsFix by locationService.gpsFix.collectAsState()
```

- [ ] **Step 4: Replace the camera/police TTS `LaunchedEffect` and add the proximity + drive-through effects**

The current combined effect (lines 158-169) is:

```kotlin
                LaunchedEffect(userLocation) {
                    if (isDriving && userLocation != null) {
                        alertManager.evaluate(
                            nearbyCamera, overspeedResult, speedKmh,
                            settings.voiceAlertsEnabled, settings.vibrationAlertsEnabled
                        )
                        alertManager.evaluatePolicePresence(
                            nearbyPoliceAlert,
                            settings.voiceAlertsEnabled, settings.vibrationAlertsEnabled
                        )
                    }
                }
```

Replace it with three separate effects:

```kotlin
                LaunchedEffect(userLocation) {
                    if (isDriving && userLocation != null) {
                        alertManager.evaluate(
                            nearbyCamera, overspeedResult, speedKmh,
                            settings.voiceAlertsEnabled, settings.vibrationAlertsEnabled
                        )
                    }
                }

                LaunchedEffect(gpsFix) {
                    val fix = gpsFix
                    if (isDriving && fix != null) {
                        alertManager.evaluatePolicePresence(
                            fix, policeAlerts,
                            settings.voiceAlertsEnabled, settings.vibrationAlertsEnabled
                        )
                        val alreadyAlertedIds = policeAlerts
                            .filter { alertManager.hasAlertedProximity(it.id) }
                            .map { it.id }
                            .toSet()
                        val promptId = driveThroughTracker.onTick(
                            fix.latitude, fix.longitude, alreadyAlertedIds, policeAlerts
                        )
                        if (promptId != null) {
                            driveThroughPromptAlertId = promptId
                        }
                    }
                }

                LaunchedEffect(driveThroughPromptAlertId) {
                    val id = driveThroughPromptAlertId
                    if (id != null) {
                        delay(30_000L)
                        if (driveThroughPromptAlertId == id) {
                            driveThroughPromptAlertId = null
                        }
                    }
                }
```

- [ ] **Step 5: Reset the new trackers on stop-driving, and unify the confirm callbacks**

In the `DrivingScreen(...)` call inside the `isDriving ->` branch, update `onStopDriving` and the two confirm callbacks, and add the two new parameters:

```kotlin
                                onStopDriving = {
                                    locationService.stopUpdates()
                                    alertManager.reset()
                                    driveThroughTracker.reset()
                                    driveThroughPromptAlertId = null
                                    policeReportMessage = null
                                },
                                nearbyPoliceAlert = nearbyPoliceAlert,
                                onReportPolicePresence = {
                                    val loc = userLocation
                                    if (loc == null) {
                                        policeReportMessage = getString(R.string.police_location_unavailable)
                                        return@DrivingScreen
                                    }
                                    scope.launch {
                                        policeReportMessage = when (val r = policePresenceRepository.report(loc.first, loc.second)) {
                                            is ReportResult.Success -> getString(R.string.police_reported_thanks)
                                            is ReportResult.Failure -> r.reason
                                        }
                                    }
                                },
                                policeReportMessage = policeReportMessage,
                                onConfirmPolicePresent = { alertId ->
                                    val loc = userLocation
                                    if (loc != null) {
                                        scope.launch { policePresenceRepository.confirm(alertId, loc.first, loc.second, present = true) }
                                    }
                                },
                                onConfirmPoliceNotPresent = { alertId ->
                                    val loc = userLocation
                                    if (loc != null) {
                                        scope.launch { policePresenceRepository.confirm(alertId, loc.first, loc.second, present = false) }
                                    }
                                },
                                driveThroughPrompt = driveThroughPromptAlertId?.let { id ->
                                    policeAlerts.firstOrNull { it.id == id }
                                },
                                onDriveThroughAnswer = { alertId, present ->
                                    val loc = userLocation
                                    driveThroughPromptAlertId = null
                                    if (loc != null) {
                                        scope.launch { policePresenceRepository.confirm(alertId, loc.first, loc.second, present) }
                                    }
                                },
                                modifier = Modifier.padding(innerPadding)
```

- [ ] **Step 6: Full compile**

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:compileDebugKotlin -q`
Expected: no output, exit code 0 — the whole app compiles clean.

- [ ] **Step 7: Run the full Android unit test suite**

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (no test file references the old `confirmPresent`/`confirmNotPresent`/`ConfirmRequest`/old `evaluatePolicePresence` signature, since none of those existed as directly-tested units before).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/drivesafe/kenya/MainActivity.kt
git commit -m "feat: wire ProximityAlertEngine, DriveThroughTracker, and unified confirm into MainActivity"
```

---

## Phase D — Verification (required — synthetic tests alone are not sufficient)

### Task 12: Real-movement verification with emulator route playback

**Files:** none (manual verification task, no code changes)

This exercises the actual GPS→bearing→TTS→escalation→drive-through pipeline end-to-end, which the unit tests in Tasks 2–3 cannot: they test the pure math/state-machine, not the live GPS stream, TTS engine, or Compose wiring.

- [ ] **Step 1: Seed a test alert near a known route**

With the backend running locally (`cd backend && source venv/bin/activate && python manage.py runserver`), pick a location on a road you can simulate driving along in the emulator (e.g. Waiyaki Way, Nairobi: `-1.2685, 36.7754`), and seed an alert via the Django shell:

```bash
cd backend && source venv/bin/activate && python manage.py shell -c "
from police_presence.models import PolicePresenceAlert
PolicePresenceAlert.objects.create(latitude=-1.2685, longitude=36.7754, reported_by_device_hash='seed-device')
print('seeded')
"
```

Expected output: `seeded`

- [ ] **Step 2: Point the Android build at your local backend and install it**

Confirm `ApiConfig.BASE_URL` (or whatever local-dev override the project uses — check `app/src/main/java/com/drivesafe/kenya/data/api/ApiConfig.kt`) points at your machine's LAN IP and port, matching the pattern noted in project memory (physical device tested at `192.168.1.10:8111`). Build and install:

Run: `JAVA_HOME=/Users/ronojak/Library/Java/JavaVirtualMachines/jdk-17.0.2+8/Contents/Home ./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, app installed on the running emulator/device.

- [ ] **Step 3: Set up route playback in the emulator**

In Android Studio's emulator: **Extended Controls (`...`) → Location → Routes**, and either import a GPX/KML route along the road you seeded the alert on, or use **Saved routes → point-to-point** approaching the seeded coordinate from ~3km away, ending ~1km past it, at a simulated speed of roughly 40-60 km/h (fast enough to clear `MIN_SPEED_FOR_BEARING_MPS`).

- [ ] **Step 4: Start driving mode and play the route, observing each checkpoint**

Open the app, tap "Start Driving," then start route playback. Verify, in order:
1. **Alert fires between 2km and 1.5km** from the seeded point (not before ~2km, not after) — you should hear "Police reported X.X kilometres ahead."
2. **No re-alert** — the same message does not repeat as you continue approaching.
3. **Escalation at ~400m** — you hear "Police 400 metres ahead" once, and only once.
4. **Drive-through prompt appears after passing** — once you're within 250m and then moving away, the bottom overlay ("Is the police checkpoint still there?" / "Bado wapo / Still there" / "Wameondoka / Gone") appears.
5. **Auto-dismiss** — if you don't tap either button, the overlay disappears after 30 seconds.

- [ ] **Step 5: Verify the bearing filter with a route driving AWAY from the point**

Re-seed a fresh alert (Step 1, different coordinates or delete+recreate), then play a route that starts near the alert and drives directly away from it (never approaching within 2km with the alert ahead of the course). Verify: no TTS alert fires at all, since the driver's course never points toward the alert within the 60° cone and distance never drops under the 1000m no-course fallback.

Expected overall result of this task: all five behaviors in Step 4 and the negative case in Step 5 are observed and match. If any deviate, return to the relevant task (2, 3, or 11) and fix before proceeding — this is the actual acceptance test for the feature, not the unit tests.

---

### Task 13: Manual API verification with curl

**Files:** none (manual verification task, no code changes)

Confirms the backend clearing/validation logic end-to-end via real HTTP requests, independent of the Android app.

- [ ] **Step 1: Start the backend and seed an alert**

```bash
cd backend && source venv/bin/activate && python manage.py runserver &
ALERT_ID=$(python manage.py shell -c "
from police_presence.models import PolicePresenceAlert
a = PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219, reported_by_device_hash='curl-reporter')
print(a.id)
" | tail -1)
echo "Seeded alert: $ALERT_ID"
```

Expected: prints a UUID.

- [ ] **Step 2: Confirm from two distinct devices at valid coordinates — verify the alert clears**

```bash
curl -s -X POST "http://127.0.0.1:8000/api/police-presence/$ALERT_ID/confirm" \
  -H "Content-Type: application/json" \
  -d '{"latitude": -1.2921, "longitude": 36.8219, "device_hash": "curl-device-1", "present": false}'
echo
curl -s -X POST "http://127.0.0.1:8000/api/police-presence/$ALERT_ID/confirm" \
  -H "Content-Type: application/json" \
  -d '{"latitude": -1.2921, "longitude": 36.8219, "device_hash": "curl-device-2", "present": false}'
echo
curl -s "http://127.0.0.1:8000/api/police-presence/active?lat=-1.2921&lon=36.8219"
```

Expected: first two calls return `200` with `"status": "active"` then `"status": "not_present"` respectively on the second response's body; the final `/active` call returns `{"alerts": []}` — the cleared alert is excluded.

- [ ] **Step 3: Verify rejection when confirming from far away**

```bash
ALERT_ID_2=$(python manage.py shell -c "
from police_presence.models import PolicePresenceAlert
a = PolicePresenceAlert.objects.create(latitude=-1.2921, longitude=36.8219, reported_by_device_hash='curl-reporter-2')
print(a.id)
" | tail -1)
curl -s -o /tmp/confirm_far.json -w "%{http_code}\n" -X POST "http://127.0.0.1:8000/api/police-presence/$ALERT_ID_2/confirm" \
  -H "Content-Type: application/json" \
  -d '{"latitude": -1.15, "longitude": 36.8219, "device_hash": "curl-device-3", "present": true}'
cat /tmp/confirm_far.json
```

Expected: prints `400` followed by a JSON body containing `"error": "You must be within 500m of the location to confirm"`.

- [ ] **Step 4: Stop the dev server**

```bash
kill %1
```

Expected overall result: alert clears after two distinct-device "gone" votes and disappears from `/active`; a confirmation attempt from ~14km away is rejected with `400`. This is the acceptance test for Task 7's backend logic, run against the real server rather than the Django test client.

---

## Self-Review Notes (for whoever executes this plan)

- **Spec coverage:** Part 1a → Task 10. Part 1b → Task 2, Task 4. Part 1c → Task 3, Task 9, Task 11. Part 2a → Task 7. Part 2b → Task 5, Task 7. Part 2c → Task 5. Verification #1 (unit tests) → Tasks 2, 3, 5, 7. Verification #2 (real-movement) → Task 12. Verification #3 (curl) → Task 13. Verification #4 (incremental diffs, Android engine → backend → UI wiring) → the task ordering itself (Phase A → B → C).
- **Deviations from the literal spec, and why:** (1) the spec's "cache results in memory as `Map<UUID, CachedAlert>`" is satisfied by the pre-existing `PolicePresenceRepository.alerts: StateFlow<List<PolicePresenceAlert>>`, which already does full add/update/drop-on-poll — introducing a second parallel cache would duplicate state for no benefit. (2) The spec's status name `confirmed_not_present` is the pre-existing `PolicePresenceAlert.STATUS_NOT_PRESENT` — same concept, existing vocabulary kept to avoid an unnecessary rename across serializer/DTO/tests. (3) No Swahili TTS — no locale mechanism exists anywhere in the app; only the drive-through button labels get the bilingual treatment, using the literal text the spec provided.
