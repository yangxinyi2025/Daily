package com.daily.life.core.security

enum class SecretId {
    DeepSeekApiKey,
    WebDavUsername,
    WebDavPassword
}

interface SecretStore {
    suspend fun put(key: SecretId, value: String)
    suspend fun read(key: SecretId): String?
    suspend fun remove(key: SecretId)
}

class InMemorySecretStore : SecretStore {
    private val values = linkedMapOf<SecretId, String>()

    override suspend fun put(key: SecretId, value: String) {
        values[key] = value
    }

    override suspend fun read(key: SecretId): String? = values[key]

    override suspend fun remove(key: SecretId) {
        values.remove(key)
    }
}
