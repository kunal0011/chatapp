package com.chatapp.domain.model

data class DeviceContact(
    val name: String,
    val phone: String,
    val registeredUser: User? = null
) {
    val isOnChatApp: Boolean get() = registeredUser != null
}
