package com.chatapp.domain.repository

import com.chatapp.domain.model.Session

interface AuthRepository {
    suspend fun requestOtp(phone: String)
    suspend fun verifyOtp(phone: String, code: String): Pair<Boolean, Session?> // isNewUser, Session
    suspend fun login(phone: String, password: String): Session
    suspend fun register(phone: String, password: String, displayName: String): Session
    suspend fun logout()
}
