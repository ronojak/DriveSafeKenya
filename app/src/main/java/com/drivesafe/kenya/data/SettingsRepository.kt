package com.drivesafe.kenya.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            voiceAlertsEnabled = prefs[KEY_VOICE] ?: true,
            vibrationAlertsEnabled = prefs[KEY_VIBRATION] ?: true,
            warningDistanceMeters = prefs[KEY_WARNING_DISTANCE] ?: 700,
            overspeedToleranceKmh = prefs[KEY_TOLERANCE] ?: 5,
            keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: false
        )
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        AppThemeMode.fromStoredValue(prefs[KEY_THEME_MODE])
    }

    suspend fun setVoiceAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VOICE] = enabled }
    }

    suspend fun setVibrationAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIBRATION] = enabled }
    }

    suspend fun setWarningDistance(meters: Int) {
        context.dataStore.edit { it[KEY_WARNING_DISTANCE] = meters }
    }

    suspend fun setOverspeedTolerance(kmh: Int) {
        context.dataStore.edit { it[KEY_TOLERANCE] = kmh }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setThemeMode(themeMode: AppThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = themeMode.name }
    }

    companion object {
        private val KEY_VOICE = booleanPreferencesKey("voice_alerts_enabled")
        private val KEY_VIBRATION = booleanPreferencesKey("vibration_alerts_enabled")
        private val KEY_WARNING_DISTANCE = intPreferencesKey("warning_distance_meters")
        private val KEY_TOLERANCE = intPreferencesKey("overspeed_tolerance_kmh")
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
