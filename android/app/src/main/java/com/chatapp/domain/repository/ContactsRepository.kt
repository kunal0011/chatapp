package com.chatapp.domain.repository

import com.chatapp.domain.model.Conversation
import com.chatapp.domain.model.DeviceContact
import com.chatapp.domain.model.User

interface ContactsRepository {
    suspend fun getContacts(): List<User>
    suspend fun startDirectConversation(otherUserId: String): Conversation
    suspend fun syncContactsFromDevice(): List<User>
    suspend fun discoverContactsFromDevice(): List<User>
    suspend fun getDeviceContacts(): List<DeviceContact>
    suspend fun getDirectory(): List<User>
    suspend fun addContact(userId: String): List<User>
    suspend fun getMyProfile(): User
    suspend fun updateMyProfile(displayName: String): User
    suspend fun updatePushToken(token: String)
    suspend fun blockUser(userId: String)
    suspend fun unblockUser(userId: String)
}
