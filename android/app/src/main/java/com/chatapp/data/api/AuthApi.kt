package com.chatapp.data.api

import com.chatapp.data.dto.*
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/otp/request")
    suspend fun requestOtp(@Body request: OtpRequest): OtpResponse

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body request: OtpVerifyRequest): OtpVerifyResponse

    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthResponse

    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest): Unit
}
