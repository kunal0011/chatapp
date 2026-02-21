package com.chatapp.domain.model

import java.time.Instant

data class User(
    val id: String,
    val phone: String,
    val displayName: String,
    val lastSeen: Instant? = null
)
