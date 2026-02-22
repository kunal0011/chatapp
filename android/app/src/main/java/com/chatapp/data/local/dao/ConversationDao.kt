package com.chatapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chatapp.data.local.entities.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE ownerId = :ownerId ORDER BY lastMessageTime DESC")
    fun getConversations(ownerId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE ownerId = :ownerId ORDER BY lastMessageTime DESC")
    suspend fun getConversationsOnce(ownerId: String): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id AND ownerId = :ownerId")
    fun observeConversationById(id: String, ownerId: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id AND ownerId = :ownerId")
    suspend fun getConversationById(id: String, ownerId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET isMember = :isMember WHERE id = :id AND ownerId = :ownerId")
    suspend fun updateMembershipStatus(id: String, ownerId: String, isMember: Boolean)

    @Query("DELETE FROM conversations WHERE id = :id AND ownerId = :ownerId")
    suspend fun deleteConversation(id: String, ownerId: String)

    @Query("DELETE FROM conversations WHERE ownerId = :ownerId")
    suspend fun clearAll(ownerId: String)
}
