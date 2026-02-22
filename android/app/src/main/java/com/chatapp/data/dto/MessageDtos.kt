package com.chatapp.data.dto

import com.chatapp.domain.model.ChatMessage
import com.chatapp.domain.model.MessageReaction
import com.chatapp.domain.model.MessageStatus
import com.google.gson.annotations.SerializedName
import java.time.Instant

data class MessagesResponse(
    @SerializedName("messages") val messages: List<ApiMessage>,
    @SerializedName("nextCursor") val nextCursor: String?
)

data class UnifiedSearchResponse(
    @SerializedName("messages") val messages: List<ApiMessage>,
    @SerializedName("contacts") val contacts: List<ApiUser>,
    @SerializedName("groups") val groups: List<ApiConversation>
)

data class ApiMessage(
    @SerializedName("id") val id: String,
    @SerializedName("conversationId") val conversationId: String,
    @SerializedName("senderId") val senderId: String,
    @SerializedName("content") val content: String,
    @SerializedName("type") val type: String? = null,
    @SerializedName("status") val status: String,
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("isEdited") val isEdited: Boolean = false,
    @SerializedName("parentId") val parentId: String? = null,
    @SerializedName("parent") val parent: ApiParentMessage? = null,
    @SerializedName("reactions") val reactions: List<ApiReaction>? = null,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("sender") val sender: ApiUser
)

data class ApiParentMessage(
    @SerializedName("id") val id: String,
    @SerializedName("content") val content: String,
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("isEdited") val isEdited: Boolean = false,
    @SerializedName("sender") val sender: ApiUser
)

data class ApiReaction(
    @SerializedName("userId") val userId: String,
    @SerializedName("emoji") val emoji: String
)

data class SocketMessageEnvelope(
    @SerializedName("message") val message: ApiMessage
)

fun ApiMessage.toDomain(): ChatMessage {
    val messageType = when (type) {
        "SYSTEM" -> com.chatapp.domain.model.MessageType.SYSTEM
        else -> com.chatapp.domain.model.MessageType.USER
    }

    return ChatMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = sender.displayName,
        content = content,
        type = messageType,
        createdAt = Instant.parse(createdAt),
        isDeleted = isDeleted,
        isEdited = isEdited,
        parentId = parentId,
        parentContent = parent?.content,
        parentSenderName = parent?.sender?.displayName,
        reactions = reactions?.map { MessageReaction(it.userId, it.emoji) } ?: emptyList(),
        status = when (status) {
            "READ" -> MessageStatus.READ
            "DELIVERED" -> MessageStatus.DELIVERED
            else -> MessageStatus.SENT
        }
    )
}
