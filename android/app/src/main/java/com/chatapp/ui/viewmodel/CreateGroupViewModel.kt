package com.chatapp.ui.viewmodel

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

data class CreateGroupUiState(
    val groupName: String = "",
    val loading: Boolean = false,
    val contacts: List<User> = emptyList(),
    val selectedUserIds: Set<String> = emptySet(),
    val createdConversationId: String? = null,
    val error: String? = null
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateGroupUiState())
    val state: StateFlow<CreateGroupUiState> = _state.asStateFlow()

    init {
        loadContacts()
    }

    private fun loadContacts() {
        viewModelScope.launch {
            runCatching { contactsRepository.getDirectory() }
                .onSuccess { list -> _state.update { it.copy(contacts = list) } }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    fun onGroupNameChange(name: String) {
        _state.update { it.copy(groupName = name) }
    }

    fun toggleUserSelection(userId: String) {
        _state.update { current ->
            val newSelection = if (current.selectedUserIds.contains(userId)) {
                current.selectedUserIds - userId
            } else {
                current.selectedUserIds + userId
            }
            current.copy(selectedUserIds = newSelection)
        }
    }

    fun createGroup() {
        val name = _state.value.groupName.trim()
        val members = _state.value.selectedUserIds.toList()

        if (name.isEmpty() || members.isEmpty()) return

        _state.update { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            runCatching { chatRepository.createGroup(name, members) }
                .onSuccess { conversation ->
                    _state.update { it.copy(loading = false, createdConversationId = conversation.id) }
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message ?: "Failed to create group") }
                }
        }
    }
}
