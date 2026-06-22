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
        if (currentZoneId != lastZoneId) {
            lastAlertTimes.clear()
            lastZoneId = currentZoneId
        }

        val isNearby = nearbyCamera?.isInWarningRadius == true
        val isOverspeed = overspeedResult?.status == OverspeedStatus.OVERSPEED

        val alertType = when {
            isNearby && isOverspeed -> AlertType.STRONG_WARNING
            isOverspeed -> AlertType.OVERSPEED
            isNearby -> AlertType.NEARBY_CAMERA
            else -> return
        }

        if (!isCooldownExpired(alertType)) return

        if (voiceEnabled) speak(buildMessage(alertType, overspeedResult))
        if (vibrationEnabled) vibrate(alertType)
        lastAlertTimes[alertType] = SystemClock.elapsedRealtime()
    }

    fun reset() {
        tts?.stop()
        lastAlertTimes.clear()
        lastZoneId = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        lastAlertTimes.clear()
        lastZoneId = null
    }

    private fun isCooldownExpired(type: AlertType): Boolean {
        val lastTime = lastAlertTimes[type] ?: return true
        val cooldownMs = when (type) {
            AlertType.NEARBY_CAMERA -> COOLDOWN_NEARBY_MS
            AlertType.OVERSPEED -> COOLDOWN_OVERSPEED_MS
            AlertType.STRONG_WARNING -> COOLDOWN_STRONG_MS
        }
        return SystemClock.elapsedRealtime() - lastTime >= cooldownMs
    }

    private fun buildMessage(type: AlertType, overspeedResult: OverspeedResult?): String {
        return when (type) {
            AlertType.NEARBY_CAMERA -> {
                val limit = overspeedResult?.applicableLimitKmh
                if (limit != null) {
                    "Speed camera ahead. Limit is $limit kilometres per hour."
                } else {
                    "Speed camera ahead."
                }
            }
            AlertType.OVERSPEED ->
                "You are above the speed limit. Please slow down."
            AlertType.STRONG_WARNING ->
                "Warning. Speed camera ahead and you are above the speed limit. Reduce speed now."
        }
    }

    private fun speak(message: String) {
        if (!ttsReady) return
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun vibrate(type: AlertType) {
        val pattern = when (type) {
            AlertType.NEARBY_CAMERA -> VIBRATE_NEARBY
            AlertType.OVERSPEED, AlertType.STRONG_WARNING -> VIBRATE_OVERSPEED
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    companion object {
        private const val TAG = "AlertManager"
        private const val UTTERANCE_ID = "drivesafe_alert"
        private const val COOLDOWN_NEARBY_MS = 120_000L
        private const val COOLDOWN_OVERSPEED_MS = 30_000L
        private const val COOLDOWN_STRONG_MS = 30_000L
        private val VIBRATE_NEARBY = longArrayOf(0, 200)
        private val VIBRATE_OVERSPEED = longArrayOf(0, 300, 100, 300)
    }
}
