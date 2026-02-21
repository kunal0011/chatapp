package com.chatapp.domain.model

import java.time.Instant

enum class MessageStatus {
    SENT,
    DELIVERED,
    READ
}

data class MessageReaction(
    val userId: String,
    val emoji: String
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val createdAt: Instant,
    val status: MessageStatus = MessageStatus.SENT,
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false,
    val parentId: String? = null,
    val parentContent: String? = null,
    val parentSenderName: String? = null,
    val reactions: List<MessageReaction> = emptyList()
)
