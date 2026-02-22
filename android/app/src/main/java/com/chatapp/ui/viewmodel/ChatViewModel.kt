package com.chatapp.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.domain.model.ChatMessage
import com.chatapp.domain.repository.ChatRepository
import com.chatapp.domain.repository.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val conversationId: String = "",
    val contactName: String = "",
    val otherUserId: String? = null,
    val currentUserId: String? = null,
    val isConnected: Boolean = false,
    val isOtherTyping: Boolean = false,
    val isGroup: Boolean = false,
    val memberCount: Int = 0,
    val isMember: Boolean = true,
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val sending: Boolean = false,
    val replyingTo: ChatMessage? = null,
    val editingMessage: ChatMessage? = null,
    val messages: List<ChatMessage> = emptyList(),
    val nextCursor: String? = null,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val contactsRepository: ContactsRepository
) : ViewModel() {
    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val contactName: String = Uri.decode(checkNotNull(savedStateHandle["contactName"]))

    private val _state = MutableStateFlow(
        ChatUiState(
            conversationId = conversationId,
            contactName = contactName
        )
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var observerJob: Job? = null
    private var readObserverJob: Job? = null
    private var typingObserverJob: Job? = null
    private var typingPublishJob: Job? = null

    init {
        initialize()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                chatRepository.fetchHistory(conversationId = conversationId, limit = 40)
            }
        }
    }

    private fun initialize() {
        viewModelScope.launch {
            val currentUserId = chatRepository.currentUserIdOrNull()
            _state.update { it.copy(currentUserId = currentUserId) }

            // 1. Observe conversation details (for membership status and name updates)
            launch {
                chatRepository.observeConversation(conversationId).collectLatest { conv ->
                    android.util.Log.d("ChatViewModel", "Received conversation update for $conversationId: name=${conv?.name}")
                    _state.update {
                        it.copy(
                            contactName = conv?.name ?: it.contactName,
                            isGroup = conv?.type == com.chatapp.domain.model.ConversationType.GROUP,
                            memberCount = conv?.memberCount ?: 0,
                            isMember = conv?.isMember ?: true
                        )
                    }
                }
            }

            // 2. Observe local messages
            launch {
                chatRepository.observeMessages(conversationId).collectLatest { list ->
                    val otherId = list.firstOrNull { it.senderId != currentUserId }?.senderId
                    _state.update {
                        it.copy(
                            messages = list,
                            loading = false, // Stop loading immediately when DB observation starts
                            otherUserId = otherId
                        )
                    }
                }
            }

            // 3. Perform network sync and start observers
            runCatching {
                chatRepository.connectRealtime()
                chatRepository.joinConversation(conversationId)
                chatRepository.markAsRead(conversationId)

                // Start socket event listeners
                startObserver()
                startReadObserver()
                startTypingObserver()
                startConnectionObserver()

                // Initial history fetch
                val cursor = chatRepository.fetchHistory(conversationId = conversationId, limit = 40)
                _state.update { it.copy(nextCursor = cursor, isConnected = true) }
            }.onFailure { throwable ->
                _state.update { it.copy(error = throwable.message, loading = false) }
            }
        }
    }

    private fun startObserver() {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            chatRepository.observeIncomingMessages().collect { incoming ->
                if (incoming.conversationId == conversationId) {
                    if (incoming.senderId != _state.value.currentUserId) {
                        chatRepository.markAsRead(conversationId)
                    }
                }
            }
        }
    }

    private fun startReadObserver() {
        readObserverJob?.cancel()
        readObserverJob = viewModelScope.launch {
            chatRepository.observeReadEvents().collect { (convId, readerId) ->
                if (convId == conversationId && readerId != _state.value.currentUserId) {
                    // Room handles UI updates
                }
            }
        }
    }

    private fun startTypingObserver() {
        typingObserverJob?.cancel()
        typingObserverJob = viewModelScope.launch {
            chatRepository.observeTypingEvents().collect { (convId, userId, isTyping) ->
                if (convId == conversationId && userId != _state.value.currentUserId) {
                    _state.update { it.copy(isOtherTyping = isTyping) }
                }
            }
        }
    }

    private fun startConnectionObserver() {
        viewModelScope.launch {
            chatRepository.observeConnectionState().collect { connected ->
                _state.update { it.copy(isConnected = connected) }
            }
        }
    }

    private val _messageInput = MutableStateFlow("")
    val messageInput: StateFlow<String> = _messageInput.asStateFlow()

    fun onMessageInputChangeNew(value: String) {
        val oldVal = _messageInput.value
        _messageInput.value = value.take(4000)

        if (value.isNotEmpty() && oldVal.isEmpty()) {
            publishTyping(true)
        } else if (value.isEmpty() && oldVal.isNotEmpty()) {
            publishTyping(false)
        }
    }

    private fun publishTyping(isTyping: Boolean) {
        typingPublishJob?.cancel()
        typingPublishJob = viewModelScope.launch {
            chatRepository.sendTypingStatus(conversationId, isTyping)
            if (isTyping) {
                delay(3000)
                chatRepository.sendTypingStatus(conversationId, false)
            }
        }
    }

    fun onReplyClick(message: ChatMessage) {
        _state.update { it.copy(replyingTo = message, editingMessage = null) }
    }

    fun cancelReply() {
        _state.update { it.copy(replyingTo = null) }
    }

    fun onEditClick(message: ChatMessage) {
        _state.update { it.copy(editingMessage = message, replyingTo = null) }
        _messageInput.value = message.content
    }

    fun cancelEdit() {
        _state.update { it.copy(editingMessage = null) }
        _messageInput.value = ""
    }

    fun sendReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            chatRepository.sendReaction(messageId, emoji)
        }
    }

    fun sendMessage() {
        val text = _messageInput.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            publishTyping(false)
            val editingId = _state.value.editingMessage?.id
            val parentId = _state.value.replyingTo?.id

            _state.update { it.copy(sending = true, replyingTo = null, editingMessage = null) }
            val clientTempId = "local-${System.currentTimeMillis()}"

            runCatching {
                if (editingId != null) {
                    chatRepository.editMessage(editingId, text)
                } else {
                    chatRepository.sendText(
                        conversationId = conversationId,
                        content = text,
                        clientTempId = clientTempId,
                        parentId = parentId
                    )
                }
            }.onSuccess {
                _messageInput.value = ""
                _state.update { it.copy(sending = false) }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        sending = false,
                        error = throwable.message ?: "Failed to send message"
                    )
                }
            }
        }
    }

    fun unsendMessage(messageId: String) {
        viewModelScope.launch {
            runCatching { chatRepository.unsendMessage(messageId) }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    fun blockUser() {
        val otherId = _state.value.otherUserId ?: return
        viewModelScope.launch {
            runCatching { contactsRepository.blockUser(otherId) }
                .onSuccess { initialize() }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    fun loadOlderMessages() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loadingOlder) return

        viewModelScope.launch {
            _state.update { it.copy(loadingOlder = true) }
            runCatching {
                val next = chatRepository.fetchHistory(
                    conversationId = conversationId,
                    cursor = cursor,
                    limit = 30
                )
                _state.update { it.copy(loadingOlder = false, nextCursor = next) }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        loadingOlder = false,
                        error = throwable.message ?: "Failed to load older messages"
                    )
                }
            }
        }
    }
}
