# PRODUCT REQUIREMENTS DOCUMENT

## Project: DriveSafe Kenya — Speed Camera & Speed Limit Alert Android App

---

# 1. Product Overview

## 1.1 Product Name

**DriveSafe Kenya**

Alternative names:

* Speed Alert Kenya
* Nairobi Speed Alert
* Drive Safe Nairobi
* Camera Alert Kenya

## 1.2 Product Description

DriveSafe Kenya is an Android mobile application that uses the user’s GPS location and travelling speed to alert them when they are approaching known speed-camera areas, monitored corridors, or speed-limit zones.

The first version will combine:

1. **Offline Android App**

   * Works without internet.
   * Stores speed-camera and speed-limit data locally.
   * Shows current speed.
   * Alerts the driver when near a camera or monitored zone.
   * Warns when the user exceeds the speed limit.

2. **Online Update System**

   * Allows the app to download updated speed-camera and speed-limit data from an online backend later.
   * Stores downloaded updates locally.
   * Allows the app to continue working even when internet is unavailable.
   * Allows future admin management of camera locations and speed zones.

The app must be positioned as a **road safety and speed-awareness tool**, not as a tool for avoiding law enforcement.

---

# 2. Problem Statement

Drivers in Nairobi and Greater Nairobi may not always know when they are approaching a monitored speed-camera corridor or a reduced-speed zone. Many major roads have different speed limits, for example:

* 50 km/h on urban roads and junctions
* 80 km/h on many major dual carriageways
* Up to 110 km/h on parts of Thika Road
* Mostly 80 km/h on the Nairobi Expressway

The problem is that drivers may unintentionally exceed speed limits due to lack of real-time awareness.

DriveSafe Kenya will help by giving timely alerts based on:

* Current user location
* Current speed
* Nearby camera or monitored corridor
* Known speed limit for that road section

---

# 3. Product Goals

## 3.1 Main Goals

The app should:

1. Detect the user’s current location.
2. Detect or calculate the user’s current travelling speed.
3. Compare the user’s location with known speed-camera and monitored-corridor data.
4. Alert the user before reaching a known camera or speed-monitoring zone.
5. Warn the user if they are travelling above the known speed limit.
6. Work offline using locally stored camera data.
7. Sync updated camera and speed-limit data when internet is available.
8. Allow future admin management of camera data.

## 3.2 Safety Goal

The app must encourage safe driving by reminding users to obey posted speed limits and official road signs.

---

# 4. Target Users

## 4.1 Primary Users

* Private vehicle drivers in Nairobi and Greater Nairobi
* Taxi drivers
* Matatu drivers
* Delivery riders/drivers
* Long-distance drivers entering Nairobi
* Fleet drivers
* Company drivers

## 4.2 Secondary Users

* Fleet managers
* Driving schools
* Road safety awareness groups
* Insurance or logistics companies

---

# 5. MVP Scope

## 5.1 Included in MVP

The MVP will include:

1. Android app built in Kotlin.
2. Current speed display.
3. Location permission handling.
4. Local speed-camera/speed-limit JSON database.
5. Nearby camera detection.
6. Overspeed warning.
7. Voice alerts.
8. Vibration alerts.
9. Basic settings screen.
10. Offline operation.
11. Preparation for later online sync.

## 5.2 Not Included in MVP

The MVP will not include:

1. Turn-by-turn navigation.
2. Live traffic.
3. Full route planning.
4. Social user reports.
5. AI-based driving analysis.
6. Police/camera evasion features.
7. Real-time government camera feed.
8. Payment system.
9. Fleet management dashboard.

---

# 6. Initial Speed Camera and Speed Limit Dataset

The initial speed-camera and speed-limit information comes from the extracted Nairobi & Greater Nairobi speed-camera image.

## 6.1 Thika Superhighway

| Location                       | Speed Limit |
| ------------------------------ | ----------: |
| Safari Park                    |    110 km/h |
| Jomoko / Thika Turnoff         |     80 km/h |
| Allsops / GSU HQ               |     80 km/h |
| Pangani / Muthaiga Interchange |     80 km/h |
| Roysambu / TRM                 | 80–100 km/h |

## 6.2 Nairobi Expressway

| Location                | Speed Limit |
| ----------------------- | ----------: |
| Museum Hill → Westlands |     80 km/h |
| After Nyayo Stadium     |     80 km/h |

## 6.3 Mombasa Road

| Location                         | Speed Limit |
| -------------------------------- | ----------: |
| Nyayo Stadium → Sameer / GM Area |     80 km/h |
| Cabanas → JKIA Stretch           |     80 km/h |

## 6.4 Southern Bypass

| Location                                  | Speed Limit |
| ----------------------------------------- | ----------: |
| Near Virtual Weighbridge, Ngong Road Side |     80 km/h |

## 6.5 Northern Bypass

| Location         | Speed Limit |
| ---------------- | ----------: |
| Gitaru / Wangige |     80 km/h |
| Ruaka / Wangige  |     80 km/h |

## 6.6 Waiyaki Way

| Location         | Speed Limit |
| ---------------- | ----------: |
| Kangemi → Uthiru |  60–80 km/h |

## 6.7 Other Monitored Corridors

| Location                      | Speed Limit |
| ----------------------------- | ----------: |
| Kiambu Road                   |  50–80 km/h |
| Limuru Road                   |  50–80 km/h |
| Likoni Road / Industrial Area |  50–80 km/h |
| Urban Roads / Junctions       |     50 km/h |

## 6.8 General Speed Limits to Remember

| Road Type               |    Speed Limit |
| ----------------------- | -------------: |
| Urban Roads / Junctions |        50 km/h |
| Major Dual Carriageways |        80 km/h |
| Parts of Thika Road     | Up to 110 km/h |
| Expressway              | Mostly 80 km/h |

---

# 7. Important Data Limitation

The image provides road names and speed limits, but it does not provide exact GPS coordinates.

Therefore, before the app can work accurately, each camera or monitored zone must be assigned:

* Latitude
* Longitude
* Road name
* Location name
* Direction, if known
* Warning radius
* Speed limit
* Verification status

The first version may use manually entered approximate coordinates, but production release should use verified coordinates.

If coordinates are not verified, the data must be clearly marked:

```json
{
  "verified": false,
  "latitude": 0.0,
  "longitude": 0.0
}
```

The app must not pretend that unverified data is accurate.

---

# 8. Functional Requirements

---

## 8.1 Location Permission

### Requirement

The app must request permission to access the user’s location.

### Permissions Needed

For foreground driving mode:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

For future background mode:

```xml
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
```

### Acceptance Criteria

* App explains clearly why location is needed.
* User can deny permission without app crashing.
* App only starts driving mode after permission is granted.
* If permission is denied, app shows a helpful message.

---

## 8.2 Current Speed Detection

### Requirement

The app must display the user’s current speed in km/h.

### Logic

Android GPS returns speed in metres per second.

Conversion:

```kotlin
val speedKmh = location.speed * 3.6
```

### Display

```text
Current Speed
82 km/h
```

### Acceptance Criteria

* Speed updates while the user is moving.
* Speed displays as km/h.
* Speed shows `0 km/h` or `--` when location speed is unavailable.
* App handles GPS inaccuracy gracefully.

---

## 8.3 Local Camera Database

### Requirement

The app must include a local speed-camera and speed-zone database.

For MVP, use a local JSON file first.

Later, migrate to Room Database.

### File Location

```text
app/src/main/assets/camera_zones.json
```

### Example JSON Structure

```json
[
  {
    "id": "thika_safari_park_001",
    "roadName": "Thika Superhighway",
    "locationName": "Safari Park",
    "latitude": 0.0,
    "longitude": 0.0,
    "speedLimitKmh": 110,
    "minSpeedLimitKmh": null,
    "maxSpeedLimitKmh": 110,
    "warningRadiusMeters": 700,
    "cameraType": "monitored_zone",
    "direction": "unknown",
    "status": "active",
    "verified": false,
    "source": "initial_image_dataset",
    "lastUpdated": "2026-06-22"
  }
]
```

### Acceptance Criteria

* App loads camera data from local JSON.
* App does not crash if the JSON file is missing or malformed.
* Each camera/zone has a unique ID.
* App can calculate distance from user to each camera/zone.

---

## 8.4 Nearby Camera Detection

### Requirement

The app must detect when a user is within a warning radius of a camera or monitored zone.

### Logic

```kotlin
val distance = userLocation.distanceTo(cameraLocation)

if (distance <= camera.warningRadiusMeters) {
    triggerCameraAlert(camera)
}
```

### Default Warning Radius

```text
700 metres
```

User should later be able to choose:

* 300 metres
* 500 metres
* 700 metres
* 1 kilometre

### Acceptance Criteria

* App detects nearby cameras based on radius.
* App shows camera name and speed limit.
* App avoids repeating the same alert too frequently.
* App alerts again only after a cooldown period or after user leaves and re-enters the zone.

---

## 8.5 Overspeed Detection

### Requirement

The app must detect when the user is driving above the speed limit for a nearby road/camera zone.

### Logic

```kotlin
val toleranceKmh = 5

if (currentSpeedKmh > speedLimitKmh + toleranceKmh) {
    triggerOverspeedAlert()
}
```

### Handling Variable Speed Limits

For speed ranges like 60–80 km/h:

* Display: `Variable limit: 60–80 km/h`
* Strong overspeed alert above 80 km/h.
* Safety warning may display when above 60 km/h in urban or unclear areas.

### Acceptance Criteria

* App warns when speed exceeds limit plus tolerance.
* App does not over-alert due to small GPS errors.
* Variable speed limits are shown clearly.
* User is reminded to obey posted signs.

---

## 8.6 Voice Alerts

### Requirement

The app must provide voice warnings.

### Example Voice Messages

Nearby camera:

```text
Speed camera ahead. Limit is 80 kilometres per hour.
```

Overspeed:

```text
You are above the speed limit. Please slow down.
```

Strong warning:

```text
Warning. Speed camera ahead and you are above the speed limit. Reduce speed now.
```

### Acceptance Criteria

* Voice alert works when enabled.
* Voice can be turned off in settings.
* Voice alert does not repeat continuously.
* App handles phone silent mode respectfully.

---

## 8.7 Vibration Alerts

### Requirement

The app should vibrate when important alerts occur.

### Alert Pattern

* Short vibration for nearby camera.
* Longer vibration for overspeed near camera.

### Acceptance Criteria

* Vibration can be enabled or disabled.
* Vibration triggers only for relevant alerts.
* App does not vibrate repeatedly without cooldown.

---

## 8.8 Alert Cooldown

### Requirement

The app must avoid repeating the same alert too many times.

### Default Cooldowns

```text
Same camera alert cooldown: 2 minutes
Overspeed alert cooldown: 30 seconds
Strong warning cooldown: 30 seconds
```

### Acceptance Criteria

* App does not keep repeating the same warning every second.
* App can re-alert if the user remains overspeeding after cooldown.
* App can alert again when user enters a different camera zone.

---

## 8.9 Settings Screen

### Requirement

The app must allow users to configure alerts.

### Settings

* Voice alerts: On/Off
* Vibration alerts: On/Off
* Warning distance: 300m / 500m / 700m / 1000m
* Overspeed tolerance: 5 km/h / 10 km/h
* Show speed in km/h
* Data update check: Manual / Automatic

### Acceptance Criteria

* Settings are saved locally.
* Settings remain after app restart.
* Settings affect alert behavior immediately.

---

## 8.10 Data Update System

### Requirement

The app should be designed to download new camera and speed-limit data from an online backend.

### MVP Implementation

For early version:

* Local JSON file bundled with app.
* Manual replacement/update during development.

### Later Implementation

App checks API:

```text
GET /api/camera-zones/version
GET /api/camera-zones
```

If server version is newer:

1. Download latest camera list.
2. Validate JSON.
3. Save to local database.
4. Use updated data for alerts.

### Acceptance Criteria

* App uses local data immediately.
* App syncs when internet is available.
* App continues working when internet is unavailable.
* Failed sync does not break driving mode.

---

# 9. Non-Functional Requirements

## 9.1 Performance

* App should respond quickly to location changes.
* Alert detection should not freeze the UI.
* Camera search should be optimized when database grows.

## 9.2 Battery Usage

* Location updates should be balanced between accuracy and battery.
* Driving mode should only run when user starts it.
* Future background mode should use foreground service notification.

## 9.3 Reliability

* App must not crash if GPS signal is weak.
* App must not crash if data sync fails.
* App must handle missing speed value from GPS.

## 9.4 Privacy

* User location should not be uploaded in MVP.
* The app should process location locally.
* If analytics are added later, user consent is required.

## 9.5 Safety

* App must remind users to obey official road signs.
* App must not encourage speeding.
* App must not claim data is official unless verified.

## 9.6 Legal/Compliance Disclaimer

The app must include this disclaimer:

```text
Speed limits and camera alerts are provided for driver awareness only. Always obey posted road signs and official traffic laws. Camera locations and speed limits may change.
```

---

# 10. User Interface Requirements

## 10.1 Home Screen

### Elements

```text
DriveSafe Kenya

[ Start Driving Mode ]

Current Speed: --
Nearest Camera: --
Speed Limit: --
Last Data Update: --
```

### Buttons

* Start Driving Mode
* Map later
* Settings
* Check for Updates

---

## 10.2 Driving Mode Screen

### Elements

```text
Current Speed
82 km/h

Speed Limit
80 km/h

Nearby Zone
Thika Superhighway - Allsops / GSU HQ

Distance
500 metres

[ Stop Driving Mode ]
```

### Visual States

Normal:

```text
Speed is within limit.
```

Warning:

```text
Camera ahead.
```

Danger:

```text
You are above the speed limit.
```

---

## 10.3 Alert Banner

### Nearby Camera Alert

```text
Speed camera ahead
Thika Superhighway - Safari Park
Limit: 110 km/h
Distance: 650m
```

### Overspeed Alert

```text
Slow down
Your speed: 88 km/h
Limit: 80 km/h
```

---

## 10.4 Settings Screen

### Options

```text
Voice Alerts: ON/OFF
Vibration Alerts: ON/OFF
Warning Distance: 700m
Overspeed Tolerance: 5 km/h
Check Data Updates Automatically: ON/OFF
```

---

## 10.5 Data Update Screen

### Elements

```text
Camera Database Version: 1.0
Last Updated: 22 June 2026
Number of Camera Zones: 17

[ Check for Updates ]
```

---

# 11. Data Model

## 11.1 CameraZone

```kotlin
data class CameraZone(
    val id: String,
    val roadName: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val speedLimitKmh: Int?,
    val minSpeedLimitKmh: Int?,
    val maxSpeedLimitKmh: Int?,
    val warningRadiusMeters: Int,
    val cameraType: String,
    val direction: String?,
    val status: String,
    val verified: Boolean,
    val source: String,
    val lastUpdated: String
)
```

## 11.2 AppSettings

```kotlin
data class AppSettings(
    val voiceAlertsEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val warningDistanceMeters: Int,
    val overspeedToleranceKmh: Int,
    val automaticUpdatesEnabled: Boolean
)
```

## 11.3 AlertHistory

```kotlin
data class AlertHistory(
    val id: String,
    val cameraZoneId: String,
    val alertTime: Long,
    val userSpeedKmh: Double,
    val speedLimitKmh: Int?,
    val distanceMeters: Float,
    val alertType: String
)
```

---

# 12. Alert Engine Logic

## 12.1 Basic Flow

```text
Start Driving Mode
    ↓
Receive location update
    ↓
Calculate current speed
    ↓
Load active camera zones
    ↓
Find nearest camera zone
    ↓
Check if distance <= warning radius
    ↓
Check if speed > limit + tolerance
    ↓
Trigger correct alert
```

## 12.2 Alert Types

| Alert Type       | Condition                                  |
| ---------------- | ------------------------------------------ |
| Nearby Camera    | User is within warning radius              |
| Overspeed        | User speed exceeds speed limit + tolerance |
| Strong Warning   | User is near camera and overspeeding       |
| Corridor Warning | User enters monitored corridor             |

## 12.3 Cooldown Rules

| Alert            |   Cooldown |
| ---------------- | ---------: |
| Nearby Camera    |  2 minutes |
| Overspeed        | 30 seconds |
| Strong Warning   | 30 seconds |
| Same Zone Repeat |  2 minutes |

---

# 13. Technical Architecture

```text
Android App
│
├── UI Layer
│   ├── Home Screen
│   ├── Driving Screen
│   ├── Settings Screen
│   └── Data Update Screen
│
├── Location Module
│   ├── Requests location permission
│   ├── Gets GPS location updates
│   └── Calculates speed
│
├── Alert Engine
│   ├── Finds nearby camera zones
│   ├── Checks speed limit
│   ├── Applies alert cooldown
│   └── Sends alert events
│
├── Local Data Layer
│   ├── Local JSON seed
│   ├── Room Database later
│   └── User settings
│
├── Alert Output Layer
│   ├── Screen banner
│   ├── Voice alert
│   └── Vibration alert
│
└── Sync Layer
    ├── Checks online version
    ├── Downloads latest data
    ├── Validates data
    └── Stores data locally
```

---

# 14. Backend Requirements for Option 2

The backend can be added after the offline MVP.

Recommended backend:

```text
Django + Django REST Framework + PostgreSQL
```

## 14.1 Backend Models

### CameraZone

Fields:

```text
id
road_name
location_name
latitude
longitude
speed_limit_kmh
min_speed_limit_kmh
max_speed_limit_kmh
warning_radius_meters
camera_type
direction
status
verified
source
created_at
updated_at
```

### DatasetVersion

Fields:

```text
version
published_at
notes
is_active
```

## 14.2 API Endpoints

```text
GET /api/health
GET /api/camera-zones/version
GET /api/camera-zones
GET /api/camera-zones/{id}
```

Future admin endpoints:

```text
POST /api/admin/camera-zones
PUT /api/admin/camera-zones/{id}
DELETE /api/admin/camera-zones/{id}
POST /api/admin/publish-dataset
```

## 14.3 Admin Panel

Use Django Admin first.

Admin should be able to:

* Add camera zone
* Edit camera zone
* Mark as active/inactive
* Set speed limit
* Set warning radius
* Mark as verified/unverified
* Publish updated dataset

---

# 15. Recommended Technology Stack

## 15.1 Android

```text
Kotlin
Jetpack Compose
Android Studio
Fused Location Provider
Room Database later
DataStore Preferences
Text-to-Speech
Vibration API
Foreground Service later
Google Maps SDK later
```

## 15.2 Backend

```text
Python
Django
Django REST Framework
PostgreSQL
Gunicorn
Nginx
Ubuntu VPS
```

## 15.3 DevOps

```text
GitHub
GitHub Actions later
Docker later
UFW firewall
SSL certificate
Backups
Monitoring
```

---

# 16. Required Skills

## 16.1 Android Development Skills

Required:

* Kotlin programming
* Jetpack Compose UI
* Android lifecycle
* Android permissions
* Location services
* Fused Location Provider
* GPS speed handling
* Background/foreground services later
* Text-to-Speech
* Vibration alerts
* Local JSON parsing
* Room Database later
* DataStore Preferences
* Error handling
* Android testing
* Google Play release preparation

## 16.2 Mapping and Geolocation Skills

Required:

* Latitude and longitude handling
* Distance calculation
* Radius-based detection
* Basic geofencing concepts
* Road corridor modelling
* Speed zone modelling
* GPS accuracy handling
* Direction handling later
* Map markers later

## 16.3 Backend Development Skills

Required for Option 2:

* Python
* Django
* Django REST Framework
* PostgreSQL
* REST API design
* Dataset versioning
* JSON API responses
* Data validation
* Admin dashboard management
* Server deployment

## 16.4 DevOps Skills

Required later:

* Ubuntu server management
* Nginx
* Gunicorn
* PostgreSQL setup
* Domain and SSL setup
* Environment variables
* Firewall configuration
* Backups
* Monitoring
* Git deployment

## 16.5 Product/Data Skills

Required:

* Road data collection
* GPS coordinate verification
* Speed-limit verification
* Safety disclaimer writing
* User testing
* Driver feedback collection
* Alert-distance tuning
* False-alert reduction
* Data update process

---

# 17. Claude Skills Available on This Machine

The following Claude skills are currently installed in:

```text
~/.claude/skills
```

Installed skills:

```text
autoplan
freeze
plan-design-review
benchmark
gstack
plan-eng-review
browse
gstack-upgrade
qa
canary
guard
qa-only
careful
humanizer
retro
codex
investigate
review
cso
land-and-deploy
setup-browser-cookies
design-consultation
last30days
setup-deploy
design-review
office-hours
ship
document-release
plan-ceo-review
unfreeze
```

Superpowers is also installed globally as a Claude plugin. It may not appear inside `~/.claude/skills`, but Claude Code reported:

```text
Plugin 'superpowers@claude-plugins-official' is already installed globally.
```

Therefore, DriveSafe Kenya should use:

```text
1. The installed local Claude skills listed above.
2. The globally installed Superpowers plugin.
3. The custom project skill: drivesafe-loop.
```

---

# 18. Recommended Claude Skill Usage for DriveSafe Kenya

The main workflow should be:

```text
Superpowers + drivesafe-loop + careful + review + qa
```

Claude Code should not rush directly into coding. It should inspect, plan, implement a small change, build/test, review, fix, and repeat.

---

## 18.1 Main Project Loop Skill

### Skill: drivesafe-loop

Use this custom skill for all DriveSafe Kenya development work.

Purpose:

```text
Controls the development loop:
Plan → Implement → Test → Review → Fix → Repeat
```

Use when:

```text
Starting any DriveSafe Kenya task.
Adding a feature.
Fixing a bug.
Reviewing code.
Preparing release.
```

Recommended first prompt inside Claude Code:

```text
Use the drivesafe-loop skill. We are building the DriveSafe Kenya Android app. Start with Phase 1: inspect the project, confirm the Android structure, and prepare a small implementation plan. Do not code until you have inspected the files.
```

After Claude gives a good plan, continue with:

```text
GO. Implement Phase 1 only.
```

---

## 18.2 Superpowers Plugin

Use Superpowers as the general development framework.

Use it for:

```text
Brainstorming
Planning
Step-by-step implementation
Debugging
Testing discipline
Code review
Refactoring
Release preparation
```

Recommended prompt:

```text
Use Superpowers and the drivesafe-loop skill. Work carefully and implement only one small DriveSafe Kenya phase at a time.
```

---

## 18.3 Planning Skills

### autoplan

Use for:

* Breaking the PRD into small tasks
* Creating an implementation roadmap
* Creating milestones
* Deciding what to build first
* Preventing Claude from implementing too much at once

### plan-design-review

Use for:

* Reviewing the planned user experience
* Checking the screen flow
* Checking if the app is simple enough for drivers
* Checking if alerts are clear and safe

### plan-eng-review

Use for:

* Reviewing the technical architecture before coding
* Checking Android package structure
* Checking location service design
* Checking alert engine design
* Checking sync design
* Checking Room database design

### plan-ceo-review

Use for:

* Checking whether the product direction makes business sense
* Confirming MVP scope
* Avoiding overbuilding
* Confirming safety positioning
* Checking launch readiness

---

## 18.4 Coding and Debugging Skills

### careful

Use for:

* Location permission handling
* GPS speed handling
* Alert cooldown logic
* Distance calculation
* Foreground service logic later

### codex

Use for:

* Writing code
* Editing Kotlin files
* Generating Android classes
* Creating backend code later
* Refactoring existing code

### investigate

Use for:

* Debugging build errors
* Investigating Gradle errors
* Investigating Android permission issues
* Investigating crashes
* Investigating GPS/location problems
* Investigating JSON parsing problems

### guard

Use for:

* Checking safety issues
* Checking privacy issues
* Checking whether location data is being uploaded
* Checking that the app does not encourage camera avoidance
* Checking risky code changes

### cso

Use for:

* Security review
* Privacy review
* Backend security later
* API exposure review
* Admin protection
* Environment variable checks

---

## 18.5 Testing and Quality Skills

### qa

Use for:

* Testing completed features
* Running test checklist
* Checking app behaviour
* Checking location and speed features
* Checking alert logic
* Checking offline behaviour

### qa-only

Use when:

* You want Claude to test and review only.
* You do not want code changes.
* You want a pure QA pass.

### benchmark

Use for:

* Checking performance
* Checking whether camera-zone search is efficient
* Checking battery-sensitive logic later
* Comparing alert engine approaches

### canary

Use for:

* Testing risky changes gradually
* Trying a new approach before fully adopting it
* Checking whether a change breaks the app

---

## 18.6 Review and Release Skills

### review

Use for:

* Reviewing code after implementation
* Checking for obvious bugs
* Checking code quality
* Checking whether Claude changed too many files
* Checking whether acceptance criteria are met

### retro

Use for:

* Reviewing what went well and badly after a phase
* Improving the development process
* Capturing lessons learned
* Planning the next phase better

### freeze

Use for:

* Freezing a stable point before major changes
* Preventing unnecessary changes
* Marking a working baseline

### unfreeze

Use for:

* Resuming changes after a freeze
* Allowing next phase development

### setup-deploy

Use for:

* Preparing deployment setup
* Preparing backend deployment later
* Configuring server deployment workflow

### land-and-deploy

Use for:

* Finalizing changes
* Preparing deployable version
* Checking deployment readiness

### ship

Use for:

* Final release checklist
* Google Play readiness
* APK/AAB readiness
* Release confidence check

### document-release

Use for:

* Writing release notes
* Updating README
* Writing changelog
* Documenting version changes
* Preparing Play Store release notes

### humanizer

Use for:

* Improving user-facing wording
* Making alerts sound natural
* Improving disclaimer language
* Improving Play Store description
* Improving release notes

---

# 19. Recommended Skill Combination by Phase

## Phase 1: Project Setup

Use:

```text
drivesafe-loop
Superpowers
autoplan
plan-eng-review
careful
qa
review
```

Goal:

```text
Create or confirm Android project and make sure it builds.
```

## Phase 2: Local Camera Data

Use:

```text
drivesafe-loop
careful
codex
review
qa
guard
```

Goal:

```text
Create camera_zones.json, CameraZone model, and JSON loader.
```

## Phase 3: Location and Speed

Use:

```text
drivesafe-loop
careful
codex
investigate
qa
review
guard
```

Goal:

```text
Request permission and display live speed in km/h.
```

## Phase 4: Nearby Camera Detection

Use:

```text
drivesafe-loop
careful
codex
benchmark
qa
review
```

Goal:

```text
Calculate distance and detect nearby camera zones.
```

## Phase 5: Overspeed Warning

Use:

```text
drivesafe-loop
careful
codex
qa
review
guard
humanizer
```

Goal:

```text
Warn users safely and clearly when above the speed limit.
```

## Phase 6: Voice and Vibration

Use:

```text
drivesafe-loop
careful
codex
qa
review
humanizer
```

Goal:

```text
Add voice and vibration warnings with cooldown.
```

## Phase 7: Settings

Use:

```text
drivesafe-loop
design-consultation
design-review
codex
qa
review
```

Goal:

```text
Add settings for voice, vibration, warning distance, and speed tolerance.
```

## Phase 8: Room Database

Use:

```text
drivesafe-loop
plan-eng-review
careful
codex
investigate
qa
review
canary
```

Goal:

```text
Move camera-zone data into local database.
```

## Phase 9: Online Sync Preparation

Use:

```text
drivesafe-loop
plan-eng-review
careful
codex
guard
cso
qa
review
```

Goal:

```text
Prepare the Android app for Django API sync.
```

## Phase 10: Django Backend Later

Use:

```text
drivesafe-loop
Superpowers
autoplan
plan-eng-review
codex
cso
setup-deploy
land-and-deploy
qa
review
document-release
```

Goal:

```text
Create Django API, PostgreSQL database, and Django Admin for camera-zone updates.
```

---

# 20. Claude Code Workflow Prompts

## 20.1 Standard Claude Code Startup Prompt

Use this prompt at the start of the project:

```text
Use Superpowers and the drivesafe-loop skill.

We are building DriveSafe Kenya, an Android Kotlin app that:
- Gets the user’s GPS location.
- Shows current speed in km/h.
- Loads camera and speed-zone data from local JSON.
- Alerts near speed-camera or monitored corridors.
- Warns when the user exceeds known speed limits.
- Works offline first.
- Later syncs with a Django backend.

Use the installed skills where appropriate:
autoplan, plan-design-review, plan-eng-review, design-review, design-consultation, careful, codex, investigate, guard, cso, qa, qa-only, review, benchmark, canary, freeze, unfreeze, gstack, land-and-deploy, setup-deploy, ship, document-release, humanizer, retro.

Start with Phase 1 only:
Inspect the current project folder, confirm whether an Android project already exists, list relevant files, and prepare a small implementation plan.

Do not code until you have inspected the files and presented the plan.
```

## 20.2 Standard GO Prompt

After Claude Code gives a plan, use:

```text
GO. Implement Phase 1 only using drivesafe-loop. Keep the change small, run the build/check command, and summarize:
Completed
Files changed
Commands run
Result
Known issues
Next recommended step
```

## 20.3 Standard Review Prompt

After implementation, use:

```text
Use review and qa-only. Review the last changes against the DriveSafe Kenya PRD. Do not edit files. Confirm whether the project builds, whether the implementation matches the phase goal, and what should be fixed before moving to the next phase.
```

## 20.4 Standard Fix Prompt

If there is an error, use:

```text
Use investigate and careful. Diagnose this error and make the smallest possible fix. Do not rewrite unrelated files. After fixing, rerun the failed command and summarize the result.
```

## 20.5 Standard Freeze Prompt

When a phase works, use:

```text
Use freeze. Mark the current DriveSafe Kenya state as stable after Phase [number]. Summarize what works, what files changed, and what should not be changed without review.
```

---

# 21. Recommended Folder Structure

```text
DriveSafeKenya/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── camera_zones.json
│   │   ├── java/com/drivesafe/kenya/
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   │   ├── CameraZone.kt
│   │   │   │   ├── CameraZoneRepository.kt
│   │   │   │   └── LocalJsonDataSource.kt
│   │   │   ├── location/
│   │   │   │   ├── LocationManager.kt
│   │   │   │   └── SpeedCalculator.kt
│   │   │   ├── alerts/
│   │   │   │   ├── AlertEngine.kt
│   │   │   │   ├── VoiceAlertManager.kt
│   │   │   │   └── VibrationAlertManager.kt
│   │   │   ├── settings/
│   │   │   │   └── SettingsRepository.kt
│   │   │   ├── sync/
│   │   │   │   └── SyncManager.kt
│   │   │   └── ui/
│   │   │       ├── HomeScreen.kt
│   │   │       ├── DrivingScreen.kt
│   │   │       ├── SettingsScreen.kt
│   │   │       └── DataUpdateScreen.kt
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── docs/
│   ├── PRD.md
│   ├── ROADMAP.md
│   └── DATA_FORMAT.md
└── README.md
```

---

# 22. Initial JSON Seed Data

Initial file:

```text
app/src/main/assets/camera_zones.json
```

Example:

```json
[
  {
    "id": "thika_safari_park_001",
    "roadName": "Thika Superhighway",
    "locationName": "Safari Park",
    "latitude": 0.0,
    "longitude": 0.0,
    "speedLimitKmh": 110,
    "minSpeedLimitKmh": null,
    "maxSpeedLimitKmh": 110,
    "warningRadiusMeters": 700,
    "cameraType": "monitored_zone",
    "direction": "unknown",
    "status": "active",
    "verified": false,
    "source": "initial_image_dataset",
    "lastUpdated": "2026-06-22"
  },
  {
    "id": "thika_jomoko_thika_turnoff_001",
    "roadName": "Thika Superhighway",
    "locationName": "Jomoko / Thika Turnoff",
    "latitude": 0.0,
    "longitude": 0.0,
    "speedLimitKmh": 80,
    "minSpeedLimitKmh": null,
    "maxSpeedLimitKmh": 80,
    "warningRadiusMeters": 700,
    "cameraType": "monitored_zone",
    "direction": "unknown",
    "status": "active",
    "verified": false,
    "source": "initial_image_dataset",
    "lastUpdated": "2026-06-22"
  }
]
```

Note: latitude and longitude must be replaced with verified coordinates.

---

# 23. Acceptance Criteria for MVP

The MVP is complete when:

1. User can open the Android app.
2. User can grant location permission.
3. User can start driving mode.
4. App displays current speed in km/h.
5. App loads speed-camera data from local JSON.
6. App calculates distance to camera zones.
7. App shows nearest camera/zone.
8. App alerts when user is within warning radius.
9. App warns when user exceeds speed limit.
10. Voice alert works.
11. Vibration alert works.
12. Settings can enable/disable alerts.
13. App works without internet.
14. App does not crash when GPS is unavailable.
15. App includes safety disclaimer.
16. App marks unverified data clearly.
17. App does not upload user location during MVP.

---

# 24. Future Features

After MVP:

1. Online Django backend.
2. Django Admin dashboard.
3. PostgreSQL database.
4. Automatic camera-data sync.
5. Google Maps or Mapbox display.
6. User camera reports.
7. Admin verification workflow.
8. Direction-aware alerts.
9. Corridor/polyline-based speed zones.
10. Background driving mode.
11. Fleet version.
12. Subscription or premium features later.

---

# 25. Risks

| Risk                            | Impact                      | Mitigation                      |
| ------------------------------- | --------------------------- | ------------------------------- |
| Incorrect camera coordinates    | False alerts                | Verify GPS coordinates manually |
| GPS inaccuracy                  | Wrong speed/distance        | Add tolerance and cooldown      |
| Outdated speed limits           | User confusion              | Add online sync and disclaimer  |
| Battery drain                   | Poor user experience        | Use driving mode only first     |
| Background permission rejection | App limitation              | Start foreground-only           |
| Legal concerns                  | App rejection or complaints | Position as safety app          |
| Too many alerts                 | User disables app           | Add cooldown and settings       |
| App perceived as camera evasion | Policy/legal risk           | Use safety-focused language     |

---

# 26. Final Recommendation

Build in this order:

```text
1. Offline Android MVP
2. Local JSON camera data
3. Current speed display
4. Nearby camera alert
5. Overspeed warning
6. Voice and vibration alerts
7. Settings screen
8. Room database
9. Online sync
10. Django backend and admin panel
```

The first practical deliverable should be:

```text
An Android app that displays speed and warns near locally stored speed-camera zones.
```

The project must be developed using:

```text
Superpowers + drivesafe-loop + careful + review + qa
```

Do not move to the next phase until the current phase builds and passes review.

# End of PRD

