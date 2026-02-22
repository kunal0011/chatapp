package com.chatapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.chatapp.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND ownerId = :ownerId ORDER BY createdAt ASC")
    fun getMessages(conversationId: String, ownerId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Transaction
    suspend fun replaceTempMessage(tempId: String, ownerId: String, permanentMessage: MessageEntity) {
        deleteMessageById(tempId, ownerId)
        insertMessage(permanentMessage)
    }

    @Query("SELECT * FROM messages WHERE id = :id AND ownerId = :ownerId")
    suspend fun getMessageById(id: String, ownerId: String): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteMessageById(id: String, ownerId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND ownerId = :ownerId")
    suspend fun deleteMessagesForConversation(conversationId: String, ownerId: String)
}
