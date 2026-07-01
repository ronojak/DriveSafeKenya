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

    suspend fun fetchActiveAlerts(lat: Double, lon: Double) {
        try {
            val response = api.getActive(lat, lon)
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

    suspend fun confirmPresent(alertId: String) {
        try {
            val response = api.confirmPresent(alertId, ConfirmRequest(deviceHash))
            if (response.isSuccessful) {
                response.body()?.toDomain()?.let { updated ->
                    _alerts.value = _alerts.value.map { if (it.id == alertId) updated else it }
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun confirmNotPresent(alertId: String) {
        try {
            val response = api.confirmNotPresent(alertId, ConfirmRequest(deviceHash))
            if (response.isSuccessful) {
                response.body()?.toDomain()?.let { updated ->
                    _alerts.value = _alerts.value.map { if (it.id == alertId) updated else it }
                        .filter { it.isActive() }
                }
            }
        } catch (_: Exception) {}
    }
}

// ── Error body parsing ───────────────────────────────────────────────────────

private fun parseErrorField(json: String): String {
    // Extract {"error":"..."} from DRF error responses
    val match = Regex(""""error"\s*:\s*"([^"]+)"""").find(json)
    return match?.groupValues?.get(1) ?: "Could not submit report."
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
        source = source
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
