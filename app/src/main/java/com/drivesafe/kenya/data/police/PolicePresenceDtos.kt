package com.drivesafe.kenya.data.police

import com.google.gson.annotations.SerializedName

data class ReportPolicePresenceRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("device_hash") val deviceHash: String
)

data class ConfirmRequest(
    @SerializedName("device_hash") val deviceHash: String
)

data class PolicePresenceAlertDto(
    val id: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("reported_at") val reportedAt: String,
    @SerializedName("confirmation_required_after") val confirmationRequiredAfter: String,
    @SerializedName("expires_at") val expiresAt: String,
    val status: String,
    @SerializedName("present_confirmations") val presentConfirmations: Int,
    @SerializedName("not_present_confirmations") val notPresentConfirmations: Int,
    val source: String = "anonymous_community_report"
)

data class ActiveAlertsDto(
    val alerts: List<PolicePresenceAlertDto>
)
