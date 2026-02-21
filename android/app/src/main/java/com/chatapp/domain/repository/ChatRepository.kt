package com.chatapp.domain.repository

import com.chatapp.domain.model.ChatMessage
import com.chatapp.domain.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun connectRealtime()
    suspend fun disconnectRealtime()
    suspend fun joinConversation(conversationId: String)
    suspend fun markAsRead(conversationId: String)
    suspend fun sendTypingStatus(conversationId: String, isTyping: Boolean)
    
    fun observeConversations(): Flow<List<Conversation>>
    suspend fun fetchConversations()
    suspend fun getConversations(): List<Conversation>
    suspend fun muteConversation(conversationId: String)
    suspend fun unmuteConversation(conversationId: String)
    
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun fetchHistory(conversationId: String, cursor: String? = null, limit: Int = 40): String?
    suspend fun getHistory(conversationId: String, cursor: String? = null, limit: Int = 30): Pair<List<ChatMessage>, String?>
    suspend fun searchMessages(query: String): List<ChatMessage>
    
    suspend fun sendText(conversationId: String, content: String, clientTempId: String, parentId: String? = null)
    suspend fun editMessage(messageId: String, content: String)
    suspend fun unsendMessage(messageId: String)
    suspend fun sendReaction(messageId: String, emoji: String)
    suspend fun deleteMessage(messageId: String)
    
    fun observeIncomingMessages(): Flow<ChatMessage>
    fun observeReadEvents(): Flow<Pair<String, String>> // conversationId, readerId
    fun observeTypingEvents(): Flow<Triple<String, String, Boolean>> // convId, userId, isTyping
    fun observeConnectionState(): Flow<Boolean>
    suspend fun currentUserIdOrNull(): String?
}
