package com.daily.life.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPreferencesBackgroundGuideTest {
    private val preferenceFile = File(
        System.getProperty("java.io.tmpdir"),
        "daily-background-guide-${System.nanoTime()}.preferences_pb"
    )

    @After
    fun tearDown() {
        preferenceFile.delete()
    }

    @Test
    fun backgroundRuntimeGuideIsShownUntilItIsAcknowledged() = runTest {
        val preferences = DailyPreferences.create(
            scope = CoroutineScope(Dispatchers.Unconfined),
            produceFile = { preferenceFile }
        )

        assertFalse(preferences.backgroundRuntimeGuideAcknowledged.first())

        preferences.setBackgroundRuntimeGuideAcknowledged()

        assertTrue(preferences.backgroundRuntimeGuideAcknowledged.first())
    }

    @Test
    fun createMigratesPersistedLegacyDefaultBudgetAndPreservesCurrentSemester() = runTest {
        val legacyDefaultBudgetKey = longPreferencesKey("default_budget_cents")
        val currentSemesterKey = longPreferencesKey("current_semester_id")
        val legacyScope = testScope()
        val legacyStore = PreferenceDataStoreFactory.create(
            scope = legacyScope,
            produceFile = { preferenceFile }
        )
        legacyStore.edit { preferences ->
            preferences[legacyDefaultBudgetKey] = 300_000L
            preferences[currentSemesterKey] = 7L
        }
        close(legacyScope)

        val preferencesScope = testScope()
        val preferences = DailyPreferences.create(
            scope = preferencesScope,
            produceFile = { preferenceFile }
        )
        assertEquals(7L, preferences.currentSemesterId.first())
        close(preferencesScope)

        val verificationScope = testScope()
        val persistedPreferences = PreferenceDataStoreFactory.create(
            scope = verificationScope,
            produceFile = { preferenceFile }
        ).data.first()
        assertFalse(persistedPreferences.asMap().containsKey(legacyDefaultBudgetKey))
        assertEquals(7L, persistedPreferences[currentSemesterKey])
        close(verificationScope)
    }

    private fun testScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun close(scope: CoroutineScope) {
        scope.cancel()
        scope.coroutineContext[Job]?.join()
    }
}
