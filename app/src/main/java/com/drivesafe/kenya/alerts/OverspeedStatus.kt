package com.drivesafe.kenya.alerts

enum class OverspeedStatus {
    WITHIN_LIMIT,
    OVERSPEED
}

data class OverspeedResult(
    val status: OverspeedStatus,
    val applicableLimitKmh: Int?,
    val toleranceKmh: Int?
)
