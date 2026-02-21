package com.chatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.domain.model.ChatMessage
import com.chatapp.domain.model.Conversation
import com.chatapp.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val loading: Boolean = true,
    val conversations: List<Conversation> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<ChatMessage> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ConversationsUiState())
    val state: StateFlow<ConversationsUiState> = _state.asStateFlow()

    private val _navigateToChat = MutableSharedFlow<NavigateToChat>()
    val navigateToChat: SharedFlow<NavigateToChat> = _navigateToChat.asSharedFlow()

    init {
        viewModelScope.launch {
            chatRepository.observeConversations().collectLatest { list ->
                // Ensure loading is set to false when data arrives from local storage
                _state.value = _state.value.copy(conversations = list, loading = false)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Only set loading to true if we don't have any cached data to show
            _state.value = _state.value.copy(loading = _state.value.conversations.isEmpty(), error = null)
            runCatching { chatRepository.fetchConversations() }
                .onSuccess {
                    _state.value = _state.value.copy(loading = false)
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = throwable.message ?: "Failed to load chats"
                    )
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        if (query.length >= 2) {
            performSearch(query)
        } else {
            _state.value = _state.value.copy(searchResults = emptyList(), isSearching = false)
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSearching = true)
            val results = chatRepository.searchMessages(query)
            _state.value = _state.value.copy(searchResults = results, isSearching = false)
        }
    }

    fun toggleMute(conversation: Conversation) {
        viewModelScope.launch {
            if (conversation.isMuted) {
                chatRepository.unmuteConversation(conversation.id)
            } else {
                chatRepository.muteConversation(conversation.id)
            }
        }
    }

    fun onConversationClick(conversation: Conversation) {
        viewModelScope.launch {
            _navigateToChat.emit(
                NavigateToChat(
                    conversationId = conversation.id,
                    contactName = conversation.contactName
                )
            )
        }
    }
    
    fun onSearchResultClick(message: ChatMessage) {
        viewModelScope.launch {
            val conv = _state.value.conversations.find { it.id == message.conversationId }
            _navigateToChat.emit(
                NavigateToChat(
                    conversationId = message.conversationId,
                    contactName = conv?.contactName ?: "Chat"
                )
            )
        }
    }
}
