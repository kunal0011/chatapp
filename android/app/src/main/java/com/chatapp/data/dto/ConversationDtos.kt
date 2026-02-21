package com.chatapp.data.dto

import com.chatapp.domain.model.Conversation
import com.google.gson.annotations.SerializedName
import java.time.Instant

data class DirectConversationRequest(
    @SerializedName("otherUserId") val otherUserId: String
)

data class ConversationResponseEnvelope(
    @SerializedName("conversation") val conversation: ApiConversation
)

data class ConversationsResponseEnvelope(
    @SerializedName("conversations") val conversations: List<ApiConversation>
)

data class ApiConversation(
    @SerializedName("id") val id: String,
    @SerializedName("members") val members: List<ApiConversationMember>,
    @SerializedName("messages") val messages: List<ApiMessage>? = null
)

data class ApiConversationMember(
    @SerializedName("user") val user: ApiUser,
    @SerializedName("isMuted") val isMuted: Boolean = false
)

fun ApiConversation.toDomain(currentUserId: String): Conversation {
    val otherMember = members.firstOrNull { it.user.id != currentUserId }
    val contactName = otherMember?.user?.displayName ?: "Unknown"
    val lastMsg = messages?.firstOrNull()
    val me = members.firstOrNull { it.user.id == currentUserId }

    return Conversation(
        id = id,
        contactName = contactName,
        lastMessage = lastMsg?.content,
        lastMessageTime = lastMsg?.let { Instant.parse(it.createdAt) },
        lastSeen = otherMember?.user?.lastSeen?.let { Instant.parse(it) },
        isMuted = me?.isMuted ?: false
    )
}
