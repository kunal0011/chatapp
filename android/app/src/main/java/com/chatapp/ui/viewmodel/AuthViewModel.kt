package com.chatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthStep {
    ENTER_PHONE,
    VERIFY_OTP,
    SETUP_PROFILE
}

data class AuthUiState(
    val step: AuthStep = AuthStep.ENTER_PHONE,
    val phone: String = "+1",
    val otpCode: String = "",
    val displayName: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) {
        _state.value = _state.value.copy(phone = value, error = null)
    }

    fun onOtpChange(value: String) {
        if (value.length <= 6) {
            _state.value = _state.value.copy(otpCode = value, error = null)
        }
    }

    fun onDisplayNameChange(value: String) {
        _state.value = _state.value.copy(displayName = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun togglePasswordVisibility() {
        _state.value = _state.value.copy(passwordVisible = !_state.value.passwordVisible)
    }

    fun requestOtp() {
        val current = _state.value
        val normalizedPhone = normalize(current.phone)
        if (normalizedPhone.isBlank()) {
            _state.value = current.copy(error = "Phone is required")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { authRepository.requestOtp(normalizedPhone) }
                .onSuccess {
                    _state.value = _state.value.copy(loading = false, step = AuthStep.VERIFY_OTP)
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(loading = false, error = t.message)
                }
        }
    }

    fun verifyOtp() {
        val current = _state.value
        if (current.otpCode.length < 6) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { authRepository.verifyOtp(current.phone, current.otpCode) }
                .onSuccess { (isNewUser, session) ->
                    if (isNewUser) {
                        _state.value = _state.value.copy(loading = false, step = AuthStep.SETUP_PROFILE)
                    } else {
                        // Already logged in by repository saving session
                        _state.value = _state.value.copy(loading = false)
                    }
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(loading = false, error = t.message)
                }
        }
    }

    fun completeProfile() {
        val current = _state.value
        if (current.displayName.isBlank() || current.password.isBlank()) {
            _state.value = current.copy(error = "All fields required")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                authRepository.register(current.phone, current.password, current.displayName)
            }.onSuccess {
                _state.value = _state.value.copy(loading = false)
            }.onFailure { t ->
                _state.value = _state.value.copy(loading = false, error = t.message)
            }
        }
    }

    private fun normalize(phone: String) = phone.filterNot { it.isWhitespace() || it == '-' }
}
