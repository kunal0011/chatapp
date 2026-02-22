package com.chatapp.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.domain.model.MessageRecipientStatus
import com.chatapp.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageInfoUiState(
    val recipients: List<MessageRecipientStatus> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MessageInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val messageId: String = checkNotNull(savedStateHandle["messageId"])

    private val _state = MutableStateFlow(MessageInfoUiState())
    val state: StateFlow<MessageInfoUiState> = _state.asStateFlow()

    init {
        loadInfo()
    }

    private fun loadInfo() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val info = chatRepository.getMessageInfo(messageId)
                _state.update { it.copy(recipients = info, loading = false) }
            } catch (t: Exception) {
                _state.update { it.copy(error = t.message, loading = false) }
            }
        }
    }
}
