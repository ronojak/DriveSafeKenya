package com.drivesafe.kenya.alerts

import com.drivesafe.kenya.data.CameraZone

data class NearbyCameraResult(
    val zone: CameraZone,
    val distanceMeters: Float,
    val isInWarningRadius: Boolean
)
