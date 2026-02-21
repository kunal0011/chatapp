package com.chatapp.data.network

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
        val session = runBlocking { sessionStore.getSessionOrNull() } ?: return null

        synchronized(this) {
            val newSession = runBlocking {
                runCatching {
                    authApi.get().refresh(RefreshRequest(session.refreshToken))
                }.getOrNull()
            }

            return if (newSession != null) {
                runBlocking {
                    sessionStore.saveTokens(newSession.accessToken, newSession.refreshToken)
                }
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${newSession.accessToken}")
                    .build()
            } else {
                runBlocking { sessionStore.clearSession() }
                null
            }
        }
    }
}
