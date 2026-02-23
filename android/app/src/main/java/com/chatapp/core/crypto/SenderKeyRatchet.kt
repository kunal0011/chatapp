package com.chatapp.core.crypto

import android.util.Base64
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import com.google.crypto.tink.subtle.Hkdf
import java.security.SecureRandom

/**
 * SenderKeyRatchet — Implements the SenderKey symmetric ratchet for group E2EE.
 *
 * Protocol (per-sender):
 *   1. Sender generates a random 32-byte chainKey + Ed25519 signing keypair.
 *   2. Distributes (chainKey, publicVerifyKey) to each group member via 1:1 E2EE.
 *   3. On each message: ratchet chainKey → derive messageKey → AES-GCM encrypt → Ed25519 sign.
 *   4. Receivers: advance their copy of the chain to the sender's iteration → decrypt → verify signature.
 *
 * Forward secrecy: chain ratchet ensures compromised chain key can't recover past messages.
 * Authentication: Ed25519 signature prevents message forgery by a malicious relay.
 */
object SenderKeyRatchet {

    private const val HASH_ALGORITHM = "HMACSHA256"
    private const val INFO_MSG = "SenderKeyMessage"
    private const val INFO_CHAIN = "SenderKeyChain"

    /**
     * Generate a fresh SenderKey for the local user in a group.
     */
    fun generateSenderKey(groupId: String, myUserId: String): SenderKeyState {
        val chainKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val signingKeyPair = Ed25519Sign.KeyPair.newKeyPair()
        return SenderKeyState(
            groupId = groupId,
            senderUserId = myUserId,
            chainKey = chainKey,
            signingKeyPublic = signingKeyPair.publicKey,
            signingKeyPrivate = signingKeyPair.privateKey,
            iteration = 0
        )
    }

    /**
     * Encrypt a plaintext message using the sender's SenderKey.
     *
     * @param state The sender's current SenderKeyState (must have signingKeyPrivate).
     * @param plaintext The plaintext bytes to encrypt.
     * @return Pair of (SenderKeyMessage wire format, updated SenderKeyState with ratcheted chain).
     */
    fun encrypt(state: SenderKeyState, plaintext: ByteArray): Pair<SenderKeyMessage, SenderKeyState> {
        requireNotNull(state.signingKeyPrivate) { "Cannot encrypt without signing private key (this is not your own SenderKey)" }

        // Ratchet: derive messageKey from current chainKey
        val (newChainKey, messageKey) = ratchetChainKey(state.chainKey)

        // AES-GCM encrypt
        val aead = AesGcmJce(messageKey)
        val ciphertext = aead.encrypt(plaintext, null)

        // Ed25519 sign the ciphertext for authenticity
        val signer = Ed25519Sign(state.signingKeyPrivate)
        val signature = signer.sign(ciphertext)

        val message = SenderKeyMessage(
            groupId = state.groupId,
            senderUserId = state.senderUserId,
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            iteration = state.iteration,
            signature = Base64.encodeToString(signature, Base64.NO_WRAP)
        )

        val updatedState = state.copy(
            chainKey = newChainKey,
            iteration = state.iteration + 1
        )

        return Pair(message, updatedState)
    }

    /**
     * Decrypt an incoming SenderKey-encrypted group message.
     *
     * @param state The receiver's copy of the sender's SenderKeyState.
     * @param message The incoming SenderKeyMessage.
     * @return Pair of (plaintext bytes, updated SenderKeyState with advanced chain).
     * @throws SecurityException if signature verification fails.
     * @throws IllegalStateException if chain cannot be advanced to the required iteration.
     */
    fun decrypt(state: SenderKeyState, message: SenderKeyMessage): Pair<ByteArray, SenderKeyState> {
        val ciphertext = Base64.decode(message.ciphertext, Base64.NO_WRAP)
        val signature = Base64.decode(message.signature, Base64.NO_WRAP)

        // Verify Ed25519 signature FIRST (before any decryption attempt)
        try {
            val verifier = Ed25519Verify(state.signingKeyPublic)
            verifier.verify(signature, ciphertext)
        } catch (e: Exception) {
            throw SecurityException("SenderKey signature verification failed for ${message.senderUserId}: ${e.message}")
        }

        // Advance chain to match the message's iteration (handle skipped messages)
        var currentChain = state.chainKey
        var currentIteration = state.iteration
        val targetIteration = message.iteration

        if (targetIteration < currentIteration) {
            throw IllegalStateException(
                "Cannot decrypt: message iteration $targetIteration is behind current chain iteration $currentIteration (possible replay)"
            )
        }

        var messageKey: ByteArray? = null
        while (currentIteration <= targetIteration) {
            val (newChain, msgKey) = ratchetChainKey(currentChain)
            currentChain = newChain
            if (currentIteration == targetIteration) {
                messageKey = msgKey
            }
            currentIteration++
        }

        requireNotNull(messageKey) { "Failed to derive message key at iteration $targetIteration" }

        // AES-GCM decrypt
        val aead = AesGcmJce(messageKey)
        val plaintext = aead.decrypt(ciphertext, null)

        val updatedState = state.copy(
            chainKey = currentChain,
            iteration = currentIteration
        )

        return Pair(plaintext, updatedState)
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /**
     * KDF chain step: HKDF(chainKey) → (newChainKey, messageKey).
     * Same pattern as DoubleRatchet's KDF_CK but with SenderKey-specific info strings.
     */
    private fun ratchetChainKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val messageKey = Hkdf.computeHkdf(
            HASH_ALGORITHM,
            chainKey,
            ByteArray(32),  // salt = zeros
            INFO_MSG.toByteArray(Charsets.UTF_8),
            32
        )
        val newChainKey = Hkdf.computeHkdf(
            HASH_ALGORITHM,
            chainKey,
            ByteArray(32),
            INFO_CHAIN.toByteArray(Charsets.UTF_8),
            32
        )
        return Pair(newChainKey, messageKey)
    }
}
