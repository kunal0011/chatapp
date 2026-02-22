package com.chatapp.data.dto

import com.chatapp.domain.model.Conversation
import com.google.gson.annotations.SerializedName

data class DirectConversationRequest(
    @SerializedName("otherUserId") val otherUserId: String
)

data class ConversationResponseEnvelope(
    @SerializedName("conversation") val conversation: ApiConversation
)

data class ConversationsResponseEnvelope(
    @SerializedName("conversations") val conversations: List<ApiConversation>
)

data class MembersResponse(
    @SerializedName("members") val members: List<ApiMember>
)

data class ApiMember(
    @SerializedName("userId") val userId: String,
    @SerializedName("role") val role: String,
    @SerializedName("joinedAt") val joinedAt: String,
    @SerializedName("user") val user: ApiUser
)

data class GroupConversationRequest(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("memberIds") val memberIds: List<String>
)

data class AddMembersRequest(
    @SerializedName("memberIds") val memberIds: List<String>
)

data class UpdateGroupRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

data class ApiConversation(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("creatorId") val creatorId: String? = null,
    @SerializedName("members") val members: List<ApiConversationMember>? = null,
    @SerializedName("messages") val messages: List<ApiMessage>? = null
)

data class ApiConversationMember(
    @SerializedName("user") val user: ApiUser,
    @SerializedName("role") val role: String,
    @SerializedName("isMuted") val isMuted: Boolean = false
)

fun ApiConversation.toDomain(currentUserId: String): Conversation {
    val lastMsg = messages?.firstOrNull()
    val me = members?.find { it.user.id == currentUserId }

    val displayName = if (type == "GROUP") {
        name ?: "Group Chat"
    } else {
        members?.find { it.user.id != currentUserId }?.user?.displayName ?: "Unknown"
    }

    val conversationType = when (type) {
        "GROUP" -> com.chatapp.domain.model.ConversationType.GROUP
        else -> com.chatapp.domain.model.ConversationType.DIRECT
    }

    val lastMsgTime = try {
        lastMsg?.createdAt?.let { java.time.Instant.parse(it) }
    } catch (e: Exception) {
        null
    }

    return Conversation(
        id = id,
        type = conversationType,
        name = displayName,
        avatarUrl = avatarUrl,
        description = description,
        creatorId = creatorId,
        memberCount = members?.size ?: 0,
        isMember = me != null || (type == "GROUP" && members == null), // Fallback if members list is omitted
        lastMessage = lastMsg?.content,
        lastMessageTime = lastMsgTime,
        isMuted = me?.isMuted ?: false
    )
}
