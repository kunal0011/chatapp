package com.chatapp.domain.model

import java.time.Instant

data class GroupMember(
    val userId: String,
    val displayName: String,
    val phone: String,
    val role: String, // ADMIN, MEMBER
    val joinedAt: Instant,
    val lastReadTime: Instant? = null,
    val lastDeliveredTime: Instant? = null,
    val lastSeen: Instant? = null
)
