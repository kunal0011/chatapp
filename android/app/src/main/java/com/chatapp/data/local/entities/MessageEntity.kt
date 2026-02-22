package com.chatapp.data.local.entities

import androidx.room.Entity
import com.chatapp.domain.model.ChatMessage
import com.chatapp.domain.model.MessageReaction
import com.chatapp.domain.model.MessageStatus
import com.chatapp.domain.model.MessageType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

@Entity(
    tableName = "messages",
    primaryKeys = ["id", "ownerId"]
)
data class MessageEntity(
    val id: String,
    val ownerId: String, // Partition data by logged-in user
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val type: String, // USER, SYSTEM
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
    val typeToken = object : TypeToken<List<MessageReaction>>() {}.type
    val reactions: List<MessageReaction> = if (reactionsJson != null) gson.fromJson(reactionsJson, typeToken) else emptyList()

    return ChatMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        content = content,
        type = try { MessageType.valueOf(type) } catch (e: Exception) { MessageType.USER },
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

fun ChatMessage.toEntity(ownerId: String): MessageEntity {
    val gson = Gson()
    val reactionsJson = if (reactions.isNotEmpty()) gson.toJson(reactions) else null

    return MessageEntity(
        id = id,
        ownerId = ownerId,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        content = content,
        type = type.name,
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
