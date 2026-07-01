package com.drivesafe.kenya.alerts

data class NearbyPolicePresenceResult(
    val alert: PolicePresenceAlert,
    val distanceMeters: Float,
    val isWithinWarningRadius: Boolean,
    val warningRadiusMeters: Int = 10_000,
    val needsConfirmation: Boolean
)
