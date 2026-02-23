package com.chatapp.core.crypto

import com.google.gson.annotations.SerializedName

/**
 * Wire format for a SenderKey-encrypted group message.
 *
 * Sent as JSON inside the `content` field of an encrypted group message.
 * Receivers parse this to identify the sender's chain iteration and verify the signature
 * before decryption.
 */
data class SenderKeyMessage(
    @SerializedName("groupId") val groupId: String,
    @SerializedName("senderUserId") val senderUserId: String,
    @SerializedName("ciphertext") val ciphertext: String,       // base64 AES-GCM ciphertext
    @SerializedName("iteration") val iteration: Int,            // chain ratchet step
    @SerializedName("signature") val signature: String           // base64 Ed25519 signature over ciphertext
)

/**
 * SenderKey state for one participant in a group conversation.
 *
 * - For OWN keys: `signingKeyPrivate` is non-null (we sign outgoing messages).
 * - For RECEIVED keys: `signingKeyPrivate` is null (we only verify incoming).
 */
data class SenderKeyState(
    val groupId: String,
    val senderUserId: String,
    val chainKey: ByteArray,              // 32-byte symmetric chain key (ratchets per message)
    val signingKeyPublic: ByteArray,      // Ed25519 verification key
    val signingKeyPrivate: ByteArray?,    // Ed25519 signing key (null for received keys)
    val iteration: Int = 0                // current message counter
) {
    /** Serializable envelope for 1:1 E2EE distribution (private key is NEVER distributed). */
    fun toDistribution(): SenderKeyDistribution = SenderKeyDistribution(
        groupId = groupId,
        senderUserId = senderUserId,
        chainKey = android.util.Base64.encodeToString(chainKey, android.util.Base64.NO_WRAP),
        signingKeyPublic = android.util.Base64.encodeToString(signingKeyPublic, android.util.Base64.NO_WRAP),
        iteration = iteration
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SenderKeyState) return false
        return groupId == other.groupId && senderUserId == other.senderUserId &&
                chainKey.contentEquals(other.chainKey) && iteration == other.iteration
    }

    override fun hashCode(): Int = arrayOf(groupId, senderUserId, iteration).contentHashCode()
}

/**
 * Serializable SenderKey distribution payload — this is what gets encrypted with
 * the 1:1 E2EE session and sent to each group member.
 *
 * Does NOT include the signing private key (only the sender keeps that).
 */
data class SenderKeyDistribution(
    @SerializedName("groupId") val groupId: String,
    @SerializedName("senderUserId") val senderUserId: String,
    @SerializedName("chainKey") val chainKey: String,           // base64
    @SerializedName("signingKeyPublic") val signingKeyPublic: String,  // base64
    @SerializedName("iteration") val iteration: Int = 0
) {
    fun toState(): SenderKeyState = SenderKeyState(
        groupId = groupId,
        senderUserId = senderUserId,
        chainKey = android.util.Base64.decode(chainKey, android.util.Base64.NO_WRAP),
        signingKeyPublic = android.util.Base64.decode(signingKeyPublic, android.util.Base64.NO_WRAP),
        signingKeyPrivate = null,  // receiver never gets the signing private key
        iteration = iteration
    )
}
