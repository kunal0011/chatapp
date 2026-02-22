package com.chatapp.data.repository

import com.chatapp.data.api.ConversationsApi
import com.chatapp.data.dto.AddMembersRequest
import com.chatapp.data.dto.GroupConversationRequest
import com.chatapp.data.dto.UpdateGroupRequest
import com.chatapp.data.local.dao.ConversationDao
import com.chatapp.data.local.dao.MemberDao
import com.chatapp.data.local.dao.MessageDao
import com.chatapp.data.local.entities.MemberEntity
import com.chatapp.data.local.entities.toEntity
import com.chatapp.data.network.SessionGateway
import com.chatapp.data.socket.ChatSocketClient
import com.chatapp.data.store.SessionStore
import com.chatapp.domain.model.ChatMessage
import com.chatapp.domain.model.Conversation
import com.chatapp.domain.model.GroupMember
import com.chatapp.domain.model.MessageRecipientStatus
import com.chatapp.domain.model.MessageStatus
import com.chatapp.domain.model.SearchResults
import com.chatapp.domain.repository.ChatRepository
import com.chatapp.domain.repository.StatusUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import com.chatapp.data.dto.toDomain as toDomainDto
import com.chatapp.data.dto.toDomain as toDomainUser
import com.chatapp.data.local.entities.toDomain as toDomainEntity
import com.chatapp.data.local.entities.toDomain as toDomainMessage

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val conversationsApi: ConversationsApi,
    private val socketClient: ChatSocketClient,
    private val sessionGateway: SessionGateway,
    private val sessionStore: SessionStore,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val memberDao: MemberDao
) : ChatRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        // Global ACK observer
        repositoryScope.launch {
            socketClient.observeAcks().collectLatest { ack ->
                val userId = sessionGateway.currentUserIdOrNull() ?: return@collectLatest
                messageDao.deleteMessageById(ack.clientTempId, userId)
            }
        }

        // Global Message observer - ensures messages are saved even if ChatScreen is closed
        repositoryScope.launch {
            socketClient.observeMessages().collect { apiMsg ->
                val userId = sessionGateway.currentUserIdOrNull() ?: return@collect
                val domain = apiMsg.toDomainDto()
                messageDao.insertMessage(domain.toEntity(userId))
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

    override suspend fun updateReadWatermark(conversationId: String, userId: String, messageId: String?, lastReadTime: Instant?) {
        val ownerId = sessionGateway.currentUserIdOrNull() ?: return
        memberDao.updateReadWatermarkWithTime(conversationId, userId, ownerId, messageId, lastReadTime?.toEpochMilli())
    }

    override suspend fun sendTypingStatus(conversationId: String, isTyping: Boolean) = socketClient.sendTypingStatus(conversationId, isTyping)

    override fun observeConversations(): Flow<List<Conversation>> {
        return sessionStore.sessionFlow.flatMapLatest { session ->
            val userId = session?.userId ?: return@flatMapLatest flowOf(emptyList())
            conversationDao.getConversations(userId).map { entities ->
                entities.map { it.toDomainEntity() }
            }
        }
    }

    override fun observeConversation(id: String): Flow<Conversation?> {
        return sessionStore.sessionFlow.flatMapLatest { session ->
            val userId = session?.userId ?: return@flatMapLatest flowOf(null)
            android.util.Log.d("ChatRepository", "Observing conversation $id for user $userId")
            conversationDao.observeConversationById(id, userId).map { entity ->
                android.util.Log.d("ChatRepository", "DB Flow emitted for $id: name=${entity?.name}")
                entity?.toDomainEntity()
            }
        }
    }

    override fun observeMembers(conversationId: String): Flow<List<GroupMember>> {
        return sessionStore.sessionFlow.flatMapLatest { session ->
            val userId = session?.userId ?: return@flatMapLatest flowOf(emptyList())
            memberDao.observeMembers(conversationId, userId).map { entities ->
                entities.map { entity ->
                    GroupMember(
                        userId = entity.userId,
                        displayName = entity.displayName,
                        phone = entity.phone,
                        role = entity.role,
                        joinedAt = Instant.now(), // Placeholder
                        lastReadTime = entity.lastReadTime?.let { Instant.ofEpochMilli(it) },
                        lastDeliveredTime = entity.lastDeliveredTime?.let { Instant.ofEpochMilli(it) },
                        lastSeen = entity.lastSeen?.let { Instant.ofEpochMilli(it) }
                    )
                }
            }
        }
    }

    override suspend fun fetchConversations() {
        val userId = sessionGateway.requireSession().userId
        val response = conversationsApi.listConversations()
        val networkConversations = response.conversations.map { it.toDomainDto(userId) }

        // Save conversations
        conversationDao.insertConversations(networkConversations.map { it.toEntity(userId) })

        // Save members
        response.conversations.forEach { apiConv ->
            apiConv.members?.let { apiMembers ->
                val memberEntities = apiMembers.map { m ->
                    MemberEntity(
                        conversationId = apiConv.id,
                        ownerId = userId,
                        userId = m.user.id,
                        displayName = m.user.displayName,
                        phone = m.user.phone,
                        role = m.role,
                        lastReadMessageId = m.lastReadMessageId,
                        lastReadTime = m.lastReadMessage?.createdAt?.let { Instant.parse(it).toEpochMilli() },
                        lastSeen = m.user.lastSeen?.let { Instant.parse(it).toEpochMilli() }
                    )
                }
                memberDao.insertMembers(memberEntities)
            }
        }
    }

    override suspend fun getConversations(): List<Conversation> {
        val userId = sessionGateway.requireSession().userId
        fetchConversations()
        return conversationDao.getConversationsOnce(userId).map { it.toDomainEntity() }
    }

    override suspend fun getConversation(id: String): Conversation? {
        val userId = sessionGateway.requireSession().userId
        return conversationDao.getConversationById(id, userId)?.toDomainEntity()
    }

    override suspend fun createGroup(name: String, memberIds: List<String>): Conversation {
        val userId = sessionGateway.requireSession().userId
        val response = conversationsApi.createGroup(GroupConversationRequest(name = name, memberIds = memberIds))
        val domain = response.conversation.toDomainDto(userId)
        conversationDao.insertConversation(domain.toEntity(userId))
        return domain
    }

    override suspend fun updateGroupMetadata(conversationId: String, name: String?, description: String?): Conversation {
        val userId = sessionGateway.requireSession().userId
        android.util.Log.d("ChatRepository", "Updating metadata for $conversationId. New name: $name")
        val response = conversationsApi.updateGroup(conversationId, UpdateGroupRequest(name = name, description = description))
        val domain = response.conversation.toDomainDto(userId)

        withContext(Dispatchers.IO) {
            android.util.Log.d("ChatRepository", "Inserting updated conversation to local DB: ${domain.name}")
            conversationDao.insertConversation(domain.toEntity(userId))
        }

        fetchConversations() // Background sync
        return domain
    }

    override suspend fun addGroupMembers(conversationId: String, memberIds: List<String>) {
        conversationsApi.addMembers(conversationId, AddMembersRequest(memberIds))
        getGroupMembers(conversationId)
    }

    override suspend fun getGroupMembers(conversationId: String): List<GroupMember> {
        val userId = sessionGateway.requireSession().userId
        val response = conversationsApi.getMembers(conversationId)
        val domainMembers = response.members.map { apiMember ->
            GroupMember(
                userId = apiMember.userId,
                displayName = apiMember.user.displayName,
                phone = apiMember.user.phone,
                role = apiMember.role,
                joinedAt = Instant.parse(apiMember.joinedAt),
                lastSeen = apiMember.user.lastSeen?.let { Instant.parse(it) }
            )
        }

        memberDao.clearMembersForConversation(conversationId, userId)
        memberDao.insertMembers(domainMembers.map { member ->
            MemberEntity(
                conversationId = conversationId,
                ownerId = userId,
                userId = member.userId,
                displayName = member.displayName,
                phone = member.phone,
                role = member.role,
                lastSeen = member.lastSeen?.toEpochMilli()
            )
        })

        return domainMembers
    }

    override suspend fun updateMemberRole(conversationId: String, userId: String, role: String) {
        conversationsApi.updateMemberRole(conversationId, userId, mapOf("role" to role))
        fetchConversations()
    }

    override suspend fun removeMember(conversationId: String, userId: String) {
        conversationsApi.removeMember(conversationId, userId)
        fetchConversations()
    }

    override suspend fun isMember(conversationId: String): Boolean {
        val userId = sessionGateway.requireSession().userId
        return memberDao.isMember(conversationId, userId, userId)
    }

    override suspend fun leaveGroup(conversationId: String) {
        val userId = sessionGateway.requireSession().userId
        conversationsApi.removeMember(conversationId, userId)
        conversationDao.updateMembershipStatus(conversationId, userId, false)
    }

    override suspend fun deleteConversationLocally(conversationId: String) {
        val userId = sessionGateway.requireSession().userId
        conversationDao.deleteConversation(conversationId, userId)
        messageDao.deleteMessagesForConversation(conversationId, userId)
    }

    override suspend fun muteConversation(conversationId: String) {
        runCatching { conversationsApi.muteConversation(conversationId) }
    }

    override suspend fun unmuteConversation(conversationId: String) {
        runCatching { conversationsApi.unmuteConversation(conversationId) }
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
        return sessionStore.sessionFlow.flatMapLatest { session ->
            val userId = session?.userId ?: return@flatMapLatest flowOf(emptyList())
            messageDao.getMessages(conversationId, userId).map { entities ->
                entities.map { it.toDomainMessage() }
            }
        }
    }

    override suspend fun fetchHistory(conversationId: String, cursor: String?, limit: Int): String? {
        return runCatching {
            val userId = sessionGateway.requireSession().userId
            val response = conversationsApi.getMessages(conversationId, cursor, limit)
            val domainMessages = response.messages.map { it.toDomainDto() }
            messageDao.insertMessages(domainMessages.map { it.toEntity(userId) })
            response.nextCursor
        }.getOrNull()
    }

    override suspend fun getHistory(conversationId: String, cursor: String?, limit: Int): Pair<List<ChatMessage>, String?> {
        val next = fetchHistory(conversationId, cursor, limit)
        return emptyList<ChatMessage>() to next
    }

    override suspend fun searchMessages(query: String): SearchResults {
        return try {
            val response = conversationsApi.searchMessages(query)
            val userId = sessionGateway.currentUserIdOrNull() ?: ""
            SearchResults(
                messages = response.messages.map { it.toDomainDto() },
                contacts = response.contacts.map { it.toDomainUser() },
                groups = response.groups.map { it.toDomainDto(userId) }
            )
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error during search mapping", e)
            SearchResults()
        }
    }

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
        withContext(Dispatchers.IO) {
            messageDao.insertMessage(localMsg.toEntity(session.userId))
        }

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

    override suspend fun getMessageInfo(messageId: String): List<MessageRecipientStatus> {
        val response = conversationsApi.getMessageInfo(messageId)
        return response.info.map { item ->
            MessageRecipientStatus(
                userId = item.user.id,
                displayName = item.user.displayName,
                phone = item.user.phone,
                status = item.status,
                timestamp = item.timestamp?.let { ts -> Instant.parse(ts) }
            )
        }
    }

    override fun observeIncomingMessages(): Flow<ChatMessage> {
        // Now handled by global listener in init{} for better reliability
        return socketClient.observeMessages().map { it.toDomainDto() }
    }

    override fun observeReadEvents(): Flow<Pair<String, String>> = socketClient.observeReadEvents().map { it.conversationId to it.readerId }
    override fun observeStatusUpdates(): Flow<StatusUpdate> = socketClient.observeStatusUpdates().map {
        StatusUpdate(it.conversationId, it.userId, it.lastReadMessageId, it.lastReadMessageTime?.let { ts -> Instant.parse(ts) })
    }
    override fun observeTypingEvents(): Flow<Triple<String, String, Boolean>> = socketClient.observeTypingEvents().map { Triple(it.conversationId, it.userId, it.isTyping) }
    override fun observeConnectionState(): Flow<Boolean> = socketClient.observeConnection()
    override suspend fun currentUserIdOrNull(): String? = sessionGateway.currentUserIdOrNull()
}
