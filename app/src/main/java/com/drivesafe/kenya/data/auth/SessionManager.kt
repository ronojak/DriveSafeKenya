package com.drivesafe.kenya.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionStore by preferencesDataStore(name = "session")

class SessionManager(private val context: Context) {

    private val tokenKey = stringPreferencesKey("session_token")
    private val userIdKey = stringPreferencesKey("session_user_id")
    private val emailKey = stringPreferencesKey("session_email")
    private val nameKey = stringPreferencesKey("session_name")

    val isLoggedIn: Flow<Boolean> = context.sessionStore.data.map { prefs ->
        prefs[tokenKey]?.isNotBlank() == true
    }

    suspend fun save(token: String, userId: String, email: String, name: String?) {
        context.sessionStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[userIdKey] = userId
            prefs[emailKey] = email
            prefs[nameKey] = name ?: ""
        }
    }

    suspend fun clear() {
        context.sessionStore.edit { prefs ->
            prefs[tokenKey] = ""
            prefs[userIdKey] = ""
            prefs[emailKey] = ""
            prefs[nameKey] = ""
        }
    }

    suspend fun token(): String? =
        context.sessionStore.data.first()[tokenKey]?.takeIf { it.isNotBlank() }

    suspend fun userId(): String? =
        context.sessionStore.data.first()[userIdKey]?.takeIf { it.isNotBlank() }

    suspend fun email(): String? =
        context.sessionStore.data.first()[emailKey]?.takeIf { it.isNotBlank() }

    suspend fun name(): String? =
        context.sessionStore.data.first()[nameKey]?.takeIf { it.isNotBlank() }
}
