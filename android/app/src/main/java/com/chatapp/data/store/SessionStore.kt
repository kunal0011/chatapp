package com.chatapp.data.store

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chatapp.domain.model.Session
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "chatapp_session")

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val userId = stringPreferencesKey("user_id")
        val displayName = stringPreferencesKey("display_name")
        val phone = stringPreferencesKey("phone")
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
    }

    val sessionFlow: Flow<Session?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs.toSessionOrNull() }

    suspend fun saveSession(session: Session) {
        context.dataStore.edit { prefs ->
            prefs[Keys.userId] = session.userId
            prefs[Keys.displayName] = session.displayName
            prefs[Keys.phone] = session.phone
            prefs[Keys.accessToken] = session.accessToken
            prefs[Keys.refreshToken] = session.refreshToken
        }
    }

    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.accessToken] = access
            prefs[Keys.refreshToken] = refresh
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.userId)
            prefs.remove(Keys.displayName)
            prefs.remove(Keys.phone)
            prefs.remove(Keys.accessToken)
            prefs.remove(Keys.refreshToken)
        }
    }

    suspend fun getSessionOrNull(): Session? {
        return sessionFlow.first()
    }

    private fun Preferences.toSessionOrNull(): Session? {
        val userId = this[Keys.userId] ?: return null
        val displayName = this[Keys.displayName] ?: return null
        val phone = this[Keys.phone] ?: return null
        val accessToken = this[Keys.accessToken] ?: return null
        val refreshToken = this[Keys.refreshToken] ?: return null
        return Session(
            userId = userId,
            displayName = displayName,
            phone = phone,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }
}
