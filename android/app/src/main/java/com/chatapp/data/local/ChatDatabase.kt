package com.chatapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chatapp.data.local.dao.ConversationDao
import com.chatapp.data.local.dao.MemberDao
import com.chatapp.data.local.dao.MessageDao
import com.chatapp.data.local.entities.ConversationEntity
import com.chatapp.data.local.entities.MemberEntity
import com.chatapp.data.local.entities.MessageEntity
import com.chatapp.data.local.entities.UserEntity

@Database(
    entities = [UserEntity::class, ConversationEntity::class, MessageEntity::class, MemberEntity::class],
    version = 13, // Added otherMemberId partitioning
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun memberDao(): MemberDao
}
