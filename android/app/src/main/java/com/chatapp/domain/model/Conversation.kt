package com.chatapp.domain.model

import java.time.Instant

data class Conversation(
    val id: String,
    val contactName: String,
    val lastMessage: String? = null,
    val lastMessageTime: Instant? = null,
    val lastSeen: Instant? = null,
    val isMuted: Boolean = false
)
