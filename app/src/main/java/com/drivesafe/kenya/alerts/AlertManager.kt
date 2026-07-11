package com.drivesafe.kenya.alerts

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class AlertManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ttsReady = false

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.applicationContext
            .getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val lastAlertTimes = mutableMapOf<AlertType, Long>()
    private var lastZoneId: String? = null
    private val warningStateTracker = CameraWarningStateTracker()
    private val proximityAlertEngine = ProximityAlertEngine()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
            Log.d(TAG, "TTS initialized")
        } else {
            Log.e(TAG, "TTS initialization failed with status $status")
        }
    }

    fun evaluate(
        nearbyCamera: NearbyCameraResult?,
        overspeedResult: OverspeedResult?,
        speedKmh: Double?,
        voiceEnabled: Boolean = true,
        vibrationEnabled: Boolean = true
    ) {
        if (speedKmh == null) return

        val currentZoneId = nearbyCamera?.zone?.id
        val isNearby = nearbyCamera?.isInWarningRadius == true
        val isOverspeed = overspeedResult?.status == OverspeedStatus.OVERSPEED

        // Update which zones the driver is currently inside (resets count on exit)
        val nearbyIds = if (isNearby && currentZoneId != null) setOf(currentZoneId) else emptySet()
        warningStateTracker.updateNearbyZones(nearbyIds)

        // Reset time-based cooldowns when the nearest zone changes
        if (currentZoneId != lastZoneId) {
            lastAlertTimes.clear()
            lastZoneId = currentZoneId
        }

        when {
            isNearby && isOverspeed -> {
                if (isCooldownExpired(AlertType.STRONG_WARNING)) {
                    val msg = buildOverspeedMessage(nearbyCamera!!, overspeedResult!!)
                    if (voiceEnabled) speak(msg)
                    if (vibrationEnabled) vibrate(AlertType.STRONG_WARNING)
                    lastAlertTimes[AlertType.STRONG_WARNING] = SystemClock.elapsedRealtime()
                }
            }
            isOverspeed -> {
                if (isCooldownExpired(AlertType.OVERSPEED)) {
                    if (voiceEnabled) speak(buildMessage(AlertType.OVERSPEED, overspeedResult))
                    if (vibrationEnabled) vibrate(AlertType.OVERSPEED)
                    lastAlertTimes[AlertType.OVERSPEED] = SystemClock.elapsedRealtime()
                }
            }
            isNearby -> {
                // Proximity-only warning: max 2 per zone entry, spaced by COOLDOWN_NEARBY_MS
                if (isCooldownExpired(AlertType.NEARBY_CAMERA) &&
                    warningStateTracker.shouldWarnProximity(currentZoneId!!)
                ) {
                    val msg = buildProximityMessage(nearbyCamera!!)
                    if (voiceEnabled) speak(msg)
                    if (vibrationEnabled) vibrate(AlertType.NEARBY_CAMERA)
                    lastAlertTimes[AlertType.NEARBY_CAMERA] = SystemClock.elapsedRealtime()
                }
            }
        }
    }

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
        return "Police reported ${String.format(Locale.US, "%.1f", km)} kilometres ahead."
    }

    private fun buildPoliceEscalationMessage(): String = "Police 400 metres ahead."

    fun reset() {
        tts?.stop()
        lastAlertTimes.clear()
        lastZoneId = null
        warningStateTracker.reset()
        proximityAlertEngine.reset()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        lastAlertTimes.clear()
        lastZoneId = null
        warningStateTracker.reset()
        proximityAlertEngine.reset()
    }

    private fun isCooldownExpired(type: AlertType): Boolean {
        val lastTime = lastAlertTimes[type] ?: return true
        val cooldownMs = when (type) {
            AlertType.NEARBY_CAMERA -> COOLDOWN_NEARBY_MS
            AlertType.OVERSPEED -> COOLDOWN_OVERSPEED_MS
            AlertType.STRONG_WARNING -> COOLDOWN_STRONG_MS
            AlertType.POLICE_PRESENCE -> COOLDOWN_POLICE_MS
        }
        return SystemClock.elapsedRealtime() - lastTime >= cooldownMs
    }

    private fun buildProximityMessage(camera: NearbyCameraResult): String {
        val distanceKm = camera.distanceMeters / 1000f
        val limit = camera.zone.speedLimitKmh
        return if (limit != null) {
            "Camera zone ahead in ${"%.1f".format(distanceKm)} kilometres. Speed limit is $limit kilometres per hour."
        } else {
            "Camera zone ahead in ${"%.1f".format(distanceKm)} kilometres. Please obey the speed limit."
        }
    }

    private fun buildOverspeedMessage(camera: NearbyCameraResult, overspeed: OverspeedResult): String {
        val limit = overspeed.applicableLimitKmh
        return if (limit != null) {
            "You are above the speed limit. Speed limit is $limit kilometres per hour. Slow down. Camera zone ahead."
        } else {
            "You are above the speed limit. Slow down. Camera zone ahead."
        }
    }

    private fun buildMessage(type: AlertType, overspeedResult: OverspeedResult?): String {
        return when (type) {
            AlertType.OVERSPEED ->
                "You are above the speed limit. Please slow down."
            else -> ""
        }
    }

    private fun speak(message: String) {
        if (!ttsReady || message.isEmpty()) return
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun vibrate(type: AlertType) {
        val pattern = when (type) {
            AlertType.NEARBY_CAMERA, AlertType.POLICE_PRESENCE -> VIBRATE_NEARBY
            AlertType.OVERSPEED, AlertType.STRONG_WARNING -> VIBRATE_OVERSPEED
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    companion object {
        private const val TAG = "AlertManager"
        private const val UTTERANCE_ID = "drivesafe_alert"
        // Two proximity warnings per zone entry, spaced 60 s apart
        private const val COOLDOWN_NEARBY_MS = 60_000L
        private const val COOLDOWN_OVERSPEED_MS = 15_000L
        private const val COOLDOWN_STRONG_MS = 15_000L
        private const val COOLDOWN_POLICE_MS = 15_000L
        private val VIBRATE_NEARBY = longArrayOf(0, 200)
        private val VIBRATE_OVERSPEED = longArrayOf(0, 300, 100, 300)
    }
}
