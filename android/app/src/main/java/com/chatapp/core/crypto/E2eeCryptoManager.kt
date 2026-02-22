package com.chatapp.core.crypto

import android.content.SharedPreferences
import android.util.Base64
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import com.chatapp.core.di.E2eePrefs
import com.chatapp.data.api.KeysApi
import com.chatapp.data.dto.KeyBundleRequest
import com.chatapp.data.dto.SignedPreKeyDto
import com.chatapp.data.dto.OneTimePreKeyDto
import com.chatapp.data.dto.OneTimePreKeysRequest

/**
 * E2eeCryptoManager — Central facade for all end-to-end encryption operations.
 *
 * Responsibilities:
 * - Key generation and upload to server (on first login)
 * - Initiating X3DH sessions when sending to a new contact
 * - Persisting and loading Double Ratchet state per conversation
 * - Encrypt plaintext before sending — returns EncryptedPayload
 * - Decrypt EncryptedPayload after receiving — returns plaintext
 *
 * Usage in ChatRepositoryImpl:
 *   val payload = cryptoManager.encrypt(conversationId, recipientId, plaintext)
 *   val plain   = cryptoManager.decrypt(conversationId, senderId, payload)
 */
@Singleton
class E2eeCryptoManager @Inject constructor(
    private val keyStore: E2eeKeyStore,
    private val keysApi: KeysApi,
    @E2eePrefs private val encryptedPrefs: SharedPreferences
) {
    companion object {
        private const val RATCHET_PREFIX = "ratchet_"
        private const val LOW_OPK_THRESHOLD = 3
    }

    private val secureRandom = SecureRandom()
    // In-memory ratchet cache keyed by conversationId — avoids expensive deserialization per message
    private val ratchetCache = mutableMapOf<String, DoubleRatchet>()

    // ---------------------------------------------------------------
    // Key Setup
    // ---------------------------------------------------------------

    /**
     * Called on every app start (after login).
     *
     * Strategy:
     * 1. If no local keys exist → generate fresh keys and upload to server.
     * 2. If local keys exist → ask the server how many OPKs it has.
     *    - If server has 0 (upload never reached it, or server was wiped) → re-upload existing bundle.
     *    - If server is low → replenish OPKs only.
     *
     * This ensures the server always has a valid bundle even after:
     *   - Server restarts that lost in-memory Prisma client during the initial upload
     *   - Server-side DB resets
     *   - App re-installs where keys were regenerated
     */
    suspend fun ensureKeysUploaded() {
        if (!keyStore.hasKeys()) {
            // First run — generate and upload a fresh bundle
            val bundle = keyStore.getPublicKeyBundle()
            uploadBundle(bundle)
            return
        }

        // Keys exist locally — verify the server also has them
        val serverCount = try {
            keysApi.getOPKCount().oneTimePreKeyCount
        } catch (e: Exception) {
            android.util.Log.w("E2eeCryptoManager", "Could not check OPK count: ${e.message}")
            -1  // treat as unknown — don't re-upload on a transient network error
        }

        when {
            serverCount == 0 -> {
                // Server has no OPKs — our upload never succeeded (e.g. server restarted mid-upload).
                // Re-upload and clear all ratchet sessions: they were established with the old key
                // bundle and will produce BAD_DECRYPT on the other side.
                android.util.Log.i("E2eeCryptoManager", "Server has 0 OPKs — re-uploading key bundle and clearing stale sessions")
                val existingBundle = keyStore.getExistingPublicBundle()
                if (existingBundle != null && existingBundle.oneTimePreKeys.isNotEmpty()) {
                    uploadBundle(existingBundle)
                } else {
                    uploadBundle(keyStore.getPublicKeyBundle())
                }
                clearAllRatchetSessions()   // ← wipe stale ratchet state
            }
            serverCount in 1 until LOW_OPK_THRESHOLD -> {
                // Running low — top up the pool
                replenishOPKsIfNeeded()
            }
            // serverCount >= LOW_OPK_THRESHOLD or -1 (network error) → nothing to do
        }
    }

    /** Upload a full key bundle to the server. */
    private suspend fun uploadBundle(bundle: PublicKeyBundle) {
        keysApi.uploadKeyBundle(
            KeyBundleRequest(
                identityKey = bundle.identityKey,
                signedPreKey = SignedPreKeyDto(
                    keyId = bundle.signedPreKey.keyId,
                    publicKey = bundle.signedPreKey.publicKey,
                    signature = bundle.signedPreKey.signature
                ),
                oneTimePreKeys = bundle.oneTimePreKeys.map {
                    OneTimePreKeyDto(keyId = it.keyId, publicKey = it.publicKey)
                }
            )
        )
    }

    /**
     * Check OPK count from server; if below threshold, generate and upload new ones.
     */
    suspend fun replenishOPKsIfNeeded() {
        try {
            val response = keysApi.getOPKCount()
            if (response.oneTimePreKeyCount < LOW_OPK_THRESHOLD) {
                val newOPKs = keyStore.generateOneTimePreKeys(10)
                keysApi.replenishOneTimePreKeys(
                    OneTimePreKeysRequest(
                        oneTimePreKeys = newOPKs.map {
                            OneTimePreKeyDto(keyId = it.keyId, publicKey = Base64.encodeToString(it.publicKey, Base64.NO_WRAP))
                        }
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("E2eeCryptoManager", "OPK replenishment check failed (non-fatal): ${e.message}")
        }
    }

    // ---------------------------------------------------------------
    // Encrypt (sender side)
    // ---------------------------------------------------------------

    /**
     * Encrypt [plaintext] for [recipientId] in [conversationId].
     *
     * On first message: performs X3DH to establish session, then Double Ratchet encrypt.
     * On subsequent messages: Double Ratchet encrypt only.
     */
    suspend fun encrypt(conversationId: String, recipientId: String, plaintext: String): EncryptedPayload {
        val ratchet = getOrInitRatchetForSending(conversationId, recipientId)
        val payload = ratchet.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        saveRatchetState(conversationId, ratchet)
        return payload
    }

    private suspend fun getOrInitRatchetForSending(conversationId: String, recipientId: String): DoubleRatchet {
        ratchetCache[conversationId]?.let { return it }
        loadRatchetState(conversationId)?.let {
            ratchetCache[conversationId] = it
            return it
        }

        // No existing session → perform X3DH to create one
        val recipientBundle = fetchAndParseRecipientBundle(recipientId)
        val identityPair = keyStore.getOrCreateIdentityKeyPair()
        val ephemeralKeyPair = generateEphemeralX25519()

        val x3dhResult = X3DHHandshake.computeInitiatorSharedSecret(
            ourIdentityKey = identityPair,
            ourEphemeralKey = ephemeralKeyPair,
            recipientBundle = recipientBundle
        )

        val ratchet = DoubleRatchet.forInitiator(
            sharedSecret = x3dhResult.sharedSecret,
            remoteSpkPublic = recipientBundle.signedPreKeyPublic
        )

        // Embed X3DH header into the first EncryptedPayload in the cache so encrypt() can attach it
        // We do this by wrapping the ratchet in a session-init-aware subclass approach — we simply
        // store pending X3DH header in the manager and attach it in the encryptWithHeader() call.
        pendingX3DHHeaders[conversationId] = X3DHHeader(
            senderIdentityKey = X3DHHandshake.encodePublicKey(identityPair.publicKey),
            ephemeralKey = X3DHHandshake.encodePublicKey(x3dhResult.ephemeralPublicKey),
            usedSignedPreKeyId = recipientBundle.signedPreKeyId,
            usedOneTimePreKeyId = recipientBundle.oneTimePreKeyId
        )

        ratchetCache[conversationId] = ratchet
        return ratchet
    }

    private data class X3DHHeader(
        val senderIdentityKey: String,
        val ephemeralKey: String,
        val usedSignedPreKeyId: Int,
        val usedOneTimePreKeyId: Int?
    )
    private val pendingX3DHHeaders = mutableMapOf<String, X3DHHeader>()

    /**
     * Encrypt with optional X3DH header for session initiation.
     */
    suspend fun encryptWithHeader(conversationId: String, recipientId: String, plaintext: String): EncryptedPayload {
        val ratchet = getOrInitRatchetForSending(conversationId, recipientId)
        val rawPayload = ratchet.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        saveRatchetState(conversationId, ratchet)

        val header = pendingX3DHHeaders.remove(conversationId)
        return if (header != null) {
            rawPayload.copy(
                senderIdentityKey = header.senderIdentityKey,
                ephemeralKey = header.ephemeralKey,
                usedSignedPreKeyId = header.usedSignedPreKeyId,
                usedOneTimePreKeyId = header.usedOneTimePreKeyId
            )
        } else {
            rawPayload
        }
    }

    // ---------------------------------------------------------------
    // Decrypt (receiver side)
    // ---------------------------------------------------------------

    // Cache of ciphertext -> plaintext to prevent BAD_DECRYPT if fetchHistory and socket race
    // to decrypt the exact same message concurrently. Double Ratchet cannot decrypt the same
    // message twice because the key chain advances.
    private val decryptedCache = mutableMapOf<String, String>()

    /**
     * Decrypt incoming [payload] from [senderId] in [conversationId].
     *
     * If this is the first message in the session (ephemeralKey present), performs X3DH
     * responder computation to establish the shared secret, then Double Ratchet decrypt.
     */
    @Synchronized
    fun decrypt(conversationId: String, payload: EncryptedPayload): String {
        // Prevent Double Ratchet state corruption from duplicate concurrent decryptions
        decryptedCache[payload.ciphertext]?.let { return it }

        val ratchet = getOrInitRatchetForReceiving(conversationId, payload)
        val plaintext = ratchet.decrypt(payload)
        saveRatchetState(conversationId, ratchet)
        
        val resultString = plaintext.toString(Charsets.UTF_8)
        decryptedCache[payload.ciphertext] = resultString
        return resultString
    }

    private fun getOrInitRatchetForReceiving(conversationId: String, payload: EncryptedPayload): DoubleRatchet {
        // If the payload has an ephemeralKey, the sender is forcing a new X3DH session!
        // This happens if this is the first message between them, OR if the sender cleared app data
        // and generated new keys. We MUST overwrite our old session to regain sync.
        if (payload.ephemeralKey != null && payload.senderIdentityKey != null) {
            android.util.Log.i("E2eeCryptoManager", "New X3DH header detected. Establishing new session.")
            val senderIK = X3DHHandshake.decodePublicKey(payload.senderIdentityKey)
            val ephemeralKey = X3DHHandshake.decodePublicKey(payload.ephemeralKey)
            
            val sharedSecret = X3DHHandshake.computeResponderSharedSecret(
                ourKeyStore = keyStore,
                senderIdentityKeyPublic = senderIK,
                ephemeralKeyPublic = ephemeralKey,
                usedSignedPreKeyId = payload.usedSignedPreKeyId ?: error("Missing SPK ID"),
                usedOneTimePreKeyId = payload.usedOneTimePreKeyId
            )

            val ourSpk = keyStore.getOrCreateSignedPreKey()
            val newRatchet = DoubleRatchet.forResponder(
                sharedSecret = sharedSecret,
                localSpkPrivate = ourSpk.privateKey,
                localSpkPublic = ourSpk.publicKey
            )
            ratchetCache[conversationId] = newRatchet
            return newRatchet
        }

        // Standard message (no X3DH header). Must use existing session.
        ratchetCache[conversationId]?.let { return it }
        loadRatchetState(conversationId)?.let {
            ratchetCache[conversationId] = it
            return it
        }

        error("No session exists for conversation $conversationId and no ephemeral key provided to start one.")
    }

    // ---------------------------------------------------------------
    // Session management
    // ---------------------------------------------------------------

    fun hasSession(conversationId: String): Boolean =
        ratchetCache.containsKey(conversationId) || loadRatchetState(conversationId) != null

    private fun saveRatchetState(conversationId: String, ratchet: DoubleRatchet) {
        val map = ratchet.toMap()
        val editor = encryptedPrefs.edit()
        map.forEach { (k, v) ->
            editor.putString("$RATCHET_PREFIX${conversationId}_$k", v)
        }
        editor.apply()
    }

    private fun loadRatchetState(conversationId: String): DoubleRatchet? {
        val keys = listOf("rootKey", "remotePub", "localPriv", "localPub", "sendChain", "recvChain", "sendIdx", "recvIdx", "prevLen")
        val map = mutableMapOf<String, String>()
        for (k in keys) {
            val value = encryptedPrefs.getString("$RATCHET_PREFIX${conversationId}_$k", null) ?: return null
            map[k] = value
        }
        return try { DoubleRatchet.fromMap(map) } catch (e: Exception) { null }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private suspend fun fetchAndParseRecipientBundle(recipientId: String): ReceivedKeyBundle {
        val response = keysApi.getKeyBundle(recipientId)
        return ReceivedKeyBundle(
            userId = response.userId,
            identityKeyPublic = X3DHHandshake.decodePublicKey(response.identityKey),
            signedPreKeyId = response.signedPreKey.keyId,
            signedPreKeyPublic = X3DHHandshake.decodePublicKey(response.signedPreKey.publicKey),
            oneTimePreKeyId = response.oneTimePreKey?.keyId,
            oneTimePreKeyPublic = response.oneTimePreKey?.let { X3DHHandshake.decodePublicKey(it.publicKey) }
        )
    }

    private fun generateEphemeralX25519(): Pair<ByteArray, ByteArray> {
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(secureRandom))
        val keyPair = generator.generateKeyPair()
        val priv = (keyPair.private as X25519PrivateKeyParameters).encoded
        val pub = (keyPair.public as X25519PublicKeyParameters).encoded
        return Pair(priv, pub)
    }

    /**
     * Clear ALL persisted ratchet sessions from EncryptedSharedPreferences.
     * Called when the key bundle is re-uploaded (server had 0 OPKs).
     * Any sessions built on the old key bundle will produce BAD_DECRYPT — wiping them
     * forces fresh X3DH on the next message exchange.
     */
    private fun clearAllRatchetSessions() {
        ratchetCache.clear()
        val editor = encryptedPrefs.edit()
        val allKeys = encryptedPrefs.all.keys
        allKeys.filter { it.startsWith(RATCHET_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
        android.util.Log.i("E2eeCryptoManager", "Cleared ${allKeys.count { it.startsWith(RATCHET_PREFIX) }} stale ratchet session keys")
    }
}

