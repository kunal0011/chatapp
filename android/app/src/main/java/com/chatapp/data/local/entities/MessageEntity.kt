package com.chatapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.chatapp.domain.model.ChatMessage
import com.chatapp.domain.model.MessageReaction
import com.chatapp.domain.model.MessageStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val createdAt: Long,
    val status: String,
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false,
    val parentId: String? = null,
    val parentContent: String? = null,
    val parentSenderName: String? = null,
    val reactionsJson: String? = null
)

fun MessageEntity.toDomain(): ChatMessage {
    val gson = Gson()
    val type = object : TypeToken<List<MessageReaction>>() {}.type
    val reactions: List<MessageReaction> = if (reactionsJson != null) gson.fromJson(reactionsJson, type) else emptyList()
    
    return ChatMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        content = content,
        createdAt = Instant.ofEpochMilli(createdAt),
        status = MessageStatus.valueOf(status),
        isDeleted = isDeleted,
        isEdited = isEdited,
        parentId = parentId,
        parentContent = parentContent,
        parentSenderName = parentSenderName,
        reactions = reactions
    )
}

fun ChatMessage.toEntity(): MessageEntity {
    val gson = Gson()
    val reactionsJson = if (reactions.isNotEmpty()) gson.toJson(reactions) else null
    
    return MessageEntity(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        content = content,
        createdAt = createdAt.toEpochMilli(),
        status = status.name,
        isDeleted = isDeleted,
        isEdited = isEdited,
        parentId = parentId,
        parentContent = parentContent,
        parentSenderName = parentSenderName,
        reactionsJson = reactionsJson
    )
}
