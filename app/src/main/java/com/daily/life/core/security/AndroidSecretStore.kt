package com.daily.life.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidSecretStore(context: Context) : SecretStore {
    private val sharedPreferences by lazy {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun put(key: SecretId, value: String) {
        sharedPreferences.edit().putString(key.storageKey, value).apply()
    }

    override suspend fun read(key: SecretId): String? = sharedPreferences.getString(key.storageKey, null)

    override suspend fun remove(key: SecretId) {
        sharedPreferences.edit().remove(key.storageKey).apply()
    }

    private val SecretId.storageKey: String
        get() = "secret_${name.lowercase()}"

    companion object {
        private const val FILE_NAME = "daily.secrets"
    }
}
