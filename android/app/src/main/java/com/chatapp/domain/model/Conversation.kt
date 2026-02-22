package com.chatapp.domain.model

enum class ConversationType {
    DIRECT, GROUP
}

data class Conversation(
    val id: String,
    val type: ConversationType = ConversationType.DIRECT,
    val name: String,
    val avatarUrl: String? = null,
    val description: String? = null,
    val creatorId: String? = null,
    val otherMemberId: String? = null,
    val memberCount: Int = 0,
    val isMember: Boolean = true,
    val lastMessage: String? = null,
    val lastMessageTime: java.time.Instant? = null,
    val isMuted: Boolean = false
)
