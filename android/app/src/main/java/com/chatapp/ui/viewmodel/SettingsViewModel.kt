package com.chatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.domain.model.User
import com.chatapp.domain.repository.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val loading: Boolean = true,
    val user: User? = null,
    val displayNameInput: String = "",
    val updating: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { contactsRepository.getMyProfile() }
                .onSuccess { user ->
                    _state.value = _state.value.copy(
                        loading = false,
                        user = user,
                        displayNameInput = user.displayName
                    )
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = throwable.message ?: "Failed to load profile"
                    )
                }
        }
    }

    fun onDisplayNameChange(value: String) {
        _state.value = _state.value.copy(displayNameInput = value, error = null, successMessage = null)
    }

    fun updateProfile() {
        val newName = _state.value.displayNameInput.trim()
        if (newName.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(updating = true, error = null, successMessage = null)
            runCatching { contactsRepository.updateMyProfile(newName) }
                .onSuccess { user ->
                    _state.value = _state.value.copy(
                        updating = false,
                        user = user,
                        successMessage = "Profile updated successfully"
                    )
                }
                .onFailure { throwable ->
                    _state.value = _state.value.copy(
                        updating = false,
                        error = throwable.message ?: "Failed to update profile"
                    )
                }
        }
    }
}
