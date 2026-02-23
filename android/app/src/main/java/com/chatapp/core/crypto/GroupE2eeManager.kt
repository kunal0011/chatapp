package com.chatapp.core.crypto

import com.chatapp.data.api.KeysApi
import com.chatapp.data.dto.DistributeSenderKeysRequest
import com.chatapp.data.dto.SenderKeyDistributionDto
import com.chatapp.data.local.dao.MemberDao
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GroupE2eeManager — Orchestrates the SenderKey protocol lifecycle.
 *
 * Responsibilities:
 * - Generate and distribute own SenderKey to all group members.
 * - Fetch and decrypt pending SenderKeys from others.
 * - Encrypt outgoing group messages.
 * - Decrypt incoming group messages.
 * - Rotate own SenderKey when required (e.g., member removal).
 */
@Singleton
class GroupE2eeManager @Inject constructor(
    private val senderKeyStore: SenderKeyStore,
    private val keysApi: KeysApi,
    private val e2eeCryptoManager: E2eeCryptoManager,
    private val memberDao: MemberDao
) {
    private val gson = Gson()

    /**
     * Called before sending a message to a group.
     * Ensures we have a SenderKey for this group and that it has been distributed
     * to all current members.
     */
    suspend fun ensureSenderKeyDistributed(groupId: String, myUserId: String) = withContext(Dispatchers.IO) {
        // 1. Get or create our own SenderKey
        val myState = senderKeyStore.load(groupId, myUserId) ?: run {
            val newState = SenderKeyRatchet.generateSenderKey(groupId, myUserId)
            senderKeyStore.save(newState)
            android.util.Log.i("GroupE2eeManager", "Generated new SenderKey for group $groupId")
            newState
        }

        // 2. We should ideally only distribute to members who don't have it yet,
        // but for simplicity (and since server is idempotent via Upsert), we distribute to all.
        // In a real app, we'd track who we've sent it to.
        val members = memberDao.getMembersOnce(groupId, myUserId) // gets all members
        
        val distributionKeys = members
            .filter { it.userId != myUserId }
            .mapNotNull { member ->
                // Encrypt the SenderKeyDistribution blob via 1:1 E2EE for this member
                val distributionPayload = myState.toDistribution()
                val plaintext = gson.toJson(distributionPayload)
                
                try {
                    // This uses X3DH + DoubleRatchet to encrypt the SenderKey for the recipient
                    val encryptedPayload = e2eeCryptoManager.encryptWithHeader(groupId, member.userId, plaintext)
                    val encryptedJson = gson.toJson(encryptedPayload)
                    
                    SenderKeyDistributionDto(
                        recipientUserId = member.userId,
                        encryptedKey = encryptedJson
                    )
                } catch (e: Exception) {
                    android.util.Log.e("GroupE2eeManager", "Failed to encrypt SenderKey for ${member.userId}: ${e.message}")
                    null
                }
            }

        if (distributionKeys.isNotEmpty()) {
            try {
                keysApi.distributeSenderKeys(groupId, DistributeSenderKeysRequest(distributionKeys))
                android.util.Log.i("GroupE2eeManager", "Distributed SenderKey to ${distributionKeys.size} members in $groupId")
            } catch (e: Exception) {
                android.util.Log.e("GroupE2eeManager", "Failed to distribute SenderKeys to server: ${e.message}")
                throw e
            }
        }
    }

    /**
     * Called before decrypting an incoming group message.
     * If we don't have the sender's SenderKey, we fetch pending keys from the server.
     */
    suspend fun ensureSenderKeyReceived(groupId: String, senderUserId: String, myUserId: String): SenderKeyState {
        // 1. Check local store
        senderKeyStore.load(groupId, senderUserId)?.let { return it }

        // 2. If missing, fetch from server
        android.util.Log.i("GroupE2eeManager", "Missing SenderKey for $senderUserId in $groupId, fetching from server...")
        
        try {
            val response = keysApi.fetchSenderKeys(groupId)
            
            // Decrypt all fetched keys and save them
            response.keys.forEach { dto ->
                if (dto.senderUserId == null) return@forEach // Should be present from server response
                
                try {
                    // Decrypt the 1:1 payload
                    val encryptedPayload = gson.fromJson(dto.encryptedKey, com.chatapp.core.crypto.EncryptedPayload::class.java)
                    val plaintextJson = e2eeCryptoManager.decrypt(groupId, encryptedPayload)
                    
                    // Parse the SenderKeyDistribution
                    val distribution = gson.fromJson(plaintextJson, com.chatapp.core.crypto.SenderKeyDistribution::class.java)
                    val state = distribution.toState()
                    
                    senderKeyStore.save(state)
                    android.util.Log.i("GroupE2eeManager", "Successfully received and decrypted SenderKey from ${dto.senderUserId}")
                } catch (e: Exception) {
                    android.util.Log.e("GroupE2eeManager", "Failed to decrypt SenderKey from ${dto.senderUserId}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GroupE2eeManager", "Failed to fetch SenderKeys: ${e.message}")
            // Don't throw yet, maybe the key was populated by another concurrent request
        }

        // Return from store, or throw if still missing
        return senderKeyStore.load(groupId, senderUserId)
            ?: throw IllegalStateException("SenderKey for $senderUserId not found even after fetching")
    }

    /**
     * Encrypt a group message using our own SenderKey.
     * Automatically ratchets our chain.
     */
    fun encryptGroupMessage(groupId: String, myUserId: String, plaintext: String): SenderKeyMessage {
        val myState = senderKeyStore.load(groupId, myUserId)
            ?: throw IllegalStateException("Own SenderKey not found. Did you call ensureSenderKeyDistributed?")
            
        val (message, updatedState) = SenderKeyRatchet.encrypt(myState, plaintext.toByteArray(Charsets.UTF_8))
        senderKeyStore.save(updatedState) // Save advanced chain
        return message
    }

    /**
     * Decrypt an incoming group message using the sender's SenderKey.
     * Automatically advances the chain to the message's iteration.
     */
    fun decryptGroupMessage(groupId: String, senderUserId: String, message: SenderKeyMessage): String {
        val senderState = senderKeyStore.load(groupId, senderUserId)
            ?: throw IllegalStateException("SenderKey for $senderUserId not found. Did you call ensureSenderKeyReceived?")
            
        val (plaintextBytes, updatedState) = SenderKeyRatchet.decrypt(senderState, message)
        senderKeyStore.save(updatedState) // Save advanced chain
        return String(plaintextBytes, Charsets.UTF_8)
    }

    /**
     * Rotate our SenderKey. Required when a member leaves or is removed.
     */
    suspend fun rotateSenderKey(groupId: String, myUserId: String) = withContext(Dispatchers.IO) {
        android.util.Log.i("GroupE2eeManager", "Rotating SenderKey for group $groupId")
        
        // 1. Revoke existing distributions on server
        runCatching { keysApi.revokeMySenderKey(groupId) }
        
        // 2. Delete local copy
        senderKeyStore.deleteOwn(groupId, myUserId)
        
        // 3. Generate and distribute new key
        ensureSenderKeyDistributed(groupId, myUserId)
    }
}
