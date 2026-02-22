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

data class AddMembersUiState(
    val loading: Boolean = false,
    val contacts: List<User> = emptyList(),
    val selectedUserIds: Set<String> = emptySet(),
    val success: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AddMembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contactsRepository: ContactsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _state = MutableStateFlow(AddMembersUiState())
    val state: StateFlow<AddMembersUiState> = _state.asStateFlow()

    init {
        loadContacts()
    }

    private fun loadContacts() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val allUsers = contactsRepository.getDirectory()
                val currentMembers = chatRepository.getGroupMembers(conversationId).map { it.userId }.toSet()

                // Filter out users who are already in the group
                val availableUsers = allUsers.filter { !currentMembers.contains(it.id) }

                _state.update { it.copy(contacts = availableUsers, loading = false) }
            } catch (t: Exception) {
                _state.update { it.copy(error = t.message, loading = false) }
            }
        }
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

    fun addMembers() {
        val members = _state.value.selectedUserIds.toList()
        if (members.isEmpty()) return

        _state.update { it.copy(loading = true, error = null) }

        viewModelScope.launch {
            try {
                // Repository needs addMembers method
                chatRepository.addGroupMembers(conversationId, members)
                _state.update { it.copy(loading = false, success = true) }
            } catch (t: Exception) {
                _state.update { it.copy(loading = false, error = t.message ?: "Failed to add members") }
            }
        }
    }
}
