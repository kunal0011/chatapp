package com.chatapp.domain.repository

import com.chatapp.domain.model.ChatMessage
import com.chatapp.domain.model.Conversation
import com.chatapp.domain.model.GroupMember
import com.chatapp.domain.model.MessageRecipientStatus
import com.chatapp.domain.model.SearchResults
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class StatusUpdate(
    val conversationId: String,
    val userId: String,
    val lastReadId: String?,
    val lastReadTime: Instant?
)

interface ChatRepository {
    suspend fun connectRealtime()
    suspend fun disconnectRealtime()
    suspend fun joinConversation(conversationId: String)
    suspend fun markAsRead(conversationId: String)
    suspend fun updateReadWatermark(conversationId: String, userId: String, messageId: String?, lastReadTime: Instant?)
    suspend fun sendTypingStatus(conversationId: String, isTyping: Boolean)

    fun observeConversations(): Flow<List<Conversation>>
    fun observeConversation(id: String): Flow<Conversation?>
    fun observeMembers(conversationId: String): Flow<List<GroupMember>>
    suspend fun fetchConversations()
    suspend fun getConversations(): List<Conversation>
    suspend fun getConversation(id: String): Conversation?
    suspend fun createGroup(name: String, memberIds: List<String>): Conversation
    suspend fun updateGroupMetadata(conversationId: String, name: String?, description: String?): Conversation
    suspend fun addGroupMembers(conversationId: String, memberIds: List<String>)
    suspend fun getGroupMembers(conversationId: String): List<GroupMember>
    suspend fun updateMemberRole(conversationId: String, userId: String, role: String)
    suspend fun removeMember(conversationId: String, userId: String)
    suspend fun isMember(conversationId: String): Boolean
    suspend fun leaveGroup(conversationId: String)
    suspend fun deleteConversationLocally(conversationId: String)
    suspend fun muteConversation(conversationId: String)
    suspend fun unmuteConversation(conversationId: String)

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun fetchHistory(conversationId: String, cursor: String? = null, limit: Int = 40): String?
    suspend fun getHistory(conversationId: String, cursor: String? = null, limit: Int = 30): Pair<List<ChatMessage>, String?>
    suspend fun searchMessages(query: String): SearchResults

    suspend fun sendText(conversationId: String, content: String, clientTempId: String, parentId: String? = null)
    suspend fun editMessage(messageId: String, content: String)
    suspend fun unsendMessage(messageId: String)
    suspend fun sendReaction(messageId: String, emoji: String)
    suspend fun deleteMessage(messageId: String)
    suspend fun getMessageInfo(messageId: String): List<MessageRecipientStatus>

    fun observeIncomingMessages(): Flow<ChatMessage>
    fun observeReadEvents(): Flow<Pair<String, String>> // conversationId, readerId
    fun observeStatusUpdates(): Flow<StatusUpdate>
    fun observeTypingEvents(): Flow<Triple<String, String, Boolean>> // convId, userId, isTyping
    fun observeConnectionState(): Flow<Boolean>
    suspend fun currentUserIdOrNull(): String?
}
