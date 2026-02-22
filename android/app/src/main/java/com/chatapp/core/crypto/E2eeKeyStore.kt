package com.chatapp.core.crypto

import android.content.SharedPreferences
import android.util.Base64
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data classes representing key material
 */
data class IdentityKeyPair(
    val privateKey: ByteArray,  // X25519 private key bytes
    val publicKey: ByteArray    // X25519 public key bytes
) {
    override fun equals(other: Any?) = other is IdentityKeyPair &&
            privateKey.contentEquals(other.privateKey) &&
            publicKey.contentEquals(other.publicKey)
    override fun hashCode() = 31 * privateKey.contentHashCode() + publicKey.contentHashCode()
}

data class SignedPreKeyRecord(
    val keyId: Int,
    val privateKey: ByteArray,  // X25519 private key
    val publicKey: ByteArray,   // X25519 public key
    val signature: ByteArray    // Ed25519 signature of the public key
)

data class OneTimePreKeyRecord(
    val keyId: Int,
    val privateKey: ByteArray,  // X25519 private key
    val publicKey: ByteArray    // X25519 public key
)

data class PublicKeyBundle(
    val identityKey: String,      // base64 X25519 public key
    val signedPreKey: SignedPreKeyInfo,
    val oneTimePreKeys: List<OneTimePreKeyInfo>
)

data class SignedPreKeyInfo(
    val keyId: Int,
    val publicKey: String,    // base64
    val signature: String     // base64
)

data class OneTimePreKeyInfo(
    val keyId: Int,
    val publicKey: String     // base64
)

/**
 * E2eeKeyStore: Manages all cryptographic key material on-device.
 *
 * - Identity keys (long-term X25519) stored in EncryptedSharedPreferences (Android Keystore backed)
 * - Signed prekeys and one-time prekeys similarly stored encrypted on-device
 * - Private keys NEVER leave the device
 */
@Singleton
class E2eeKeyStore @Inject constructor(
    private val encryptedPrefs: SharedPreferences
) {
    companion object {
        private const val KEY_IDENTITY_PRIVATE = "e2ee_ik_private"
        private const val KEY_IDENTITY_PUBLIC = "e2ee_ik_public"
        private const val KEY_SIGNING_PRIVATE = "e2ee_signing_private"
        private const val KEY_SIGNING_PUBLIC = "e2ee_signing_public"
        private const val KEY_SPK_ID = "e2ee_spk_id"
        private const val KEY_SPK_PRIVATE = "e2ee_spk_private"
        private const val KEY_SPK_PUBLIC = "e2ee_spk_public"
        private const val KEY_SPK_SIGNATURE = "e2ee_spk_signature"
        private const val KEY_OPK_COUNT = "e2ee_opk_count"
        private const val KEY_OPK_PRIVATE_PREFIX = "e2ee_opk_private_"
        private const val KEY_OPK_PUBLIC_PREFIX = "e2ee_opk_public_"
        private const val OPK_BATCH_SIZE = 10
    }

    private val secureRandom = SecureRandom()

    /**
     * Generate a Curve25519 (X25519) key pair for use in ECDH.
     */
    private fun generateX25519KeyPair(): Pair<ByteArray, ByteArray> {
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(secureRandom))
        val keyPair = generator.generateKeyPair()
        val privateKey = (keyPair.private as X25519PrivateKeyParameters).encoded
        val publicKey = (keyPair.public as X25519PublicKeyParameters).encoded
        return Pair(privateKey, publicKey)
    }

    /**
     * Generate an Ed25519 key pair used for signing prekeys.
     */
    private fun generateEd25519KeyPair(): Pair<ByteArray, ByteArray> {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(secureRandom))
        val keyPair = generator.generateKeyPair()
        val privateKey = (keyPair.private as Ed25519PrivateKeyParameters).encoded
        val publicKey = (keyPair.public as Ed25519PublicKeyParameters).encoded
        return Pair(privateKey, publicKey)
    }

    /**
     * Sign data with an Ed25519 private key.
     */
    private fun sign(privateKeyBytes: ByteArray, data: ByteArray): ByteArray {
        val privateKey = Ed25519PrivateKeyParameters(privateKeyBytes)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    /**
     * Get or create this device's long-term identity key pair.
     * On first call, generates and persists to EncryptedSharedPreferences.
     */
    fun getOrCreateIdentityKeyPair(): IdentityKeyPair {
        val existingPriv = encryptedPrefs.getString(KEY_IDENTITY_PRIVATE, null)
        val existingPub = encryptedPrefs.getString(KEY_IDENTITY_PUBLIC, null)

        if (existingPriv != null && existingPub != null) {
            return IdentityKeyPair(
                privateKey = Base64.decode(existingPriv, Base64.NO_WRAP),
                publicKey = Base64.decode(existingPub, Base64.NO_WRAP)
            )
        }

        val (privBytes, pubBytes) = generateX25519KeyPair()
        // Also generate a long-term Ed25519 signing key pair for SPK signatures
        val (sigPriv, sigPub) = generateEd25519KeyPair()

        encryptedPrefs.edit()
            .putString(KEY_IDENTITY_PRIVATE, Base64.encodeToString(privBytes, Base64.NO_WRAP))
            .putString(KEY_IDENTITY_PUBLIC, Base64.encodeToString(pubBytes, Base64.NO_WRAP))
            .putString(KEY_SIGNING_PRIVATE, Base64.encodeToString(sigPriv, Base64.NO_WRAP))
            .putString(KEY_SIGNING_PUBLIC, Base64.encodeToString(sigPub, Base64.NO_WRAP))
            .apply()

        return IdentityKeyPair(privateKey = privBytes, publicKey = pubBytes)
    }

    /**
     * Get or create the signed prekey.
     * The signed prekey is a medium-term X25519 key pair signed by the Ed25519 signing key.
     */
    fun getOrCreateSignedPreKey(): SignedPreKeyRecord {
        val existingId = encryptedPrefs.getInt(KEY_SPK_ID, -1)
        val existingPriv = encryptedPrefs.getString(KEY_SPK_PRIVATE, null)
        val existingPub = encryptedPrefs.getString(KEY_SPK_PUBLIC, null)
        val existingSig = encryptedPrefs.getString(KEY_SPK_SIGNATURE, null)

        if (existingId >= 0 && existingPriv != null && existingPub != null && existingSig != null) {
            return SignedPreKeyRecord(
                keyId = existingId,
                privateKey = Base64.decode(existingPriv, Base64.NO_WRAP),
                publicKey = Base64.decode(existingPub, Base64.NO_WRAP),
                signature = Base64.decode(existingSig, Base64.NO_WRAP)
            )
        }

        val signingPrivStr = encryptedPrefs.getString(KEY_SIGNING_PRIVATE, null)
            ?: error("Identity key must exist before creating signed prekey. Call getOrCreateIdentityKeyPair() first.")

        val (privBytes, pubBytes) = generateX25519KeyPair()
        val signingPrivKey = Base64.decode(signingPrivStr, Base64.NO_WRAP)
        val signature = sign(signingPrivKey, pubBytes)
        val keyId = 1

        encryptedPrefs.edit()
            .putInt(KEY_SPK_ID, keyId)
            .putString(KEY_SPK_PRIVATE, Base64.encodeToString(privBytes, Base64.NO_WRAP))
            .putString(KEY_SPK_PUBLIC, Base64.encodeToString(pubBytes, Base64.NO_WRAP))
            .putString(KEY_SPK_SIGNATURE, Base64.encodeToString(signature, Base64.NO_WRAP))
            .apply()

        return SignedPreKeyRecord(keyId = keyId, privateKey = privBytes, publicKey = pubBytes, signature = signature)
    }

    /**
     * Generate a batch of one-time prekeys and store them locally.
     * Returns the new records for upload to the server.
     */
    fun generateOneTimePreKeys(count: Int = OPK_BATCH_SIZE): List<OneTimePreKeyRecord> {
        val currentCount = encryptedPrefs.getInt(KEY_OPK_COUNT, 0)
        val records = mutableListOf<OneTimePreKeyRecord>()
        val editor = encryptedPrefs.edit()

        for (i in 0 until count) {
            val keyId = currentCount + i
            val (privBytes, pubBytes) = generateX25519KeyPair()
            editor.putString("$KEY_OPK_PRIVATE_PREFIX$keyId", Base64.encodeToString(privBytes, Base64.NO_WRAP))
            editor.putString("$KEY_OPK_PUBLIC_PREFIX$keyId", Base64.encodeToString(pubBytes, Base64.NO_WRAP))
            records.add(OneTimePreKeyRecord(keyId = keyId, privateKey = privBytes, publicKey = pubBytes))
        }

        editor.putInt(KEY_OPK_COUNT, currentCount + count)
        editor.apply()
        return records
    }

    /**
     * Retrieve a one-time prekey private key by keyId (for X3DH responder computation).
     * Returns null if the keyId is unknown.
     */
    fun getOneTimePreKeyPrivate(keyId: Int): ByteArray? {
        val str = encryptedPrefs.getString("$KEY_OPK_PRIVATE_PREFIX$keyId", null) ?: return null
        return Base64.decode(str, Base64.NO_WRAP)
    }

    /**
     * Retrieve the signing (Ed25519) public key — used for SPK signature verification.
     */
    fun getSigningPublicKey(): ByteArray? {
        val str = encryptedPrefs.getString(KEY_SIGNING_PUBLIC, null) ?: return null
        return Base64.decode(str, Base64.NO_WRAP)
    }

    /**
     * Build and return the public key bundle to upload to the server on registration.
     * Lazily creates identity + signed prekey if not yet present.
     * @param opkCount number of one-time prekeys to generate for initial upload
     */
    fun getPublicKeyBundle(opkCount: Int = OPK_BATCH_SIZE): PublicKeyBundle {
        val identityPair = getOrCreateIdentityKeyPair()
        val spk = getOrCreateSignedPreKey()
        val opks = generateOneTimePreKeys(opkCount)

        return PublicKeyBundle(
            identityKey = Base64.encodeToString(identityPair.publicKey, Base64.NO_WRAP),
            signedPreKey = SignedPreKeyInfo(
                keyId = spk.keyId,
                publicKey = Base64.encodeToString(spk.publicKey, Base64.NO_WRAP),
                signature = Base64.encodeToString(spk.signature, Base64.NO_WRAP)
            ),
            oneTimePreKeys = opks.map { opk ->
                OneTimePreKeyInfo(keyId = opk.keyId, publicKey = Base64.encodeToString(opk.publicKey, Base64.NO_WRAP))
            }
        )
    }

    /**
     * Check if this device has ever had keys generated.
     */
    fun hasKeys(): Boolean = encryptedPrefs.getString(KEY_IDENTITY_PRIVATE, null) != null

    /**
     * Return the existing public key bundle (already-generated keys only, no new OPK generation).
     * Used to re-upload to the server after a server-side data loss without burning local OPKs.
     * Returns null if keys have never been generated.
     */
    fun getExistingPublicBundle(): PublicKeyBundle? {
        val identityPub = encryptedPrefs.getString(KEY_IDENTITY_PUBLIC, null) ?: return null
        val spkId = encryptedPrefs.getInt(KEY_SPK_ID, -1).takeIf { it >= 0 } ?: return null
        val spkPub = encryptedPrefs.getString(KEY_SPK_PUBLIC, null) ?: return null
        val spkSig = encryptedPrefs.getString(KEY_SPK_SIGNATURE, null) ?: return null

        // Collect any existing OPKs still stored locally
        val opkCount = encryptedPrefs.getInt(KEY_OPK_COUNT, 0)
        val existingOPKs = (0 until opkCount).mapNotNull { keyId ->
            val pub = encryptedPrefs.getString("$KEY_OPK_PUBLIC_PREFIX$keyId", null) ?: return@mapNotNull null
            OneTimePreKeyInfo(keyId = keyId, publicKey = pub)
        }

        return PublicKeyBundle(
            identityKey = identityPub,
            signedPreKey = SignedPreKeyInfo(keyId = spkId, publicKey = spkPub, signature = spkSig),
            oneTimePreKeys = existingOPKs
        )
    }
}
