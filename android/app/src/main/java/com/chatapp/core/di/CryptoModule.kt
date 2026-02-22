package com.chatapp.core.di

import android.app.Application
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chatapp.core.crypto.E2eeKeyStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier to distinguish E2EE EncryptedSharedPreferences from
 * any other SharedPreferences in the DI graph.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class E2eePrefs

/**
 * CryptoModule — provides all E2EE crypto dependencies.
 *
 * - EncryptedSharedPreferences backed by Android Keystore (hardware-backed on API 28+)
 * - E2eeKeyStore: persists private key material securely
 * (E2eeCryptoManager is @Singleton and @Inject constructor so Hilt wires it automatically)
 */
@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    @E2eePrefs
    fun provideE2eeEncryptedSharedPreferences(app: Application): SharedPreferences {
        val masterKey = MasterKey.Builder(app)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            app,
            "chatapp_e2ee_keys", // filename for the encrypted prefs file
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @Singleton
    fun provideE2eeKeyStore(@E2eePrefs encryptedPrefs: SharedPreferences): E2eeKeyStore {
        return E2eeKeyStore(encryptedPrefs)
    }
}
