package com.chatapp.core.crypto

import android.content.SharedPreferences
import android.util.Base64
import com.chatapp.core.di.E2eePrefs
import com.chatapp.data.api.KeysApi
import com.chatapp.data.api.UsersApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emitted when a contact's identity key on the server differs from the last-seen key.
 * The UI should display a warning dialog before allowing further messages.
 */
data class KeyChangeEvent(
    val contactUserId: String,
    val contactDisplayName: String,
    val newIdentityKey: String   // base64 — the key currently on the server
)

/**
 * KeyChangeDetector — detects when a contact's identity key has changed.
 *
 * Flow:
 *  1. On each fetchConversations(), call checkForKeyChange() for each DIRECT conversation partner.
 *  2. Compare the server's current identityKey with the cached knownIdentityKey in EncryptedSharedPrefs.
 *  3. If mismatched → emit a KeyChangeEvent via [keyChangeEvents].
 *  4. UI shows a warning dialog. User either:
 *     a. Verifies Safety Number → calls [acknowledgeKeyChange] → stamps ikVerifiedAt on server.
 *     b. Continues anyway → calls [acknowledgeKeyChange] (updates cached key without stamping verified).
 *
 * Note: On first contact (no cached key), the server key is silently cached via [cacheIdentityKey]
 * and no alert is shown.
 */
@Singleton
class KeyChangeDetector @Inject constructor(
    private val keysApi: KeysApi,
    private val usersApi: UsersApi,
    @E2eePrefs private val encryptedPrefs: SharedPreferences
) {
    companion object {
        private const val KEY_PREFIX = "known_ik_"  // known_ik_<contactUserId>
    }

    private val _keyChangeEvents = MutableSharedFlow<KeyChangeEvent>(extraBufferCapacity = 8)

    /** Observe key change events. Collect in a ViewModel to show the alert dialog. */
    val keyChangeEvents: SharedFlow<KeyChangeEvent> = _keyChangeEvents

    /**
     * Check if [contactUserId]'s identity key matches what we last saw.
     * - First time we see this contact → silently cache, no alert.
     * - Subsequent calls where key changed → emit [KeyChangeEvent].
     *
     * Should be called from a coroutine (e.g. inside fetchConversations()).
     */
    suspend fun checkForKeyChange(contactUserId: String, contactDisplayName: String) {
        val serverKey = try {
            keysApi.getKeyBundle(contactUserId).identityKey
        } catch (e: Exception) {
            android.util.Log.w("KeyChangeDetector", "Could not fetch key bundle for $contactUserId: ${e.message}")
            return
        }

        val cachedKey = encryptedPrefs.getString("$KEY_PREFIX$contactUserId", null)

        when {
            cachedKey == null -> {
                // First time seeing this contact — cache silently and tell the server
                android.util.Log.d("KeyChangeDetector", "Caching initial identity key for $contactUserId")
                cacheLocally(contactUserId, serverKey)
                notifyServerOfKnownKey(contactUserId, serverKey)
            }
            cachedKey != serverKey -> {
                // Key has changed — alert the user!
                android.util.Log.w("KeyChangeDetector",
                    "⚠️ Identity key CHANGED for $contactUserId! Emitting alert.")
                _keyChangeEvents.emit(KeyChangeEvent(
                    contactUserId = contactUserId,
                    contactDisplayName = contactDisplayName,
                    newIdentityKey = serverKey
                ))
            }
            else -> {
                // Keys match — all good
            }
        }
    }

    /**
     * Called when the user acknowledges a key change alert (either by verifying Safety Number
     * or by tapping "Continue Anyway"). Updates both local cache and server.
     */
    suspend fun acknowledgeKeyChange(contactUserId: String, newIdentityKey: String, verified: Boolean) {
        cacheLocally(contactUserId, newIdentityKey)
        if (verified) {
            // User explicitly verified — stamp ikVerifiedAt on server
            runCatching {
                usersApi.verifyIdentityKey(contactUserId, mapOf("identityKey" to newIdentityKey))
            }
        } else {
            // User continued without verifying — just update the cached key
            notifyServerOfKnownKey(contactUserId, newIdentityKey)
        }
    }

    /** Check if a given key matches what we currently have cached for a contact. */
    fun isKeyTrusted(contactUserId: String, identityKey: String): Boolean =
        encryptedPrefs.getString("$KEY_PREFIX$contactUserId", null) == identityKey

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun cacheLocally(contactUserId: String, identityKey: String) {
        encryptedPrefs.edit()
            .putString("$KEY_PREFIX$contactUserId", identityKey)
            .apply()
    }

    private suspend fun notifyServerOfKnownKey(contactUserId: String, identityKey: String) {
        runCatching {
            usersApi.updateKnownIdentityKey(contactUserId, mapOf("identityKey" to identityKey))
        }.onFailure {
            android.util.Log.w("KeyChangeDetector", "Could not update known-key on server: ${it.message}")
        }
    }
}
