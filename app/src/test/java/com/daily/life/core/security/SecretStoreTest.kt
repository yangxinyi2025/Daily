package com.daily.life.core.security

import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretStoreTest {
    @Test
    fun secretStoreReturnsValueWithoutWritingItToPreferences() = runTest {
        val preferences = createTestPreferences()
        val store: SecretStore = InMemorySecretStore()

        store.put(SecretId.DeepSeekApiKey, "test-key")

        assertEquals("test-key", store.read(SecretId.DeepSeekApiKey))
        assertNull(preferences.currentSemesterId.first())
        assertNull(preferences.targetWeightJin.first())
        assertNull(preferences.defaultBudgetCents.first())
    }

    @Test
    fun removeClearsStoredSecret() = runTest {
        val store: SecretStore = InMemorySecretStore()

        store.put(SecretId.WebDavPassword, "secret")
        store.remove(SecretId.WebDavPassword)

        assertNull(store.read(SecretId.WebDavPassword))
    }

    private fun createTestPreferences(): DailyPreferences {
        val directory = createTempDir(prefix = "daily-prefs")
        return DailyPreferences.create(
            scope = CoroutineScope(Dispatchers.Unconfined),
            produceFile = { File(directory, "daily.preferences_pb") }
        )
    }
}
