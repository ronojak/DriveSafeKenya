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
