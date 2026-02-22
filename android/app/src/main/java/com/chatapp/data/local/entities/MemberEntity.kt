package com.chatapp.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "conversation_members",
    primaryKeys = ["conversationId", "userId", "ownerId"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id", "ownerId"],
            childColumns = ["conversationId", "ownerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId"), Index("ownerId")]
)
data class MemberEntity(
    val conversationId: String,
    val ownerId: String, // Partition data by logged-in user
    val userId: String,
    val displayName: String,
    val phone: String,
    val role: String, // ADMIN, MEMBER
    val isMuted: Boolean = false,
    val lastReadMessageId: String? = null,
    val lastReadTime: Long? = null,
    val lastDeliveredMessageId: String? = null,
    val lastDeliveredTime: Long? = null,
    val lastSeen: Long? = null
)
