package com.chatapp.data.repository

import com.chatapp.data.api.ConversationsApi
import com.chatapp.data.dto.*
import com.chatapp.data.local.dao.ConversationDao
import com.chatapp.data.local.dao.MessageDao
import com.chatapp.data.local.entities.toDomain as toDomainEntity
import com.chatapp.data.local.entities.toEntity
import com.chatapp.data.network.NetworkErrorMapper
import com.chatapp.data.network.SessionGateway
import com.chatapp.data.socket.ChatSocketClient
import com.chatapp.domain.model.ChatMessage
import com.chatapp.domain.model.Conversation
import com.chatapp.domain.model.MessageStatus
import com.chatapp.domain.repository.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val conversationsApi: ConversationsApi,
    private val socketClient: ChatSocketClient,
    private val sessionGateway: SessionGateway,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ChatRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        repositoryScope.launch {
            socketClient.observeAcks().collectLatest { ack ->
                messageDao.deleteMessageById(ack.clientTempId)
            }
        }
    }

    override suspend fun connectRealtime() {
        runCatching {
            val token = sessionGateway.requireSession().accessToken
            socketClient.connect(token)
        }
    }

    override suspend fun disconnectRealtime() = socketClient.disconnect()
    override suspend fun joinConversation(conversationId: String) = socketClient.joinConversation(conversationId)
    override suspend fun markAsRead(conversationId: String) = socketClient.markAsRead(conversationId)
    override suspend fun sendTypingStatus(conversationId: String, isTyping: Boolean) = socketClient.sendTypingStatus(conversationId, isTyping)

    override fun observeConversations(): Flow<List<Conversation>> {
        return conversationDao.getConversations().map { entities ->
            entities.map { it.toDomainEntity() }
        }
    }

    override suspend fun fetchConversations() {
        runCatching {
            val userId = sessionGateway.requireSession().userId
            val response = conversationsApi.listConversations()
            val networkConversations = response.conversations.map { apiConv: ApiConversation ->
                apiConv.toDomain(userId)
            }
            conversationDao.insertConversations(networkConversations.map { it.toEntity() })
        }
    }

    override suspend fun getConversations(): List<Conversation> {
        fetchConversations()
        return emptyList()
    }

    override suspend fun muteConversation(conversationId: String) {
        runCatching { conversationsApi.muteConversation(conversationId) }
    }

    override suspend fun unmuteConversation(conversationId: String) {
        runCatching { conversationsApi.unmuteConversation(conversationId) }
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessages(conversationId).map { entities ->
            entities.map { it.toDomainEntity() }
        }
    }

    override suspend fun fetchHistory(conversationId: String, cursor: String?, limit: Int): String? {
        return runCatching {
            val response = conversationsApi.getMessages(conversationId, cursor, limit)
            val domainMessages = response.messages.map { it.toDomain() }
            messageDao.insertMessages(domainMessages.map { it.toEntity() })
            response.nextCursor
        }.getOrNull()
    }

    override suspend fun getHistory(conversationId: String, cursor: String?, limit: Int): Pair<List<ChatMessage>, String?> {
        val next = fetchHistory(conversationId, cursor, limit)
        return emptyList<ChatMessage>() to next
    }

    override suspend fun searchMessages(query: String): List<ChatMessage> = runCatching {
        conversationsApi.searchMessages(query).messages.map { it.toDomain() }
    }.getOrElse { emptyList() }

    override suspend fun sendText(conversationId: String, content: String, clientTempId: String, parentId: String?) {
        val session = sessionGateway.requireSession()
        
        val localMsg = ChatMessage(
            id = clientTempId,
            conversationId = conversationId,
            senderId = session.userId,
            senderName = session.displayName,
            content = content,
            createdAt = Instant.now(),
            status = MessageStatus.SENT,
            parentId = parentId
        )
        messageDao.insertMessage(localMsg.toEntity())

        socketClient.sendMessage(conversationId, content, clientTempId, parentId)
    }

    override suspend fun editMessage(messageId: String, content: String) {
        socketClient.editMessage(messageId, content)
    }

    override suspend fun unsendMessage(messageId: String) {
        socketClient.unsendMessage(messageId)
    }

    override suspend fun sendReaction(messageId: String, emoji: String) {
        socketClient.sendReaction(messageId, emoji)
    }

    override suspend fun deleteMessage(messageId: String) {
        runCatching { conversationsApi.deleteMessage(messageId) }
    }

    override fun observeIncomingMessages(): Flow<ChatMessage> {
        return socketClient.observeMessages().map { apiMsg ->
            val domain = apiMsg.toDomain()
            messageDao.insertMessage(domain.toEntity())
            domain
        }
    }

    override fun observeReadEvents(): Flow<Pair<String, String>> = socketClient.observeReadEvents().map { it.conversationId to it.readerId }
    override fun observeTypingEvents(): Flow<Triple<String, String, Boolean>> = socketClient.observeTypingEvents().map { Triple(it.conversationId, it.userId, it.isTyping) }
    override fun observeConnectionState(): Flow<Boolean> = socketClient.observeConnection()
    override suspend fun currentUserIdOrNull(): String? = sessionGateway.currentUserIdOrNull()
}
