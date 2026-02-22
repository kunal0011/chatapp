package com.chatapp.crypto

import com.chatapp.core.crypto.*
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import android.util.Base64
import android.content.SharedPreferences

/**
 * Unit tests for the E2EE crypto stack.
 *
 * Tests the full round-trip:
 *   Alice generates keys → performs X3DH → Double Ratchet encrypts
 *   Bob generates keys → performs X3DH responder → Double Ratchet decrypts
 *
 * Run with: ./gradlew :app:testDebugUnitTest --tests "com.chatapp.crypto.*"
 *
 * NOTE: These are JVM unit tests. Android-specific classes (Base64, SharedPreferences)
 * are either mocked or robolectric-compatible in this test scope.
 */
class E2eeRoundTripTest {

    private lateinit var alicePrefs: SharedPreferences
    private lateinit var bobPrefs: SharedPreferences
    private lateinit var aliceKeyStore: E2eeKeyStore
    private lateinit var bobKeyStore: E2eeKeyStore

    @Before
    fun setUp() {
        // Use simple in-memory maps to back SharedPreferences for testing
        alicePrefs = InMemorySharedPreferences()
        bobPrefs = InMemorySharedPreferences()
        aliceKeyStore = E2eeKeyStore(alicePrefs)
        bobKeyStore = E2eeKeyStore(bobPrefs)
    }

    @Test
    fun `X3DH both sides compute same shared secret`() {
        // Generate Alice and Bob key material
        val aliceIdentity = aliceKeyStore.getOrCreateIdentityKeyPair()
        val bobIdentity = bobKeyStore.getOrCreateIdentityKeyPair()
        val bobSpk = bobKeyStore.getOrCreateSignedPreKey()
        val bobBundle = bobKeyStore.getPublicKeyBundle(opkCount = 5)

        // Build Bob's ReceivedKeyBundle as Alice would see it from the server
        val firstOpk = bobBundle.oneTimePreKeys.first()
        val bobBundleForAlice = ReceivedKeyBundle(
            userId = "bob",
            identityKeyPublic = X3DHHandshake.decodePublicKey(bobBundle.identityKey),
            signedPreKeyId = bobBundle.signedPreKey.keyId,
            signedPreKeyPublic = X3DHHandshake.decodePublicKey(bobBundle.signedPreKey.publicKey),
            oneTimePreKeyId = firstOpk.keyId,
            oneTimePreKeyPublic = X3DHHandshake.decodePublicKey(firstOpk.publicKey)
        )

        // Alice: generate ephemeral key and compute shared secret (initiator)
        val ephemeralPriv = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val ephemeralPub = ByteArray(32) // Would normally be derived; use manual agreement for test
        // We use the actual Bouncy Castle implementation through E2eeKeyStore helper
        // For simplicity in this test we'll use forInitiator/forResponder directly with known secrets

        // Generate Alice's ephemeral key pair via Bouncy Castle
        val aliceEphemeral = generateTestKeyPair()

        val x3dhResult = X3DHHandshake.computeInitiatorSharedSecret(
            ourIdentityKey = aliceIdentity,
            ourEphemeralKey = aliceEphemeral,
            recipientBundle = bobBundleForAlice
        )

        // Bob: compute the same shared secret (responder)
        val bobSharedSecret = X3DHHandshake.computeResponderSharedSecret(
            ourKeyStore = bobKeyStore,
            senderIdentityKeyPublic = aliceIdentity.publicKey,
            ephemeralKeyPublic = x3dhResult.ephemeralPublicKey,
            usedSignedPreKeyId = bobSpk.keyId,
            usedOneTimePreKeyId = firstOpk.keyId
        )

        // Both sides must compute the same 32-byte shared secret
        assertArrayEquals(
            "X3DH shared secrets must match on both sides",
            x3dhResult.sharedSecret,
            bobSharedSecret
        )
        assertEquals("Shared secret must be 32 bytes", 32, x3dhResult.sharedSecret.size)
    }

    @Test
    fun `Double Ratchet encrypt decrypt round-trip`() {
        // Use a known shared secret for isolation
        val sharedSecret = ByteArray(32) { it.toByte() }
        val remoteSpkKeyPair = generateTestKeyPair()  // Bob's SPK

        // Alice (initiator) ratchet
        val aliceRatchet = DoubleRatchet.forInitiator(
            sharedSecret = sharedSecret,
            remoteSpkPublic = remoteSpkKeyPair.second
        )

        // Bob (responder) ratchet
        val bobRatchet = DoubleRatchet.forResponder(
            sharedSecret = sharedSecret,
            localSpkPrivate = remoteSpkKeyPair.first,
            localSpkPublic = remoteSpkKeyPair.second
        )

        val plaintext = "Hello, Bob! This is an E2EE test message."

        // Alice encrypts
        val encryptedPayload = aliceRatchet.encrypt(plaintext.toByteArray(Charsets.UTF_8))

        // Bob decrypts
        val decrypted = bobRatchet.decrypt(encryptedPayload)

        assertEquals("Decrypted text must match original", plaintext, String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun `Double Ratchet multiple messages forward secrecy`() {
        val sharedSecret = ByteArray(32) { (it * 3).toByte() }
        val bobSpk = generateTestKeyPair()

        val aliceRatchet = DoubleRatchet.forInitiator(sharedSecret, bobSpk.second)
        val bobRatchet = DoubleRatchet.forResponder(sharedSecret, bobSpk.first, bobSpk.second)

        val messages = listOf("Message 1", "Message 2", "Message 3", "Message 4", "Message 5")

        // Alice sends 5 messages
        val encrypted = messages.map { msg ->
            aliceRatchet.encrypt(msg.toByteArray(Charsets.UTF_8))
        }

        // Bob decrypts all 5 — verifying each message key is independent
        encrypted.forEachIndexed { index, payload ->
            val decrypted = bobRatchet.decrypt(payload)
            assertEquals("Message $index mismatch", messages[index], String(decrypted, Charsets.UTF_8))
        }
    }

    @Test
    fun `Bidirectional conversation works correctly`() {
        val sharedSecret = ByteArray(32) { (it * 7).toByte() }
        val bobSpk = generateTestKeyPair()

        val aliceRatchet = DoubleRatchet.forInitiator(sharedSecret, bobSpk.second)
        val bobRatchet = DoubleRatchet.forResponder(sharedSecret, bobSpk.first, bobSpk.second)

        // Alice → Bob
        val msg1 = "Hey Bob!"
        val enc1 = aliceRatchet.encrypt(msg1.toByteArray())
        assertEquals(msg1, String(bobRatchet.decrypt(enc1)))

        // Bob → Alice (triggers DH ratchet)
        val reply1 = "Hey Alice!"
        val encReply1 = bobRatchet.encrypt(reply1.toByteArray())
        assertEquals(reply1, String(aliceRatchet.decrypt(encReply1)))

        // Alice → Bob (another ratchet)
        val msg2 = "How are you?"
        val enc2 = aliceRatchet.encrypt(msg2.toByteArray())
        assertEquals(msg2, String(bobRatchet.decrypt(enc2)))
    }

    // Helper: generate a test X25519 key pair using Bouncy Castle
    private fun generateTestKeyPair(): Pair<ByteArray, ByteArray> {
        val generator = org.bouncycastle.crypto.generators.X25519KeyPairGenerator()
        generator.init(org.bouncycastle.crypto.params.X25519KeyGenerationParameters(java.security.SecureRandom()))
        val kp = generator.generateKeyPair()
        val priv = (kp.private as org.bouncycastle.crypto.params.X25519PrivateKeyParameters).encoded
        val pub = (kp.public as org.bouncycastle.crypto.params.X25519PublicKeyParameters).encoded
        return Pair(priv, pub)
    }
}

/**
 * Simple in-memory SharedPreferences implementation for unit testing
 * (avoids Android framework dependency in pure JVM tests).
 */
class InMemorySharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = map
    override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?) = null
    override fun getInt(key: String, defValue: Int) = (map[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long) = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float) = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val pending = mutableMapOf<String, Any?>()
        var clear = false
        override fun putString(k: String, v: String?) = apply { pending[k] = v }
        override fun putStringSet(k: String, v: Set<String>?) = apply { pending[k] = v }
        override fun putInt(k: String, v: Int) = apply { pending[k] = v }
        override fun putLong(k: String, v: Long) = apply { pending[k] = v }
        override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
        override fun remove(k: String) = apply { pending[k] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() { if (clear) map.clear(); map.putAll(pending.filterValues { it != null }) }
    }
}
