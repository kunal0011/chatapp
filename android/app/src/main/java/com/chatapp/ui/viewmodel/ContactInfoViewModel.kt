package com.chatapp.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.domain.model.User
import com.chatapp.domain.repository.ChatRepository
import com.chatapp.domain.repository.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactInfoUiState(
    val user: User? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val isBlocked: Boolean = false,
    val isMuted: Boolean = false,
    val conversationId: String? = null
)

@HiltViewModel
class ContactInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactsRepository: ContactsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val userId: String = checkNotNull(savedStateHandle["userId"])

    private val _state = MutableStateFlow(ContactInfoUiState())
    val state: StateFlow<ContactInfoUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val user = contactsRepository.getUserById(userId)

                // Try to find a direct conversation with this user to get mute state
                val convs = chatRepository.getConversations()
                val directConv = convs.find { it.type == com.chatapp.domain.model.ConversationType.DIRECT && it.id.contains(userId) || it.name == user.displayName }
                // A better way is to observe conversations, but for now we look for existing ones

                _state.update { it.copy(
                    user = user,
                    loading = false,
                    isMuted = directConv?.isMuted ?: false,
                    conversationId = directConv?.id
                ) }
            }.onFailure { t ->
                _state.update { it.copy(error = t.message, loading = false) }
            }
        }
    }

    fun toggleMute() {
        val convId = _state.value.conversationId ?: return
        viewModelScope.launch {
            runCatching {
                if (_state.value.isMuted) {
                    chatRepository.unmuteConversation(convId)
                } else {
                    chatRepository.muteConversation(convId)
                }
                _state.update { it.copy(isMuted = !it.isMuted) }
            }.onFailure { t ->
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun toggleBlock() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching {
                if (_state.value.isBlocked) {
                    contactsRepository.unblockUser(userId)
                } else {
                    contactsRepository.blockUser(userId)
                }
                _state.update { it.copy(isBlocked = !it.isBlocked, loading = false) }
            }.onFailure { t ->
                _state.update { it.copy(error = t.message, loading = false) }
            }
        }
    }
}
