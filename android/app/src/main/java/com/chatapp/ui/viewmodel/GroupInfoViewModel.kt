package com.chatapp.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.domain.model.Conversation
import com.chatapp.domain.model.GroupMember
import com.chatapp.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupInfoUiState(
    val conversation: Conversation? = null,
    val members: List<GroupMember> = emptyList(),
    val currentUserRole: String = "MEMBER",
    val loading: Boolean = false,
    val error: String? = null,
    val exited: Boolean = false
)

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])

    private val _state = MutableStateFlow(GroupInfoUiState())
    val state: StateFlow<GroupInfoUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val currentUserId = chatRepository.currentUserIdOrNull()
                val conv = chatRepository.getConversation(conversationId)
                val isMember = conv?.isMember ?: false

                val members = if (isMember) {
                    try {
                        chatRepository.getGroupMembers(conversationId)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val myRole = members.find { it.userId == currentUserId }?.role ?: "MEMBER"

                _state.update { it.copy(
                    conversation = conv,
                    members = members,
                    currentUserRole = myRole,
                    loading = false
                ) }
            } catch (t: Exception) {
                _state.update { it.copy(error = t.message, loading = false) }
            }
        }
    }

    fun kickMember(userId: String) {
        viewModelScope.launch {
            try {
                chatRepository.removeMember(conversationId, userId)
                loadData()
            } catch (t: Exception) {
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun updateRole(userId: String, newRole: String) {
        viewModelScope.launch {
            try {
                chatRepository.updateMemberRole(conversationId, userId, newRole)
                loadData()
            } catch (t: Exception) {
                _state.update { it.copy(error = t.message) }
            }
        }
    }

    fun updateGroupName(newName: String) {
        if (newName.isBlank()) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val updated = chatRepository.updateGroupMetadata(conversationId, newName, _state.value.conversation?.description)
                _state.update { it.copy(conversation = updated, loading = false) }
            } catch (t: Exception) {
                _state.update { it.copy(error = t.message, loading = false) }
            }
        }
    }

    fun leaveGroup() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                chatRepository.leaveGroup(conversationId)
                _state.update { it.copy(loading = false, exited = true) }
            } catch (t: Exception) {
                _state.update { it.copy(error = t.message, loading = false) }
            }
        }
    }

    fun exitGroup() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching {
                // First leave on backend (if still a member)
                if (_state.value.conversation?.isMember == true) {
                    chatRepository.leaveGroup(conversationId)
                }
                // Then delete locally
                chatRepository.deleteConversationLocally(conversationId)
                _state.update { it.copy(loading = false, exited = true) }
            }.onFailure { t ->
                _state.update { it.copy(error = t.message, loading = false) }
            }
        }
    }
}
