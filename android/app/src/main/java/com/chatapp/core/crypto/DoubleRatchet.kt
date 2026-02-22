package com.chatapp.core.crypto

import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.subtle.AesGcmJce
import com.google.crypto.tink.subtle.Hkdf
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.agreement.X25519Agreement
import java.security.SecureRandom

/**
 * Encrypted payload that is sent over the wire.
 * On the first message of a session, senderIdentityKey and all X3DH header fields are included.
 */
data class EncryptedPayload(
    val ciphertext: String,           // base64 AES-256-GCM ciphertext (includes 12-byte IV prepended)
    val ratchetKey: String,           // base64 current ratchet public key
    val messageIndex: Int,            // N_s: index of this message in sending chain
    val previousChainLength: Int,     // prev N_s before the last DH ratchet step
    // X3DH session initiation fields (only present in first message):
    val senderIdentityKey: String? = null,
    val ephemeralKey: String? = null,
    val usedSignedPreKeyId: Int? = null,
    val usedOneTimePreKeyId: Int? = null
)

/**
 * DoubleRatchet: per-conversation message encryption.
 *
 * Implements the Double Ratchet Algorithm:
 * - DH Ratchet: both parties exchange new Curve25519 keys to advance the root key
 * - Sending Chain: sequential symmetric key derivation for each sent message
 * - Receiving Chain: symmetric key derivation for each received message
 *
 * Combines X3DH-derived shared secret with per-message key derivation to give:
 * - Perfect Forward Secrecy: old message keys are deleted after use
 * - Break-in Recovery: after a DH ratchet step, future messages re-secure
 *
 * Reference: https://signal.org/docs/specifications/doubleratchet/
 *
 * @param sharedSecret The 32-byte root key from X3DH (or previous ratchet step)
 * @param remoteRatchetKey The remote party's initial ratchet public key (= their SPK public key for initiator)
 * @param isInitiator True for Alice (sender), false for Bob (receiver)
 */
class DoubleRatchet private constructor(
    private var rootKey: ByteArray,
    private var remotePubRatchetKey: ByteArray,
    private var localRatchetPrivKey: ByteArray,
    private var localRatchetPubKey: ByteArray,
    private var sendingChainKey: ByteArray?,
    private var receivingChainKey: ByteArray?,
    private var sendMessageIndex: Int,
    private var receiveMessageIndex: Int,
    private var previousChainLength: Int,
    private val secureRandom: SecureRandom
) {

    companion object {
        private const val HASH_ALGORITHM = "HmacSHA256"
        private const val INFO_ROOT = "ChatApp DR RootKey v1"
        private const val INFO_CHAIN = "ChatApp DR ChainKey v1"
        private const val INFO_MSG = "ChatApp DR MsgKey v1"

        init {
            AeadConfig.register()
        }

        /**
         * Initialize for the INITIATOR (Alice, who sent the first message).
         * Alice does an immediate DH ratchet step to derive sending chain.
         */
        fun forInitiator(sharedSecret: ByteArray, remoteSpkPublic: ByteArray): DoubleRatchet {
            val secureRandom = SecureRandom()
            val (localPriv, localPub) = generateX25519KeyPair(secureRandom)

            // Initial DH ratchet step: derive new root key + sending chain key
            val dhResult = dhAgreement(localPriv, remoteSpkPublic)
            val (newRootKey, sendingChainKey) = kdfRootKey(sharedSecret, dhResult)

            return DoubleRatchet(
                rootKey = newRootKey,
                remotePubRatchetKey = remoteSpkPublic,
                localRatchetPrivKey = localPriv,
                localRatchetPubKey = localPub,
                sendingChainKey = sendingChainKey,
                receivingChainKey = null,
                sendMessageIndex = 0,
                receiveMessageIndex = 0,
                previousChainLength = 0,
                secureRandom = secureRandom
            )
        }

        /**
         * Initialize for the RESPONDER (Bob, who received the first message).
         * Bob's initial sending chain is not set until he sends his first reply.
         */
        fun forResponder(sharedSecret: ByteArray, localSpkPrivate: ByteArray, localSpkPublic: ByteArray): DoubleRatchet {
            val secureRandom = SecureRandom()
            return DoubleRatchet(
                rootKey = sharedSecret,
                remotePubRatchetKey = ByteArray(0),            // set when first message arrives
                localRatchetPrivKey = localSpkPrivate,
                localRatchetPubKey = localSpkPublic,
                sendingChainKey = null,
                receivingChainKey = null,
                sendMessageIndex = 0,
                receiveMessageIndex = 0,
                previousChainLength = 0,
                secureRandom = secureRandom
            )
        }

        /**
         * X25519 key generation helper.
         */
        private fun generateX25519KeyPair(secureRandom: SecureRandom): Pair<ByteArray, ByteArray> {
            val generator = X25519KeyPairGenerator()
            generator.init(X25519KeyGenerationParameters(secureRandom))
            val keyPair = generator.generateKeyPair()
            val priv = (keyPair.private as X25519PrivateKeyParameters).encoded
            val pub = (keyPair.public as X25519PublicKeyParameters).encoded
            return Pair(priv, pub)
        }

        /**
         * X25519 ECDH agreement.
         */
        private fun dhAgreement(privateKeyBytes: ByteArray, publicKeyBytes: ByteArray): ByteArray {
            val agreement = X25519Agreement()
            agreement.init(X25519PrivateKeyParameters(privateKeyBytes))
            val output = ByteArray(agreement.agreementSize)
            agreement.calculateAgreement(X25519PublicKeyParameters(publicKeyBytes), output, 0)
            return output
        }

        /**
         * KDF_RK: Root Key derivation step using HKDF.
         * Returns (new_root_key, chain_key).
         */
        private fun kdfRootKey(rootKey: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
            val okm = Hkdf.computeHkdf(
                HASH_ALGORITHM,
                dhOutput,
                rootKey,
                INFO_ROOT.toByteArray(Charsets.UTF_8),
                64       // 32 bytes for new root key + 32 bytes for chain key
            )
            return Pair(okm.copyOfRange(0, 32), okm.copyOfRange(32, 64))
        }

        /**
         * KDF_CK: Chain Key step. Returns (new_chain_key, message_key).
         * Uses a simple HMAC-based KDF per the Double Ratchet spec.
         */
        private fun kdfChainKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
            val msgKey = Hkdf.computeHkdf(
                HASH_ALGORITHM,
                chainKey,
                ByteArray(32),
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
            return Pair(newChainKey, msgKey)
        }

        fun fromMap(map: Map<String, String>, secureRandom: SecureRandom = SecureRandom()): DoubleRatchet {
            fun b64(key: String) = Base64.decode(map[key]!!, Base64.NO_WRAP)
            return DoubleRatchet(
                rootKey = b64("rootKey"),
                remotePubRatchetKey = b64("remotePub"),
                localRatchetPrivKey = b64("localPriv"),
                localRatchetPubKey = b64("localPub"),
                sendingChainKey = map["sendChain"]?.takeIf { it.isNotEmpty() }?.let { Base64.decode(it, Base64.NO_WRAP) },
                receivingChainKey = map["recvChain"]?.takeIf { it.isNotEmpty() }?.let { Base64.decode(it, Base64.NO_WRAP) },
                sendMessageIndex = map["sendIdx"]!!.toInt(),
                receiveMessageIndex = map["recvIdx"]!!.toInt(),
                previousChainLength = map["prevLen"]!!.toInt(),
                secureRandom = secureRandom
            )
        }
    }

    /**
     * Encrypt a plaintext message.
     * Automatically performs DH ratchet if needed.
     */
    @Synchronized
    fun encrypt(plaintext: ByteArray): EncryptedPayload {
        // Advance the sending chain key to derive the next message key
        val currentSendChain = sendingChainKey
            ?: error("Sending chain key not initialized. Initiator must call forInitiator(), responder must send at least one DH ratchet step.")

        val (newChainKey, messageKey) = Companion.kdfChainKey(currentSendChain)
        sendingChainKey = newChainKey

        val ciphertext = aeadEncrypt(messageKey, plaintext)
        val index = sendMessageIndex
        sendMessageIndex++

        return EncryptedPayload(
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ratchetKey = Base64.encodeToString(localRatchetPubKey, Base64.NO_WRAP),
            messageIndex = index,
            previousChainLength = previousChainLength
        )
    }

    /**
     * Decrypt an incoming EncryptedPayload.
     * Performs DH ratchet step if the incoming ratchet key is new.
     */
    @Synchronized
    fun decrypt(payload: EncryptedPayload): ByteArray {
        val incomingRatchetKey = Base64.decode(payload.ratchetKey, Base64.NO_WRAP)
        val isNewRatchetKey = !incomingRatchetKey.contentEquals(remotePubRatchetKey)

        if (isNewRatchetKey) {
            // DH Ratchet step — receiving side
            previousChainLength = sendMessageIndex
            receiveMessageIndex = 0

            // Step 1: derive receiving chain key (from Bob's current private, Alice's new public)
            val dhReceive = Companion.dhAgreement(localRatchetPrivKey, incomingRatchetKey)
            val (newRootKey1, receiveChainKey) = Companion.kdfRootKey(rootKey, dhReceive)
            rootKey = newRootKey1
            receivingChainKey = receiveChainKey
            remotePubRatchetKey = incomingRatchetKey

            // Step 2: generate new local ratchet key pair
            val (newLocalPriv, newLocalPub) = Companion.generateX25519KeyPair(secureRandom)
            val dhSend = Companion.dhAgreement(newLocalPriv, incomingRatchetKey)
            val (newRootKey2, sendingChain) = Companion.kdfRootKey(newRootKey1, dhSend)
            rootKey = newRootKey2
            sendingChainKey = sendingChain
            localRatchetPrivKey = newLocalPriv
            localRatchetPubKey = newLocalPub
            sendMessageIndex = 0
        }

        val currentReceiveChain = receivingChainKey
            ?: error("Receiving chain key not established")

        val (newChainKey, messageKey) = Companion.kdfChainKey(currentReceiveChain)
        receivingChainKey = newChainKey
        receiveMessageIndex++

        return aeadDecrypt(messageKey, Base64.decode(payload.ciphertext, Base64.NO_WRAP))
    }

    /**
     * AES-256-GCM encryption using Tink.
     * Prepends the 12-byte random IV to the ciphertext for transport.
     */
    private fun aeadEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val aead = AesGcmJce(key)
        return aead.encrypt(plaintext, null)
    }

    /**
     * AES-256-GCM decryption using Tink.
     */
    private fun aeadDecrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        val aead = AesGcmJce(key)
        return aead.decrypt(ciphertext, null)
    }

    // ---------------------------------------------------------------
    // Serialization: persist ratchet state to EncryptedSharedPreferences
    // ---------------------------------------------------------------

    fun toMap(): Map<String, String> = mapOf(
        "rootKey" to Base64.encodeToString(rootKey, Base64.NO_WRAP),
        "remotePub" to Base64.encodeToString(remotePubRatchetKey, Base64.NO_WRAP),
        "localPriv" to Base64.encodeToString(localRatchetPrivKey, Base64.NO_WRAP),
        "localPub" to Base64.encodeToString(localRatchetPubKey, Base64.NO_WRAP),
        "sendChain" to (sendingChainKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""),
        "recvChain" to (receivingChainKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""),
        "sendIdx" to sendMessageIndex.toString(),
        "recvIdx" to receiveMessageIndex.toString(),
        "prevLen" to previousChainLength.toString()
    )

}

