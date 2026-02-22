package com.chatapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chatapp.data.local.entities.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM conversation_members WHERE conversationId = :conversationId AND ownerId = :ownerId")
    fun getMembersForConversation(conversationId: String, ownerId: String): Flow<List<MemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<MemberEntity>)

    @Query("SELECT * FROM conversation_members WHERE conversationId = :conversationId AND ownerId = :ownerId")
    fun observeMembers(conversationId: String, ownerId: String): Flow<List<MemberEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM conversation_members WHERE conversationId = :conversationId AND userId = :userId AND ownerId = :ownerId)")
    suspend fun isMember(conversationId: String, userId: String, ownerId: String): Boolean

    @Query("UPDATE conversation_members SET lastReadMessageId = :messageId, lastReadTime = :time WHERE conversationId = :conversationId AND userId = :userId AND ownerId = :ownerId")
    suspend fun updateReadWatermarkWithTime(conversationId: String, userId: String, ownerId: String, messageId: String?, time: Long?)

    @Query("DELETE FROM conversation_members WHERE conversationId = :conversationId AND ownerId = :ownerId")
    suspend fun clearMembersForConversation(conversationId: String, ownerId: String)
}
