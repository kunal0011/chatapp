package com.chatapp.core.crypto

import android.util.Base64
import com.google.crypto.tink.subtle.Hkdf
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

/**
 * Data returned to the sender after X3DH initiation.
 * The ephemeralPublicKey must be included in the first message so the receiver
 * can compute the same shared secret.
 */
data class X3DHResult(
    val sharedSecret: ByteArray,      // 32-byte root key seed
    val ephemeralPublicKey: ByteArray  // base64-able; must be sent alongside first message
) {
    override fun equals(other: Any?) = other is X3DHResult &&
            sharedSecret.contentEquals(other.sharedSecret) &&
            ephemeralPublicKey.contentEquals(other.ephemeralPublicKey)
    override fun hashCode() = 31 * sharedSecret.contentHashCode() + ephemeralPublicKey.contentHashCode()
}

/**
 * Received key bundle from the server when Alice wants to message Bob.
 */
data class ReceivedKeyBundle(
    val userId: String,
    val identityKeyPublic: ByteArray,    // IK_B
    val signedPreKeyId: Int,
    val signedPreKeyPublic: ByteArray,   // SPK_B
    val oneTimePreKeyId: Int?,           // null if exhausted
    val oneTimePreKeyPublic: ByteArray?  // OPK_B (null if exhausted)
)

/**
 * X3DH (Extended Triple Diffie-Hellman) handshake implementation.
 *
 * Used to establish a shared secret between two parties asynchronously —
 * Alice can send to offline Bob by consuming a one-time prekey.
 *
 * Protocol:
 *   DH1 = X25519(IK_A_priv, SPK_B_pub)
 *   DH2 = X25519(EK_A_priv, IK_B_pub)
 *   DH3 = X25519(EK_A_priv, SPK_B_pub)
 *   DH4 = X25519(EK_A_priv, OPK_B_pub)  [only if OPK present]
 *   SK  = HKDF(DH1 || DH2 || DH3 [|| DH4])
 *
 * Reference: https://signal.org/docs/specifications/x3dh/
 */
object X3DHHandshake {

    private const val HASH_ALGORITHM = "HmacSHA256"
    private const val INFO_STRING = "ChatApp X3DH v1"
    private const val KEY_LENGTH = 32

    /**
     * Compute X25519 DH agreement between a private key and a public key.
     */
    private fun dh(privateKeyBytes: ByteArray, publicKeyBytes: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        val privateKey = X25519PrivateKeyParameters(privateKeyBytes)
        val publicKey = X25519PublicKeyParameters(publicKeyBytes)
        agreement.init(privateKey)
        val output = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(publicKey, output, 0)
        return output
    }

    /**
     * Called by the INITIATOR (Alice) to establish a new session with Bob.
     *
     * @param ourIdentityKey Alice's long-term identity key pair (X25519)
     * @param recipientBundle Bob's public key bundle from the server
     * @return X3DHResult containing the shared secret root key + ephemeral public key
     */
    fun computeInitiatorSharedSecret(
        ourIdentityKey: IdentityKeyPair,
        ourEphemeralKey: Pair<ByteArray, ByteArray>,  // (private, public)
        recipientBundle: ReceivedKeyBundle
    ): X3DHResult {
        // DH1 = DH(IK_A, SPK_B)
        val dh1 = dh(ourIdentityKey.privateKey, recipientBundle.signedPreKeyPublic)
        // DH2 = DH(EK_A, IK_B)
        val dh2 = dh(ourEphemeralKey.first, recipientBundle.identityKeyPublic)
        // DH3 = DH(EK_A, SPK_B)
        val dh3 = dh(ourEphemeralKey.first, recipientBundle.signedPreKeyPublic)

        val dhMaterial = if (recipientBundle.oneTimePreKeyPublic != null) {
            // DH4 = DH(EK_A, OPK_B) — stronger security with one-time prekey
            val dh4 = dh(ourEphemeralKey.first, recipientBundle.oneTimePreKeyPublic)
            dh1 + dh2 + dh3 + dh4
        } else {
            // OPKs exhausted — still secure but without one-time forward secrecy for this session
            dh1 + dh2 + dh3
        }

        val sharedSecret = deriveKey(dhMaterial)

        android.util.Log.d("X3DHHandshake", "=== INITIATOR X3DH ===")
        android.util.Log.d("X3DHHandshake", "Alice IK: ${encodePublicKey(ourIdentityKey.publicKey)}")
        android.util.Log.d("X3DHHandshake", "Alice EK: ${encodePublicKey(ourEphemeralKey.second)}")
        android.util.Log.d("X3DHHandshake", "Bob IK: ${encodePublicKey(recipientBundle.identityKeyPublic)}")
        android.util.Log.d("X3DHHandshake", "Bob SPK: ${encodePublicKey(recipientBundle.signedPreKeyPublic)}")
        android.util.Log.d("X3DHHandshake", "Bob OPK: ${recipientBundle.oneTimePreKeyPublic?.let { encodePublicKey(it) }}")
        android.util.Log.d("X3DHHandshake", "Final SK: ${encodePublicKey(sharedSecret)}")

        return X3DHResult(
            sharedSecret = sharedSecret,
            ephemeralPublicKey = ourEphemeralKey.second
        )
    }

    /**
     * Called by the RESPONDER (Bob) upon receiving Alice's first message.
     *
     * @param ourKeyStore Bob's local key store to look up private keys
     * @param senderIdentityKeyPublic Alice's IK_A public key (from message header)
     * @param ephemeralKeyPublic Alice's EK_A public key (from message header)
     * @param usedSignedPreKeyId The SPK key ID Alice used
     * @param usedOneTimePreKeyId The OPK key ID Alice used (null if none)
     * @return The same shared secret SK that Alice computed
     */
    fun computeResponderSharedSecret(
        ourKeyStore: E2eeKeyStore,
        senderIdentityKeyPublic: ByteArray,
        ephemeralKeyPublic: ByteArray,
        usedSignedPreKeyId: Int,
        usedOneTimePreKeyId: Int?
    ): ByteArray {
        val ourIdentity = ourKeyStore.getOrCreateIdentityKeyPair()
        val ourSpk = ourKeyStore.getOrCreateSignedPreKey()
        check(ourSpk.keyId == usedSignedPreKeyId) {
            "SignedPreKey ID mismatch: expected ${ourSpk.keyId}, got $usedSignedPreKeyId"
        }

        // DH1 = DH(SPK_B, IK_A)
        val dh1 = dh(ourSpk.privateKey, senderIdentityKeyPublic)
        // DH2 = DH(IK_B, EK_A)
        val dh2 = dh(ourIdentity.privateKey, ephemeralKeyPublic)
        // DH3 = DH(SPK_B, EK_A)
        val dh3 = dh(ourSpk.privateKey, ephemeralKeyPublic)

        val dhMaterial = if (usedOneTimePreKeyId != null) {
            val opkPrivate = ourKeyStore.getOneTimePreKeyPrivate(usedOneTimePreKeyId)
                ?: error("OPK with id $usedOneTimePreKeyId not found. Was it already consumed?")
            // DH4 = DH(OPK_B, EK_A)
            val dh4 = dh(opkPrivate, ephemeralKeyPublic)
            dh1 + dh2 + dh3 + dh4
        } else {
            dh1 + dh2 + dh3
        }

        val sharedSecret = deriveKey(dhMaterial)

        android.util.Log.d("X3DHHandshake", "=== RESPONDER X3DH ===")
        android.util.Log.d("X3DHHandshake", "Alice IK: ${encodePublicKey(senderIdentityKeyPublic)}")
        android.util.Log.d("X3DHHandshake", "Alice EK: ${encodePublicKey(ephemeralKeyPublic)}")
        android.util.Log.d("X3DHHandshake", "Bob IK: ${encodePublicKey(ourIdentity.publicKey)}")
        android.util.Log.d("X3DHHandshake", "Bob SPK: ${encodePublicKey(ourSpk.publicKey)}")
        android.util.Log.d("X3DHHandshake", "Bob OPK Used ID: $usedOneTimePreKeyId")
        android.util.Log.d("X3DHHandshake", "Final SK: ${encodePublicKey(sharedSecret)}")

        return sharedSecret
    }

    /**
     * HKDF derivation: takes concatenated DH outputs → 32-byte root key.
     * Uses an all-zero salt and app-specific info string as per X3DH spec.
     */
    private fun deriveKey(inputKeyMaterial: ByteArray): ByteArray {
        // F = 32 zero bytes (X3DH requires prepending a bytestring of 0xFF bytes of curve-type length)
        val f = ByteArray(32) { 0xFF.toByte() }
        val saltedInput = f + inputKeyMaterial

        return Hkdf.computeHkdf(
            HASH_ALGORITHM,
            saltedInput,
            ByteArray(32),   // salt: 32 zero bytes
            INFO_STRING.toByteArray(Charsets.UTF_8),
            KEY_LENGTH
        )
    }

    /**
     * Encode public key bytes to base64 string for wire transfer.
     */
    fun encodePublicKey(keyBytes: ByteArray): String =
        Base64.encodeToString(keyBytes, Base64.NO_WRAP)

    /**
     * Decode a base64 public key from wire format.
     */
    fun decodePublicKey(base64Key: String): ByteArray =
        Base64.decode(base64Key, Base64.NO_WRAP)
}
