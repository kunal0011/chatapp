package com.chatapp.domain.model

import java.time.Instant

data class MessageRecipientStatus(
    val userId: String,
    val displayName: String,
    val phone: String,
    val status: String, // READ, DELIVERED, SENT
    val timestamp: Instant?
)
