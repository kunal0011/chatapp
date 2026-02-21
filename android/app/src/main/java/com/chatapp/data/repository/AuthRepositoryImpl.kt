package com.chatapp.data.repository

import com.chatapp.data.api.AuthApi
import com.chatapp.data.dto.*
import com.chatapp.data.store.SessionStore
import com.chatapp.domain.model.Session
import com.chatapp.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore
) : AuthRepository {
    override suspend fun requestOtp(phone: String) {
        authApi.requestOtp(OtpRequest(phone))
    }

    override suspend fun verifyOtp(phone: String, code: String): Pair<Boolean, Session?> {
        val response = authApi.verifyOtp(OtpVerifyRequest(phone, code))
        val session = response.toSession()
        if (session != null) {
            sessionStore.saveSession(session)
        }
        return response.isNewUser to session
    }

    override suspend fun register(phone: String, password: String, displayName: String): Session {
        val response = authApi.register(AuthRequest(phone, password, displayName))
        val session = response.toSession()
        sessionStore.saveSession(session)
        return session
    }

    override suspend fun login(phone: String, password: String): Session {
        val response = authApi.login(AuthRequest(phone, password))
        val session = response.toSession()
        sessionStore.saveSession(session)
        return session
    }

    override suspend fun logout() {
        val session = sessionStore.getSessionOrNull()
        if (session != null) {
            runCatching { authApi.logout(RefreshRequest(session.refreshToken)) }
        }
        sessionStore.clearSession()
    }
}
