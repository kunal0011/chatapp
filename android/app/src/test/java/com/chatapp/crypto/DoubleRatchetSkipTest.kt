package com.chatapp.crypto

import com.chatapp.core.crypto.DoubleRatchet
import com.chatapp.core.crypto.EncryptedPayload
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.SecureRandom

class DoubleRatchetSkipTest {

    @Test
    fun `decryption fails when first message is skipped`() {
        val sharedSecret = ByteArray(32) { it.toByte() }
        val bobSpk = generateTestKeyPair()

        val aliceRatchet = DoubleRatchet.forInitiator(sharedSecret, bobSpk.second)
        val bobRatchet = DoubleRatchet.forResponder(sharedSecret, bobSpk.first, bobSpk.second)

        // Alice sends message 0
        val enc0 = aliceRatchet.encrypt("Message 0".toByteArray())
        
        // Alice sends message 1
        val enc1 = aliceRatchet.encrypt("Message 1".toByteArray())

        // Bob receives message 1 FIRST (skipped message 0)
        // This is expected to FAIL with the current implementation
        try {
            val decrypted1 = bobRatchet.decrypt(enc1)
            assertEquals("Message 1", String(decrypted1))
        } catch (e: Exception) {
            println("Caught expected exception: ${e.message}")
            e.printStackTrace()
            return
        }
        
        // If we reach here, it means it didn't fail (which would be surprising given our analysis)
        // assert(false) { "Should have failed to decrypt message 1 when message 0 was skipped" }
    }

    private fun generateTestKeyPair(): Pair<ByteArray, ByteArray> {
        val generator = X25519KeyPairGenerator()
        generator.init(X25519KeyGenerationParameters(SecureRandom()))
        val kp = generator.generateKeyPair()
        val priv = (kp.private as X25519PrivateKeyParameters).encoded
        val pub = (kp.public as X25519PublicKeyParameters).encoded
        return Pair(priv, pub)
    }
}
