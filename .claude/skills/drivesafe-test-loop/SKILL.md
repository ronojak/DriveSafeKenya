# DriveSafeKenya Test Loop Skill

## Purpose

Use this skill after every development phase in the **DriveSafeKenya** Android project.

This skill checks that the app still builds, tests pass, camera-zone data is valid, location logic works, and warning behavior is correct.

The project is an Android Kotlin / Jetpack Compose app for Kenyan drivers. It detects nearby speed-camera zones, checks current speed, warns the driver when approaching a camera, and gives continuous warnings when the driver is exceeding the speed limit.

---

## When to Use This Skill

Use this skill after:

- Adding or editing camera coordinates
- Updating `camera_zones.json`
- Changing proximity detection
- Changing overspeed detection
- Changing warning / alert logic
- Changing Text-To-Speech or vibration behavior
- Changing the driving screen UI
- Completing any project phase
- Before committing code

---

## Project Assumptions

The project package is:

```text
com.drivesafe.kenya
```

Important project areas may include:

```text
app/src/main/assets/camera_zones.json
app/src/main/java/com/drivesafe/kenya/data/
app/src/main/java/com/drivesafe/kenya/location/
app/src/main/java/com/drivesafe/kenya/alerts/
app/src/main/java/com/drivesafe/kenya/settings/
app/src/main/java/com/drivesafe/kenya/ui/
app/src/test/
app/src/androidTest/
```

The app should support:

- Camera-zone loading from JSON
- Real camera-zone coordinates
- 3 km camera proximity warning
- Only two proximity warnings per camera entry while inside the 3 km zone
- Resetting warning count after exiting a camera zone
- Continuous overspeed warnings while inside a camera zone and above the speed limit
- Existing app features must not be broken

---

## Main Loop Instruction

When this skill is triggered, run the following loop:

```text
INSPECT → TEST → FIX → RETEST → REPORT
```

Repeat the loop until:

1. The project builds successfully.
2. Unit tests pass.
3. Camera-zone data is valid.
4. Proximity-warning behavior is correct.
5. Overspeed-warning behavior is correct.
6. No obvious regression is introduced.

Do not rewrite the whole project unnecessarily. Make small, safe, reviewable changes.

---

## Step 1: Inspect the Current Project

Run:

```bash
pwd
find . -maxdepth 4 -type f | sort | sed 's#^\./##' | head -200
```

Then inspect key files:

```bash
find app/src/main -type f | sort
find app/src/test -type f | sort || true
find app/src/androidTest -type f | sort || true
```

Check for these files:

```bash
ls -la app/src/main/assets || true
ls -la app/src/main/java/com/drivesafe/kenya || true
```

Inspect the camera-zone file:

```bash
cat app/src/main/assets/camera_zones.json
```

Inspect Gradle configuration:

```bash
cat settings.gradle.kts 2>/dev/null || cat settings.gradle
cat build.gradle.kts 2>/dev/null || cat build.gradle
cat app/build.gradle.kts 2>/dev/null || cat app/build.gradle
```

---

## Step 2: Validate Camera-Zone JSON

Check that `camera_zones.json` exists.

The file must not contain active camera zones with placeholder coordinates unless they are intentionally marked inactive or unverified.

Bad placeholder example:

```json
{
  "latitude": 0.0,
  "longitude": 0.0,
  "status": "active"
}
```

Validate these rules:

- JSON is valid.
- Every camera zone has a stable `id`.
- Every active camera zone has a latitude and longitude.
- Latitude must be within Kenya’s approximate range: `-5.0` to `5.5`.
- Longitude must be within Kenya’s approximate range: `33.0` to `42.5`.
- Nairobi camera zones should generally be around:
  - Latitude: `-1.0` to `-1.5`
  - Longitude: `36.5` to `37.2`
- `warningRadiusMeters` should be `3000` for Nairobi camera zones.
- `speedLimitKmh` should be present where known.
- `status` should be `"active"` for usable zones.
- `source` should be present.
- `lastUpdated` should be present.

If Python is available, run a JSON validation script:

```bash
python3 - <<'PY'
import json
from pathlib import Path

path = Path("app/src/main/assets/camera_zones.json")
if not path.exists():
    raise SystemExit("ERROR: camera_zones.json not found")

data = json.loads(path.read_text())

if isinstance(data, list):
    zones = data
elif isinstance(data, dict) and "zones" in data:
    zones = data["zones"]
else:
    raise SystemExit("ERROR: Unknown camera_zones.json structure")

errors = []
warnings = []
seen_ids = set()

for z in zones:
    zid = z.get("id")
    if not zid:
        errors.append("Zone missing id")
    elif zid in seen_ids:
        errors.append(f"Duplicate zone id: {zid}")
    else:
        seen_ids.add(zid)

    status = z.get("status", "active")
    lat = z.get("latitude")
    lon = z.get("longitude")

    if status == "active":
        if lat is None or lon is None:
            errors.append(f"{zid}: active zone missing latitude/longitude")
        else:
            if float(lat) == 0.0 and float(lon) == 0.0:
                errors.append(f"{zid}: active zone has placeholder 0.0,0.0 coordinates")
            if not (-5.0 <= float(lat) <= 5.5):
                errors.append(f"{zid}: latitude outside Kenya range: {lat}")
            if not (33.0 <= float(lon) <= 42.5):
                errors.append(f"{zid}: longitude outside Kenya range: {lon}")

    radius = z.get("warningRadiusMeters")
    if radius is not None and int(radius) <= 0:
        errors.append(f"{zid}: warningRadiusMeters must be positive")

    source = str(z.get("source", "")).lower()
    road = str(z.get("roadName", "")).lower()
    location = str(z.get("locationName", "")).lower()
    if "nairobi" in source or "thika" in road or "nairobi" in location:
        if radius != 3000:
            warnings.append(f"{zid}: Nairobi-related zone warningRadiusMeters is {radius}, expected 3000")

print(f"Validated {len(zones)} camera zones")

if warnings:
    print("\nWARNINGS:")
    for w in warnings:
        print("-", w)

if errors:
    print("\nERRORS:")
    for e in errors:
        print("-", e)
    raise SystemExit(1)

print("camera_zones.json validation passed")
PY
```

If validation fails, fix the JSON before continuing.

---

## Step 3: Run the Basic Build Gate

Run:

```bash
./gradlew clean
./gradlew test
./gradlew assembleDebug
```

If any command fails:

1. Read the exact error.
2. Identify the smallest safe fix.
3. Apply the fix.
4. Rerun the failed command.
5. Continue until all three commands pass.

Do not skip failing tests.

---

## Step 4: Test Camera Proximity Logic

Look for existing proximity logic, likely in files such as:

```text
CameraProximityDetector.kt
NearbyCameraResult.kt
CameraZone.kt
```

Search:

```bash
grep -R "CameraProximity" -n app/src/main app/src/test || true
grep -R "NearbyCamera" -n app/src/main app/src/test || true
grep -R "warningRadiusMeters" -n app/src/main app/src/test || true
```

Confirm that the app can:

- Find the nearest camera.
- Calculate distance in meters.
- Return whether the user is within the warning radius.
- Use `3000` meters for Nairobi camera zones.
- Handle empty camera-zone lists safely.
- Ignore invalid coordinates if needed.

If no unit tests exist, add tests.

Suggested tests:

```text
CameraProximityDetectorTest
```

Test cases:

1. User is outside 3 km → not within warning radius.
2. User is inside 3 km → within warning radius.
3. Nearest camera is correctly selected.
4. Empty camera list returns null or safe result.
5. Camera with invalid coordinates does not crash the app.

---

## Step 5: Test Proximity Warning Count Logic

The app should warn the driver only twice when entering a 3 km camera zone, unless the driver exits and re-enters.

Search for state tracking:

```bash
grep -R "WarningState" -n app/src/main app/src/test || true
grep -R "proximityWarning" -n app/src/main app/src/test || true
grep -R "shouldWarnProximity" -n app/src/main app/src/test || true
```

Expected behavior:

- First time inside a camera zone → proximity warning allowed.
- Second time inside same camera zone → proximity warning allowed.
- Third time inside same camera zone → proximity warning blocked.
- Exit the 3 km zone → state resets.
- Re-enter same camera zone → first and second warnings allowed again.
- Different camera zones must have separate counts.

If the state tracker does not exist, create one in:

```text
app/src/main/java/com/drivesafe/kenya/alerts/CameraWarningStateTracker.kt
```

Expected model:

```kotlin
class CameraWarningStateTracker(
    private val maxProximityWarningsPerEntry: Int = 2
) {
    private val proximityWarningCounts = mutableMapOf<String, Int>()
    private val activeCameraZoneIds = mutableSetOf<String>()

    fun shouldWarnProximity(cameraId: String, isWithinZone: Boolean): Boolean {
        if (!isWithinZone) {
            activeCameraZoneIds.remove(cameraId)
            proximityWarningCounts.remove(cameraId)
            return false
        }

        activeCameraZoneIds.add(cameraId)

        val count = proximityWarningCounts[cameraId] ?: 0
        if (count >= maxProximityWarningsPerEntry) {
            return false
        }

        proximityWarningCounts[cameraId] = count + 1
        return true
    }

    fun resetZonesNotCurrentlyNearby(currentNearbyCameraIds: Set<String>) {
        val exitedZones = activeCameraZoneIds - currentNearbyCameraIds
        exitedZones.forEach { cameraId ->
            activeCameraZoneIds.remove(cameraId)
            proximityWarningCounts.remove(cameraId)
        }
    }

    fun resetAll() {
        activeCameraZoneIds.clear()
        proximityWarningCounts.clear()
    }
}
```

Add tests:

```text
CameraWarningStateTrackerTest
```

Required test cases:

1. Allows first proximity warning.
2. Allows second proximity warning.
3. Blocks third proximity warning.
4. Resets after exit.
5. Tracks different camera IDs independently.

---

## Step 6: Test Overspeed Logic

Search:

```bash
grep -R "Overspeed" -n app/src/main app/src/test || true
grep -R "speedLimit" -n app/src/main app/src/test || true
```

Expected behavior:

- If user is inside a 3 km camera zone and speed is below or equal to the speed limit, only the limited proximity warnings should be used.
- If user is inside a 3 km camera zone and speed is above the speed limit, overspeed warning must continue.
- Overspeed warning must not be blocked by the two-warning proximity limit.
- Overspeed warning should use an alert cooldown to avoid speaking every second.
- Recommended cooldown: 10–15 seconds, unless the existing app already has a sensible cooldown.

Add or update tests:

```text
OverspeedDetectorTest
CameraZoneAlertDecisionTest
```

Required test cases:

1. Speed below limit → not overspeeding.
2. Speed equal to limit → not overspeeding.
3. Speed above limit plus tolerance → overspeeding.
4. Overspeed inside camera zone → warning allowed even after proximity warnings are exhausted.
5. Overspeed outside camera zone → do not give camera-zone overspeed warning unless the app has separate general overspeed behavior.

---

## Step 7: Test Alert Decision Logic

If alert decision logic is mixed inside the UI, extract it into a testable class.

Suggested file:

```text
app/src/main/java/com/drivesafe/kenya/alerts/CameraAlertDecisionEngine.kt
```

Suggested result enum:

```kotlin
enum class CameraAlertType {
    NONE,
    PROXIMITY,
    OVERSPEED
}
```

Suggested result model:

```kotlin
data class CameraAlertDecision(
    val type: CameraAlertType,
    val message: String? = null
)
```

The decision engine should decide:

```text
outside 3 km + not overspeeding = NONE
inside 3 km + not overspeeding + count 1 = PROXIMITY
inside 3 km + not overspeeding + count 2 = PROXIMITY
inside 3 km + not overspeeding + count 3 = NONE
inside 3 km + overspeeding = OVERSPEED
```

Overspeed must win over proximity.

Add tests to prove:

- Overspeed warning has priority.
- Proximity warning limit does not block overspeed warning.
- Exiting the zone resets proximity count.
- Nearest camera changes are handled safely.

---

## Step 8: Test AlertManager Cooldown

Search:

```bash
grep -R "AlertManager" -n app/src/main app/src/test || true
grep -R "TextToSpeech" -n app/src/main || true
grep -R "Vibrator" -n app/src/main || true
grep -R "cooldown" -n app/src/main app/src/test || true
```

Confirm:

- Text-To-Speech is not triggered every second.
- Overspeed warning repeats after cooldown while still overspeeding.
- Proximity warning is not repeated endlessly.
- AlertManager does not crash if TTS is not ready.
- TTS is shut down in lifecycle cleanup.
- Vibration respects user settings if settings already exist.

If cooldown is missing, add it.

Recommended defaults:

```text
proximity warning cooldown: 10 seconds
overspeed warning cooldown: 10 to 15 seconds
```

Do not make cooldown too long because overspeed warnings should remain useful.

---

## Step 9: Test UI State

Inspect the driving screen:

```bash
grep -R "DrivingScreen" -n app/src/main || true
grep -R "Nearest camera" -n app/src/main || true
grep -R "collectAsState" -n app/src/main || true
```

Confirm the UI shows or can show:

- Current speed
- Nearest camera name
- Distance to nearest camera
- Speed limit
- Warning status

Expected examples:

```text
Nearest camera: Safari Park
Distance: 2.4 km
Speed limit: 110 km/h
Status: Camera zone ahead
Status: Overspeeding - slow down
```

Run Compose-related tests if available.

---

## Step 10: Run Android Lint If Available

Run:

```bash
./gradlew lintDebug
```

If lint fails, inspect the report:

```bash
find . -path "*lint-results*" -type f
```

Fix serious issues.

Do not spend time on cosmetic warnings unless they affect app safety, permissions, crashes, or release readiness.

---

## Step 11: Run Full Verification Gate

Run the final gate:

```bash
./gradlew clean
./gradlew test
./gradlew assembleDebug
./gradlew lintDebug
```

If emulator/device tests exist, also run:

```bash
./gradlew connectedDebugAndroidTest
```

Only run `connectedDebugAndroidTest` if a device or emulator is connected.

Check connected devices with:

```bash
adb devices
```

---

## Step 12: Manual Simulation Checklist

After build succeeds, explain how to manually test the app.

Manual test checklist:

1. Launch app.
2. Grant location permission.
3. Start Driving Mode.
4. Confirm current speed area appears.
5. Confirm nearest camera section appears.
6. Simulate or physically approach a camera zone.
7. At 3 km or less, confirm the proximity warning plays.
8. Confirm the proximity warning plays only twice while staying in the same zone.
9. Confirm no endless proximity warning happens while under the speed limit.
10. Simulate speed above limit.
11. Confirm overspeed warning repeats continuously with cooldown.
12. Slow down below limit.
13. Confirm overspeed warning stops.
14. Exit the 3 km zone.
15. Re-enter the same zone.
16. Confirm the two proximity warnings are allowed again.

If testing with mock locations, suggest using Android emulator location controls or a mock-location app.

---

## Step 13: Report Format

At the end, provide a clear report.

Use this format:

```markdown
# DriveSafeKenya Test Loop Report

## Result
PASS / FAIL

## Commands Run
- ./gradlew clean
- ./gradlew test
- ./gradlew assembleDebug
- ./gradlew lintDebug

## Files Inspected
- ...

## Files Changed
- ...

## Camera-Zone Validation
- Number of zones:
- Active zones:
- Zones with valid coordinates:
- Zones with placeholder coordinates:
- Nairobi zones using 3000m warning radius:

## Warning Logic Validation
- 3 km proximity warning:
- Maximum two proximity warnings:
- Reset after exit:
- Separate count per camera:
- Continuous overspeed warning:
- Cooldown:

## Tests Added or Updated
- ...

## Test Results
Paste the relevant Gradle result summary.

## Issues Found
- ...

## Fixes Applied
- ...

## Remaining Risks / Manual Verification Needed
- Low-confidence coordinates should be verified manually.
- Real camera pole locations may differ from approximate camera-zone coordinates.
- GPS accuracy may vary by phone and road environment.

## Next Recommended Step
State the next safe action.
```

---

## Important Rules

- Do not change the package name.
- Do not remove existing features.
- Do not delete working code unless necessary.
- Do not hardcode UI-only logic where it cannot be tested.
- Prefer small classes that can be unit tested.
- Prefer deterministic tests.
- Keep camera coordinates in `camera_zones.json`, not scattered in Kotlin files.
- Treat coordinates as camera zones, not exact legal camera pole positions.
- Keep warnings driver-friendly and not distracting.
- Never make the app encourage speeding.
