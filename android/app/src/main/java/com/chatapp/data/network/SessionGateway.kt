package com.chatapp.data.network

import com.chatapp.data.api.AuthApi
import com.chatapp.data.dto.RefreshRequest
import com.chatapp.data.dto.toSession
import com.chatapp.data.store.SessionStore
import com.chatapp.domain.model.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SessionGateway @Inject constructor(
    private val sessionStore: SessionStore,
    private val authApi: Provider<AuthApi>
) {
    private val mutex = Mutex()

    suspend fun getSession(): Session? {
        return sessionStore.sessionFlow.first()
    }

    suspend fun requireSession(): Session {
        return getSession() ?: throw IllegalStateException("Session required but not found")
    }

    suspend fun currentUserIdOrNull(): String? {
        return getSession()?.userId
    }

    suspend fun <T> withAccessToken(block: suspend (String) -> T): T {
        val session = requireSession()
        return block(session.accessToken)
    }

    suspend fun refreshSession(): Session {
        mutex.withLock {
            val currentSession = requireSession()
            val response = authApi.get().refresh(RefreshRequest(currentSession.refreshToken))
            val newSession = response.toSession()
            sessionStore.saveSession(newSession)
            return newSession
        }
    }
}
