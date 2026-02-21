package com.chatapp.data.repository

import android.app.Application
import android.provider.ContactsContract
import com.chatapp.data.api.ConversationsApi
import com.chatapp.data.api.UsersApi
import com.chatapp.data.dto.*
import com.chatapp.data.network.NetworkErrorMapper
import com.chatapp.data.network.SessionGateway
import com.chatapp.domain.model.Conversation
import com.chatapp.domain.model.DeviceContact
import com.chatapp.domain.model.User
import com.chatapp.domain.repository.ContactsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ContactsRepositoryImpl @Inject constructor(
    private val application: Application,
    private val usersApi: UsersApi,
    private val conversationsApi: ConversationsApi,
    private val sessionGateway: SessionGateway
) : ContactsRepository {
    override suspend fun getContacts(): List<User> {
        return runCatching<List<User>> {
            usersApi.getContacts().contacts.map { it.toDomain() }
        }.getOrElse { throwable ->
            throw IllegalStateException(NetworkErrorMapper.toUserMessage(throwable, "Failed to load contacts"), throwable)
        }
    }

    override suspend fun syncContactsFromDevice(): List<User> {
        return runCatching<List<User>> {
            val devicePhones = fetchDeviceContacts().map { it.phone }
            if (devicePhones.isEmpty()) return getContacts()
            usersApi.syncContacts(SyncContactsRequest(devicePhones)).contacts.map { it.toDomain() }
        }.getOrElse { throwable ->
            throw IllegalStateException(NetworkErrorMapper.toUserMessage(throwable, "Failed to sync contacts"), throwable)
        }
    }

    override suspend fun discoverContactsFromDevice(): List<User> {
        return runCatching<List<User>> {
            val devicePhones = fetchDeviceContacts().map { it.phone }
            if (devicePhones.isEmpty()) return emptyList()
            usersApi.discoverContacts(SyncContactsRequest(devicePhones)).contacts.map { it.toDomain() }
        }.getOrElse { throwable ->
            throw IllegalStateException(NetworkErrorMapper.toUserMessage(throwable, "Failed to discover contacts"), throwable)
        }
    }

    override suspend fun getDeviceContacts(): List<DeviceContact> {
        return runCatching<List<DeviceContact>> {
            val myPhone = sessionGateway.requireSession().phone.replace(Regex("[^0-9]"), "").takeLast(10)
            val localContacts = fetchDeviceContacts()
            if (localContacts.isEmpty()) return emptyList()
            
            val registeredUsers = discoverContactsFromDevice()
            val registeredMap = registeredUsers.associateBy { it.phone.replace(Regex("[^0-9]"), "").takeLast(10) }
            
            localContacts
                .filter { contact -> 
                    // Filter out own number
                    contact.phone.replace(Regex("[^0-9]"), "").takeLast(10) != myPhone 
                }
                .map { local ->
                    val normalizedLocal = local.phone.replace(Regex("[^0-9]"), "").takeLast(10)
                    val registered = registeredMap[normalizedLocal]
                    local.copy(registeredUser = registered)
                }
                .sortedByDescending { it.isOnChatApp }
        }.getOrElse { throwable ->
            throw IllegalStateException(NetworkErrorMapper.toUserMessage(throwable, "Failed to load device contacts"), throwable)
        }
    }

    private suspend fun fetchDeviceContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        val contacts = mutableListOf<DeviceContact>()
        val cursor = application.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIndex)
                val number = it.getString(numberIndex)
                if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                    contacts.add(DeviceContact(name = name, phone = number))
                }
            }
        }
        contacts.groupBy { it.phone.replace(Regex("[^0-9]"), "").takeLast(10) }.map { it.value.first() }
    }

    override suspend fun getDirectory(): List<User> {
        return runCatching<List<User>> {
            val myId = sessionGateway.requireSession().userId
            usersApi.getGlobalUsers().contacts
                .filter { it.id != myId } // Filter out self from global directory
                .map { it.toDomain() }
        }.getOrElse { throwable ->
            throw IllegalStateException(NetworkErrorMapper.toUserMessage(throwable, "Failed to load directory"), throwable)
        }
    }

    override suspend fun addContact(userId: String): List<User> {
        return runCatching<List<User>> {
            usersApi.addContactByUserId(mapOf("contactId" to userId)).contacts.map { it.toDomain() }
        }.getOrElse { throwable ->
            throw IllegalStateException(NetworkErrorMapper.toUserMessage(throwable, "Failed to add contact"), throwable)
        }
    }

    override suspend fun getMyProfile(): User {
        return runCatching<User> {
            usersApi.getCurrentProfile().user.toDomain()
        }.getOrElse { throwable ->
            throw IllegalStateException(NetworkErrorMapper.toUserMessage(throwable, "Failed to load profile"), throwable)
        }
    }

    override suspend fun updateMyProfile(displayName: String): User {
        return runCatching<User> {
            usersApi.updateProfile(mapOf("displayName" to displayName)).user.toDomain()
        }.getOrElse { throwable ->
            throw IllegalStateException(NetworkErrorMapper.toUserMessage(throwable, "Failed to update profile"), throwable)
        }
    }

    override suspend fun updatePushToken(token: String) {
        runCatching<Unit> { usersApi.updatePushToken(mapOf("token" to token)) }
    }

    override suspend fun blockUser(userId: String) {
        runCatching<Unit> { usersApi.blockUser(userId = userId) }
    }

    override suspend fun unblockUser(userId: String) {
        runCatching<Unit> { usersApi.unblockUser(userId = userId) }
    }

    override suspend fun startDirectConversation(otherUserId: String): Conversation {
        return runCatching<Conversation> {
            val userId = sessionGateway.requireSession().userId
            val response = conversationsApi.createDirectConversation(DirectConversationRequest(otherUserId))
            response.conversation.toDomain(userId)
        }.getOrElse { throwable ->
            throw IllegalStateException(NetworkErrorMapper.toUserMessage(throwable, "Failed to start conversation"), throwable)
        }
    }
}
