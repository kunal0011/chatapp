package com.chatapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chatapp.domain.model.Conversation
import java.time.Instant

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val contactName: String,
    val lastMessage: String?,
    val lastMessageTime: Long?,
    val lastSeen: Long? = null,
    val isMuted: Boolean = false
)

fun ConversationEntity.toDomain() = Conversation(
    id = id,
    contactName = contactName,
    lastMessage = lastMessage,
    lastMessageTime = lastMessageTime?.let { Instant.ofEpochMilli(it) },
    lastSeen = lastSeen?.let { Instant.ofEpochMilli(it) },
    isMuted = isMuted
)

fun Conversation.toEntity() = ConversationEntity(
    id = id,
    contactName = contactName,
    lastMessage = lastMessage,
    lastMessageTime = lastMessageTime?.toEpochMilli(),
    lastSeen = lastSeen?.toEpochMilli(),
    isMuted = isMuted
)
