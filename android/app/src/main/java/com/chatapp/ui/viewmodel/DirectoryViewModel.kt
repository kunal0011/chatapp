package com.chatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.domain.model.DeviceContact
import com.chatapp.domain.model.User
import com.chatapp.domain.repository.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DirectoryUiState(
    val loading: Boolean = true, // Start as true to avoid "No contacts" flicker
    val contacts: List<DeviceContact> = emptyList(),
    val query: String = "",
    val startingChatId: String? = null,
    val error: String? = null
)

@HiltViewModel
class DirectoryViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(DirectoryUiState())
    val state: StateFlow<DirectoryUiState> = _state.asStateFlow()

    private val _onChatStarted = MutableSharedFlow<NavigateToChat>()
    val onChatStarted: SharedFlow<NavigateToChat> = _onChatStarted.asSharedFlow()

    private val _onInviteContact = MutableSharedFlow<String>() // Phone number
    val onInviteContact: SharedFlow<String> = _onInviteContact.asSharedFlow()

    fun loadDeviceContacts() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { contactsRepository.getDeviceContacts() }
                .onSuccess { contacts ->
                    _state.value = _state.value.copy(loading = false, contacts = contacts)
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = throwable.message ?: "Failed to read contacts"
                    )
                }
        }
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value, error = null)
    }

    fun startChat(contact: DeviceContact) {
        val user = contact.registeredUser ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(startingChatId = user.id, error = null)
            runCatching {
                contactsRepository.addContact(user.id)
                contactsRepository.startDirectConversation(user.id)
            }.onSuccess { conversation ->
                _state.value = _state.value.copy(startingChatId = null)
                _onChatStarted.emit(
                    NavigateToChat(
                        conversationId = conversation.id,
                        contactName = user.displayName
                    )
                )
            }.onFailure { throwable ->
                _state.value = _state.value.copy(
                    startingChatId = null,
                    error = throwable.message ?: "Failed to start chat"
                )
            }
        }
    }

    fun inviteContact(contact: DeviceContact) {
        viewModelScope.launch {
            _onInviteContact.emit(contact.phone)
        }
    }
}
