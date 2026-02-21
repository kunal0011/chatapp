package com.chatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatapp.data.store.SessionStore
import com.chatapp.domain.repository.AuthRepository
import com.chatapp.domain.repository.ChatRepository
import com.chatapp.domain.repository.ContactsRepository
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AppSessionState(
    val loading: Boolean = true,
    val authenticated: Boolean = false
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val contactsRepository: ContactsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AppSessionState())
    val state: StateFlow<AppSessionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collectLatest { session ->
                val isAuthenticated = session != null
                _state.value = AppSessionState(
                    loading = false,
                    authenticated = isAuthenticated
                )
                
                if (isAuthenticated && session != null) {
                    registerPushToken()
                }
            }
        }
    }

    private fun registerPushToken() {
        viewModelScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                contactsRepository.updatePushToken(token)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            chatRepository.disconnectRealtime()
            authRepository.logout()
        }
    }
}
