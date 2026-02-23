package com.chatapp.data.repository

import com.chatapp.data.api.ConversationsApi
import com.chatapp.data.dto.AddMembersRequest
import com.chatapp.data.dto.GroupConversationRequest
import com.chatapp.data.dto.UpdateGroupRequest
import com.chatapp.data.local.dao.ConversationDao
import com.chatapp.data.local.dao.MemberDao
import com.chatapp.data.local.dao.MessageDao
import com.chatapp.data.local.entities.MemberEntity
import com.chatapp.data.local.entities.MessageEntity
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
import com.chatapp.core.crypto.E2eeCryptoManager
import com.chatapp.core.crypto.EncryptedPayload
import com.chatapp.core.crypto.KeyChangeDetector
import com.chatapp.core.crypto.KeyChangeEvent
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

import com.chatapp.core.crypto.GroupE2eeManager

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val conversationsApi: ConversationsApi,
    private val socketClient: ChatSocketClient,
    private val sessionGateway: SessionGateway,
    private val sessionStore: SessionStore,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val memberDao: MemberDao,
    private val cryptoManager: E2eeCryptoManager,  // 1:1 E2EE crypto facade
    private val groupE2eeManager: GroupE2eeManager, // Group E2EE SenderKey protocol
    private val keyChangeDetector: KeyChangeDetector  // Key Change Alert detector
) : ChatRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        // Global ACK observer — update the optimistic message's ID from clientTempId to serverId.
        // The optimistic message (with plaintext content) stays in Room — we just fix its ID & metadata.
        repositoryScope.launch {
            socketClient.observeAcks().collectLatest { ack ->
                val userId = sessionGateway.currentUserIdOrNull() ?: return@collectLatest
                // Replace the temp message with the server-confirmed version.
                // Content stays as plaintext since we inserted it in sendText().
                val existing = messageDao.getMessageById(ack.clientTempId, userId) ?: return@collectLatest
                val updated = existing.copy(
                    id = ack.messageId,
                    status = ack.status,
                    createdAt = try { java.time.Instant.parse(ack.createdAt).toEpochMilli() } catch (_: Exception) { existing.createdAt }
                )
                messageDao.deleteMessageById(ack.clientTempId, userId)
                messageDao.insertMessage(updated)
            }
        }

        // Global Message observer — ONLY process messages from OTHER users.
        // Own messages are already in Room (optimistic insert in sendText + ACK ID update above).
        // Attempting to re-insert our own echo would overwrite plaintext with ciphertext.
        repositoryScope.launch {
            socketClient.observeMessages().collect { apiMsg ->
                val userId = sessionGateway.currentUserIdOrNull() ?: return@collect
                // SKIP own messages — they're already in Room with plaintext
                if (apiMsg.senderId == userId) return@collect
                // Decrypt incoming encrypted messages from other users
                val finalMessage = decryptApiMessage(apiMsg, userId)
                val domain = finalMessage.toDomainDto()
                messageDao.insertMessage(domain.toEntity(userId))
            }
        }

        // Global Member Added observer — distribute own SenderKey to the new member
        repositoryScope.launch {
            socketClient.observeMemberAdded().collect { event ->
                val myUserId = sessionGateway.currentUserIdOrNull() ?: return@collect
                // Ignore if we are the ones added (we can't distribute a key we don't have yet)
                if (event.userId != myUserId) {
                    runCatching { groupE2eeManager.ensureSenderKeyDistributed(event.conversationId, myUserId) }
                        .onFailure { e -> android.util.Log.e("ChatRepository", "Failed to distribute SenderKey on join", e) }
                }
            }
        }

        // Global Member Removed observer — mandatory SenderKey rotation to revoke access
        repositoryScope.launch {
            socketClient.observeMemberRemoved().collect { event ->
                val myUserId = sessionGateway.currentUserIdOrNull() ?: return@collect
                // Ignore if we are the ones removed
                if (event.userId != myUserId) {
                    runCatching { groupE2eeManager.rotateSenderKey(event.conversationId, myUserId) }
                        .onFailure { e -> android.util.Log.e("ChatRepository", "Failed to rotate SenderKey on leave", e) }
                }
            }
        }
    }

    override suspend fun connectRealtime() {
        runCatching {
            val token = sessionGateway.requireSession().accessToken
            // Upload E2EE public keys on every connect (no-op if already uploaded)
            runCatching { cryptoManager.ensureKeysUploaded() }
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
        val networkConversations = response.conversations.map { apiConv ->
            var modifiedConv = apiConv
            val lastMsg = apiConv.messages?.firstOrNull()
            
            if (lastMsg != null && lastMsg.isEncrypted) {
                try {
                    val localExisting = messageDao.getMessageById(lastMsg.id, userId)
                    val decryptedMsg = if (lastMsg.senderId == userId) {
                        // Our own encrypted message — we can't decrypt our own outbound ciphertext.
                        // We must rely on our immediate local Room insert from `sendText` or fallback.
                        if (localExisting != null) {
                            lastMsg.copy(content = localExisting.content, isEncrypted = false)
                        } else {
                            lastMsg.copy(content = "[Encrypted message]", isEncrypted = false)
                        }
                    } else {
                        // New encrypted message from someone else. We MUST run decrypt to advance the ratchet!
                        val cryptoPlaintext = decryptApiMessage(lastMsg, userId)
                        // If we already had it in Room, maybe prefer Room's existing text.
                        // But we return the successfully decrypted object.
                        cryptoPlaintext
                    }
                    modifiedConv = apiConv.copy(messages = listOf(decryptedMsg))
                } catch (e: Exception) {
                    android.util.Log.e("ChatRepository", "Failed to decrypt preview message for ${apiConv.id}", e)
                    val fallbackMsg = lastMsg.copy(content = "[Encrypted message]", isEncrypted = false)
                    modifiedConv = apiConv.copy(messages = listOf(fallbackMsg))
                }
            }
            modifiedConv.toDomainDto(userId)
        }

        // Check for identity key changes in DIRECT conversations
        response.conversations.forEach { apiConv ->
            if (apiConv.type == "DIRECT") {
                val otherMember = apiConv.members?.firstOrNull { m -> m.user.id != userId }
                if (otherMember != null) {
                    // Non-blocking — failures are logged inside KeyChangeDetector
                    runCatching {
                        keyChangeDetector.checkForKeyChange(
                            contactUserId = otherMember.user.id,
                            contactDisplayName = otherMember.user.displayName
                        )
                    }
                }
            }
        }

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

            // Process each message — skip encrypted ones already in Room to avoid
            // overwriting the decrypted plaintext with the server's ciphertext.
            val toInsert = mutableListOf<MessageEntity>()
            for (apiMsg in response.messages) {
                if (apiMsg.isEncrypted) {
                    // Check if this message is already in Room (decrypted by socket observer)
                    val existing = messageDao.getMessageById(apiMsg.id, userId)
                    if (existing != null) {
                        // Already in Room — skip (preserves the decrypted plaintext)
                        continue
                    }
                    // Not in Room — try to decrypt (e.g., received while we were offline)
                    val decrypted = if (apiMsg.senderId == userId) {
                        // Own encrypted message — try senderPlaintext (account-key encrypted)
                        val selfDecrypted = apiMsg.senderPlaintext?.let {
                            runCatching { cryptoManager.decryptForSelf(it) }.getOrNull()
                        }
                        apiMsg.copy(
                            content = selfDecrypted ?: "[Encrypted message]",
                            isEncrypted = false
                        )
                    } else {
                        decryptApiMessage(apiMsg, userId)
                    }
                    toInsert.add(decrypted.toDomainDto().toEntity(userId))
                } else {
                    toInsert.add(apiMsg.toDomainDto().toEntity(userId))
                }
            }

            if (toInsert.isNotEmpty()) {
                messageDao.insertMessages(toInsert)
            }
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

            // Server returns only unencrypted messages; supplement with local Room
            // search which has decrypted plaintext for E2EE messages.
            val localResults = try {
                messageDao.searchMessages(query, userId).map { it.toDomainMessage() }
            } catch (e: Exception) {
                emptyList()
            }

            val serverMessages = response.messages.map { it.toDomainDto() }

            // Merge, deduplicate by ID, sort newest first
            val mergedMessages = (serverMessages + localResults)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }

            SearchResults(
                messages = mergedMessages,
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

        // Store locally with plaintext immediately for optimistic UI
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
            conversationDao.updateLastMessage(conversationId, session.userId, content, localMsg.createdAt.toEpochMilli())
        }

        // Encrypt before sending over the network — server only ever sees ciphertext
        try {
            var conversation = conversationDao.getConversationById(conversationId, session.userId)
            if (conversation == null) {
                android.util.Log.w("ChatRepository", "Conversation missing locally. Fetching before encryption...")
                runCatching {
                    getConversation(conversationId)
                    conversation = conversationDao.getConversationById(conversationId, session.userId)
                }.onFailure { e ->
                    android.util.Log.e("ChatRepository", "Failed to fetch missing conversation $conversationId", e)
                }
            }

            if (conversation?.type == "GROUP") {
                // Group E2EE: SenderKey encrypt
                groupE2eeManager.ensureSenderKeyDistributed(conversationId, session.userId)
                val senderKeyMsg = groupE2eeManager.encryptGroupMessage(conversationId, session.userId, content)
                val encryptedJson = com.google.gson.Gson().toJson(senderKeyMsg)
                val selfPlaintext = cryptoManager.encryptForSelf(content)
                socketClient.sendMessage(
                    conversationId = conversationId,
                    content = encryptedJson,
                    clientTempId = clientTempId,
                    parentId = parentId,
                    isEncrypted = true,
                    // senderPlaintext goes here for own history recovery
                    senderPlaintext = selfPlaintext
                )
            } else {
                // 1:1 E2EE: X3DH + Double Ratchet
                val encryptedPayload = cryptoManager.encryptWithHeader(conversationId, getRecipientId(conversationId, session.userId), content)
                val encryptedJson = com.google.gson.Gson().toJson(encryptedPayload)
                // Encrypt plaintext with account key for own history recovery
                val selfPlaintext = cryptoManager.encryptForSelf(content)
                socketClient.sendMessage(
                    conversationId = conversationId,
                    content = encryptedJson,
                    clientTempId = clientTempId,
                    parentId = parentId,
                    isEncrypted = true,
                    // ephemeralKey is already inside encryptedJson — no need to send separately
                    senderPlaintext = selfPlaintext
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "E2EE FAILED for chat $conversationId. NOT sending plaintext: ${e.message}")
            throw e
        }
    }


    /**
     * Decrypt an incoming ApiMessage if it's encrypted.
     * Returns the message with plaintext content (or original if not encrypted / decryption fails).
     */
    private suspend fun decryptApiMessage(apiMsg: com.chatapp.data.dto.ApiMessage, myUserId: String): com.chatapp.data.dto.ApiMessage {
        if (!apiMsg.isEncrypted) return apiMsg
        return try {
            val gson = com.google.gson.Gson()
            
            // Heuristic to detect Group E2EE (SenderKeyMessage) vs 1:1 E2EE (EncryptedPayload)
            if (apiMsg.content.contains("\"signature\"") && apiMsg.content.contains("\"iteration\"")) {
                val skMsg = gson.fromJson(apiMsg.content, com.chatapp.core.crypto.SenderKeyMessage::class.java)
                groupE2eeManager.ensureSenderKeyReceived(apiMsg.conversationId, skMsg.senderUserId, myUserId)
                val plaintext = groupE2eeManager.decryptGroupMessage(apiMsg.conversationId, skMsg.senderUserId, skMsg)
                apiMsg.copy(content = plaintext, isEncrypted = false)
            } else {
                val payload = gson.fromJson(apiMsg.content, com.chatapp.core.crypto.EncryptedPayload::class.java)
                val plaintext = cryptoManager.decrypt(apiMsg.conversationId, payload)
                apiMsg.copy(content = plaintext, isEncrypted = false)
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.javaClass.simpleName
            android.util.Log.e("ChatRepository", "E2EE decrypt failed for msg ${apiMsg.id} in ${apiMsg.conversationId}: $errorMsg")
            // Return placeholder instead of crashing/throwing
            apiMsg.copy(content = "[Decryption Error: State mismatch. Log out and in to reset keys.]", isEncrypted = false)
        }
    }

    /**
     * Get the other participant's userId for 1:1 conversations.
     * Used to look up their key bundle for E2EE session initiation.
     */
    private suspend fun getRecipientId(conversationId: String, myUserId: String): String {
        // Try local DB first
        val members = memberDao.getMembersOnce(conversationId, myUserId)
        val recipient = members.firstOrNull { it.userId != myUserId }?.userId
        if (recipient != null) return recipient

        // Local DB might be empty after a fresh login or data clear while socket is connecting.
        // Fall back to fetching members synchronously from the network.
        val networkMembers = getGroupMembers(conversationId)
        return networkMembers.firstOrNull { it.userId != myUserId }?.userId
            ?: error("Cannot determine recipient for conversation $conversationId even after network fetch")
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
        // Return a lightweight flow with ONLY metadata (conversationId, senderId) for markAsRead.
        // Content is intentionally blank — the actual decrypted content is saved to Room
        // by the global init{} observer. The UI reads from observeMessages() backed by Room.
        return socketClient.observeMessages().map { apiMsg ->
            ChatMessage(
                id = apiMsg.id,
                conversationId = apiMsg.conversationId,
                senderId = apiMsg.senderId,
                senderName = apiMsg.sender.displayName,
                content = "",  // Don't expose ciphertext — UI reads from Room
                createdAt = Instant.parse(apiMsg.createdAt),
                status = com.chatapp.domain.model.MessageStatus.SENT
            )
        }
    }


    override fun observeReadEvents(): Flow<Pair<String, String>> = socketClient.observeReadEvents().map { it.conversationId to it.readerId }
    override fun observeStatusUpdates(): Flow<StatusUpdate> = socketClient.observeStatusUpdates().map {
        StatusUpdate(it.conversationId, it.userId, it.lastReadMessageId, it.lastReadMessageTime?.let { ts -> Instant.parse(ts) })
    }
    override fun observeTypingEvents(): Flow<Triple<String, String, Boolean>> = socketClient.observeTypingEvents().map { Triple(it.conversationId, it.userId, it.isTyping) }
    override fun observeConnectionState(): Flow<Boolean> = socketClient.observeConnection()
    override suspend fun currentUserIdOrNull(): String? = sessionGateway.currentUserIdOrNull()
}
