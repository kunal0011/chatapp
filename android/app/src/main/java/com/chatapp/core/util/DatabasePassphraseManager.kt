package com.chatapp.core.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabasePassphraseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_db_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getPassphrase(): ByteArray {
        val existing = sharedPrefs.getString("db_passphrase", null)
        return if (existing != null) {
            existing.toByteArray()
        } else {
            val newPassphrase = UUID.randomUUID().toString()
            sharedPrefs.edit().putString("db_passphrase", newPassphrase).apply()
            newPassphrase.toByteArray()
        }
    }
}
