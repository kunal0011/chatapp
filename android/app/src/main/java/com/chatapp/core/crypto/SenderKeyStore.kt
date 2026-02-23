package com.chatapp.core.crypto

import android.content.SharedPreferences
import android.util.Base64
import com.chatapp.core.di.E2eePrefs
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SenderKeyStore — Persists SenderKey states in EncryptedSharedPreferences.
 *
 * Key format: `sender_key_{groupId}_{senderUserId}` → JSON-serialized SenderKeyState.
 * Private signing key bytes are base64-encoded for JSON storage.
 */
@Singleton
class SenderKeyStore @Inject constructor(
    @E2eePrefs private val prefs: SharedPreferences
) {
    companion object {
        private const val PREFIX = "sender_key_"
    }

    private val gson = Gson()

    // -- Persistence Model (JSON-safe) --

    private data class StoredSenderKey(
        val groupId: String,
        val senderUserId: String,
        val chainKey: String,            // base64
        val signingKeyPublic: String,    // base64
        val signingKeyPrivate: String?,  // base64 (null for received keys)
        val iteration: Int
    )

    private fun SenderKeyState.toStored() = StoredSenderKey(
        groupId = groupId,
        senderUserId = senderUserId,
        chainKey = Base64.encodeToString(chainKey, Base64.NO_WRAP),
        signingKeyPublic = Base64.encodeToString(signingKeyPublic, Base64.NO_WRAP),
        signingKeyPrivate = signingKeyPrivate?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
        iteration = iteration
    )

    private fun StoredSenderKey.toState() = SenderKeyState(
        groupId = groupId,
        senderUserId = senderUserId,
        chainKey = Base64.decode(chainKey, Base64.NO_WRAP),
        signingKeyPublic = Base64.decode(signingKeyPublic, Base64.NO_WRAP),
        signingKeyPrivate = signingKeyPrivate?.let { Base64.decode(it, Base64.NO_WRAP) },
        iteration = iteration
    )

    // -- Public API --

    fun save(state: SenderKeyState) {
        val key = "${PREFIX}${state.groupId}_${state.senderUserId}"
        prefs.edit().putString(key, gson.toJson(state.toStored())).apply()
    }

    fun load(groupId: String, senderUserId: String): SenderKeyState? {
        val key = "${PREFIX}${groupId}_${senderUserId}"
        val json = prefs.getString(key, null) ?: return null
        return try {
            gson.fromJson(json, StoredSenderKey::class.java).toState()
        } catch (e: Exception) {
            android.util.Log.w("SenderKeyStore", "Failed to parse sender key: ${e.message}")
            null
        }
    }

    /** Delete own SenderKey for a group (before rotation). */
    fun deleteOwn(groupId: String, myUserId: String) {
        prefs.edit().remove("${PREFIX}${groupId}_${myUserId}").apply()
    }

    /** Delete ALL SenderKeys for a group (on leave/removal). */
    fun deleteAllForGroup(groupId: String) {
        val editor = prefs.edit()
        val prefix = "${PREFIX}${groupId}_"
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    /** Get all stored SenderKey senderUserIds for a group. */
    fun getAllSendersForGroup(groupId: String): List<String> {
        val prefix = "${PREFIX}${groupId}_"
        return prefs.all.keys
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
    }

    /** Check if we have a SenderKey for a specific sender in a group. */
    fun has(groupId: String, senderUserId: String): Boolean =
        prefs.contains("${PREFIX}${groupId}_${senderUserId}")
}
