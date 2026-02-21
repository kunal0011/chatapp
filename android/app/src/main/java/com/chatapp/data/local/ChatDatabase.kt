package com.chatapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chatapp.data.local.dao.ConversationDao
import com.chatapp.data.local.dao.MessageDao
import com.chatapp.data.local.entities.ConversationEntity
import com.chatapp.data.local.entities.MessageEntity
import com.chatapp.data.local.entities.UserEntity

@Database(
    entities = [UserEntity::class, ConversationEntity::class, MessageEntity::class],
    version = 2, // Incremented version due to schema changes (isMuted, reactionsJson, etc)
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
