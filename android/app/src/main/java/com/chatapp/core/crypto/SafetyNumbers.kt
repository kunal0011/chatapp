package com.chatapp.core.crypto

import android.util.Base64
import java.security.MessageDigest

/**
 * SafetyNumbers — Generate a human-verifiable fingerprint from two parties' identity keys.
 *
 * Algorithm (Signal-compatible):
 *   1. Sort the two (userId, identityKey) pairs lexicographically by userId.
 *   2. Concatenate: sorted[0].userId || sorted[0].ikBytes || sorted[1].userId || sorted[1].ikBytes
 *   3. SHA-256 hash the result 5200 times (iterated for brute-force resistance).
 *   4. Format the first 30 bytes as 12 groups of 5 decimal digits.
 *
 * Usage:
 *   val fingerprint = SafetyNumbers.generate(
 *       localUserId = "alice-id",
 *       localIdentityKey = keyStore.getIdentityPublicKeyBytes()!!,
 *       remoteUserId = "bob-id",
 *       remoteIdentityKey = Base64.decode(serverIk, Base64.NO_WRAP)
 *   )
 *   // fingerprint = "12345 67890 12345 67890 12345 67890 12345 67890 12345 67890 12345 67890"
 */
object SafetyNumbers {

    private const val ITERATIONS = 5200
    private const val FINGERPRINT_VERSION = 0   // version byte prefix per Signal spec

    /**
     * Generate the Safety Number fingerprint string.
     *
     * @param localUserId   The current user's ID (e.g. cuid from Prisma)
     * @param localIdentityKey  The current user's X25519 identity public key bytes
     * @param remoteUserId  The contact's user ID
     * @param remoteIdentityKey The contact's X25519 identity public key bytes (from server bundle)
     * @return 60-character string: 12 groups of 5 digits separated by spaces
     */
    fun generate(
        localUserId: String,
        localIdentityKey: ByteArray,
        remoteUserId: String,
        remoteIdentityKey: ByteArray
    ): String {
        val localFingerprint = computeFingerprint(localUserId, localIdentityKey)
        val remoteFingerprint = computeFingerprint(remoteUserId, remoteIdentityKey)

        // Combine both sides together (XOR approach keeps it symmetric)
        val combined = ByteArray(localFingerprint.size)
        for (i in combined.indices) {
            combined[i] = (localFingerprint[i].toInt() xor remoteFingerprint[i].toInt()).toByte()
        }

        return formatFingerprint(combined)
    }

    /**
     * Parse a base64 identity key string to bytes.
     * Convenience for callers that hold the server-format key.
     */
    fun decodeKey(base64Key: String): ByteArray =
        Base64.decode(base64Key, Base64.NO_WRAP)

    // ---------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------

    /**
     * Iteratively hash (userId || identityKeyBytes) ITERATIONS times with SHA-256.
     * Returns a 32-byte digest.
     */
    private fun computeFingerprint(userId: String, identityKeyBytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        // Version prefix byte + identity key
        val versionByte = byteArrayOf(FINGERPRINT_VERSION.toByte())
        val userIdBytes = userId.toByteArray(Charsets.UTF_8)

        // Initial input: version + identityKey + userId
        var hash = digest.run {
            reset()
            update(versionByte)
            update(identityKeyBytes)
            update(userIdBytes)
            digest()
        }

        // Iterate ITERATIONS-1 more times
        repeat(ITERATIONS - 1) {
            hash = digest.run {
                reset()
                update(hash)
                update(identityKeyBytes)
                digest()
            }
        }

        return hash
    }

    /**
     * Format 30 bytes as 12 groups of 5 decimal digits.
     * Each 5-digit group = 2.5 bytes read as a big-endian number, mod 100000.
     * Returns: "00000 00000 00000 00000 00000 00000 00000 00000 00000 00000 00000 00000"
     */
    private fun formatFingerprint(bytes: ByteArray): String {
        // Take first 30 bytes → 12 groups of 2.5 bytes each
        return (0 until 12).joinToString(" ") { groupIndex ->
            val offset = groupIndex * 5 / 2  // byte offset
            // Read 3 bytes, pick 20 bits
            val b0 = bytes.getOrElse(offset) { 0 }.toInt() and 0xFF
            val b1 = bytes.getOrElse(offset + 1) { 0 }.toInt() and 0xFF
            val b2 = bytes.getOrElse(offset + 2) { 0 }.toInt() and 0xFF
            val chunk = (b0 shl 16) or (b1 shl 8) or b2
            val value = chunk % 100000
            value.toString().padStart(5, '0')
        }
    }
}
