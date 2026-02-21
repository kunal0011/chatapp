package com.chatapp.domain.model

data class Session(
    val userId: String,
    val displayName: String,
    val phone: String,
    val accessToken: String,
    val refreshToken: String
)
