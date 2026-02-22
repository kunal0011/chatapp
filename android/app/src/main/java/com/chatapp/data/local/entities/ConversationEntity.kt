package com.chatapp.data.local.entities

import androidx.room.Entity
import com.chatapp.domain.model.Conversation
import com.chatapp.domain.model.ConversationType
import java.time.Instant

@Entity(
    tableName = "conversations",
    primaryKeys = ["id", "ownerId"]
)
data class ConversationEntity(
    val id: String,
    val ownerId: String, // Partition data by logged-in user
    val type: String, // DIRECT or GROUP
    val name: String,
    val avatarUrl: String? = null,
    val description: String? = null,
    val creatorId: String? = null,
    val otherMemberId: String? = null,
    val memberCount: Int = 0,
    val isMember: Boolean = true,
    val lastMessage: String?,
    val lastMessageTime: Long?,
    val isMuted: Boolean = false
)

fun ConversationEntity.toDomain(): Conversation {
    val conversationType = when (type) {
        "GROUP" -> ConversationType.GROUP
        else -> ConversationType.DIRECT
    }

    return Conversation(
        id = id,
        type = conversationType,
        name = name,
        avatarUrl = avatarUrl,
        description = description,
        creatorId = creatorId,
        otherMemberId = otherMemberId,
        memberCount = memberCount,
        isMember = isMember,
        lastMessage = lastMessage,
        lastMessageTime = lastMessageTime?.let { Instant.ofEpochMilli(it) },
        isMuted = isMuted
    )
}

fun Conversation.toEntity(ownerId: String) = ConversationEntity(
    id = id,
    ownerId = ownerId,
    type = type.name,
    name = name,
    avatarUrl = avatarUrl,
    description = description,
    creatorId = creatorId,
    otherMemberId = otherMemberId,
    memberCount = memberCount,
    isMember = isMember,
    lastMessage = lastMessage,
    lastMessageTime = lastMessageTime?.toEpochMilli(),
    isMuted = isMuted
)
