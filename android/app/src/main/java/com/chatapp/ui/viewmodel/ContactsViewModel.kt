package com.chatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.domain.model.User
import com.chatapp.domain.repository.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val loading: Boolean = true,
    val syncing: Boolean = false,
    val contacts: List<User> = emptyList(),
    val query: String = "",
    val openingConversationForUserId: String? = null,
    val error: String? = null
)

data class NavigateToChat(
    val conversationId: String,
    val contactName: String,
    val otherUserId: String? = null
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    private val _navigateToChat = MutableSharedFlow<NavigateToChat>()
    val navigateToChat: SharedFlow<NavigateToChat> = _navigateToChat.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { contactsRepository.getContacts() }
                .onSuccess { contacts ->
                    _state.value = _state.value.copy(
                        loading = false,
                        contacts = contacts,
                        openingConversationForUserId = null
                    )
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        loading = false,
                        openingConversationForUserId = null,
                        error = throwable.message ?: "Failed to load contacts"
                    )
                }
        }
    }

    fun syncContacts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(syncing = true, error = null)
            runCatching { contactsRepository.syncContactsFromDevice() }
                .onSuccess { contacts ->
                    _state.value = _state.value.copy(
                        syncing = false,
                        contacts = contacts
                    )
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        syncing = false,
                        error = throwable.message ?: "Sync failed"
                    )
                }
        }
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value, error = null)
    }

    fun startConversation(user: User) {
        viewModelScope.launch {
            _state.value = _state.value.copy(openingConversationForUserId = user.id, error = null)
            runCatching {
                contactsRepository.startDirectConversation(user.id)
            }.onSuccess { conversation ->
                _state.value = _state.value.copy(openingConversationForUserId = null)
                _navigateToChat.emit(
                    NavigateToChat(
                        conversationId = conversation.id,
                        contactName = user.displayName
                    )
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    openingConversationForUserId = null,
                    error = throwable.message ?: "Failed to start conversation"
                )
            }
        }
    }
}
