package com.chatapp.data.network

import android.util.Log
import com.chatapp.data.api.AuthApi
import com.chatapp.data.dto.RefreshRequest
import com.chatapp.data.store.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AuthAuthenticator @Inject constructor(
    private val sessionStore: SessionStore,
    private val authApi: Provider<AuthApi>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val url = response.request.url.toString()
        Log.d("AuthAuthenticator", "401 detected on $url. Attempting refresh...")

        // If the refresh request itself failed with 401, the refresh token is dead.
        if (url.contains("/auth/refresh")) {
            Log.e("AuthAuthenticator", "Refresh token is invalid or expired. Force logging out.")
            runBlocking { sessionStore.clearSession() }
            return null
        }

        synchronized(this) {
            val currentSession = runBlocking { sessionStore.getSessionOrNull() } ?: run {
                Log.e("AuthAuthenticator", "No session found in store, giving up.")
                return null
            }

            val requestToken = response.request.header("Authorization")?.replace("Bearer ", "")
            if (requestToken != currentSession.accessToken) {
                Log.d("AuthAuthenticator", "Token already refreshed by another thread. Retrying with new token.")
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${currentSession.accessToken}")
                    .build()
            }

            Log.d("AuthAuthenticator", "Triggering refresh API call...")
            val newSession = runBlocking {
                runCatching {
                    authApi.get().refresh(RefreshRequest(currentSession.refreshToken))
                }.onFailure {
                    Log.e("AuthAuthenticator", "Refresh API call failed", it)
                }.getOrNull()
            }

            return if (newSession != null) {
                Log.d("AuthAuthenticator", "Refresh successful. Saving new tokens.")
                runBlocking {
                    sessionStore.saveTokens(newSession.accessToken, newSession.refreshToken)
                }
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newSession.accessToken}")
                    .build()
            } else {
                Log.e("AuthAuthenticator", "Refresh failed unrecoverably. Clearing session.")
                runBlocking { sessionStore.clearSession() }
                null
            }
        }
    }
}
