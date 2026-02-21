package com.chatapp.data.dto

import com.chatapp.domain.model.Session
import com.google.gson.annotations.SerializedName
import java.time.Instant

data class AuthRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("password") val password: String,
    @SerializedName("displayName") val displayName: String? = null
)

data class OtpRequest(
    @SerializedName("phone") val phone: String
)

data class OtpResponse(
    @SerializedName("message") val message: String
)

data class OtpVerifyRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("code") val code: String
)

data class OtpVerifyResponse(
    @SerializedName("isNewUser") val isNewUser: Boolean,
    @SerializedName("user") val user: ApiUser? = null,
    @SerializedName("accessToken") val accessToken: String? = null,
    @SerializedName("refreshToken") val refreshToken: String? = null
)

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class ApiUser(
    @SerializedName("id") val id: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("lastSeen") val lastSeen: String? = null
)

data class AuthResponse(
    @SerializedName("user") val user: ApiUser,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String
)

fun AuthResponse.toSession(): Session {
    return Session(
        userId = user.id,
        displayName = user.displayName,
        phone = user.phone,
        accessToken = accessToken,
        refreshToken = refreshToken
    )
}

fun OtpVerifyResponse.toSession(): Session? {
    if (user == null || accessToken == null || refreshToken == null) return null
    return Session(
        userId = user.id,
        displayName = user.displayName,
        phone = user.phone,
        accessToken = accessToken,
        refreshToken = refreshToken
    )
}

fun ApiUser.toDomain(): com.chatapp.domain.model.User {
    return com.chatapp.domain.model.User(
        id = id,
        phone = phone,
        displayName = displayName,
        lastSeen = lastSeen?.let { Instant.parse(it) }
    )
}
