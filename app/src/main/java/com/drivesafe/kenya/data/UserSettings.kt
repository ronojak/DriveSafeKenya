package com.drivesafe.kenya.data

data class UserSettings(
    val voiceAlertsEnabled: Boolean = true,
    val vibrationAlertsEnabled: Boolean = true,
    val warningDistanceMeters: Int = 700,
    val overspeedToleranceKmh: Int = 5,
    val keepScreenOn: Boolean = false
)
