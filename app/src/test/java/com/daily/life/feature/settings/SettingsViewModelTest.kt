package com.daily.life.feature.settings

import com.daily.life.core.datastore.DailyPreferences
import com.daily.life.core.security.InMemorySecretStore
import com.daily.life.core.security.SecretId
import com.daily.life.core.security.SecretStore
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun savingTargetWeightUpdatesPreferences() = runTest {
        val preferences = createTestPreferences()
        val viewModel = SettingsViewModel(
            semesterRepository = EmptySemesterSettingsRepository,
            preferences = preferences,
            secretStore = InMemorySecretStore(),
            coroutineScope = backgroundScope
        )

        viewModel.updateTargetWeightJin(132.5)

        assertEquals(132.5, preferences.targetWeightJin.first() ?: Double.NaN, 0.0)
        assertEquals(
            132.5,
            viewModel.state.first { it.targetWeightJin == 132.5 }.targetWeightJin ?: Double.NaN,
            0.0
        )
    }

    @Test
    fun savingSemesterStartDateUpdatesPreferences() = runTest {
        val preferences = createTestPreferences()
        val viewModel = SettingsViewModel(
            semesterRepository = EmptySemesterSettingsRepository,
            preferences = preferences,
            secretStore = InMemorySecretStore(),
            coroutineScope = backgroundScope
        )

        viewModel.updateSemesterStartDate(LocalDate.of(2026, 9, 1))

        assertEquals(LocalDate.of(2026, 9, 1), preferences.semesterStartDate.first())
        assertEquals(
            LocalDate.of(2026, 9, 1),
            viewModel.state.first { it.semesterStartDate == LocalDate.of(2026, 9, 1) }.semesterStartDate
        )
    }

    @Test
    fun savingMonthlyBudgetUpdatesPreferences() = runTest {
        val preferences = createTestPreferences()
        val viewModel = SettingsViewModel(
            semesterRepository = EmptySemesterSettingsRepository,
            preferences = preferences,
            secretStore = InMemorySecretStore(),
            coroutineScope = backgroundScope
        )

        viewModel.updateMonthlyBudgetCents(250_000L)

        assertEquals(250_000L, preferences.defaultBudgetCents.first())
        assertEquals(
            250_000L,
            viewModel.state.first { it.monthlyBudgetCents == 250_000L }.monthlyBudgetCents
        )
    }

    @Test
    fun savingWebDavConfigStoresSecretsAndMasksState() = runTest {
        val preferences = createTestPreferences()
        val secretStore: SecretStore = InMemorySecretStore()
        val viewModel = SettingsViewModel(
            semesterRepository = EmptySemesterSettingsRepository,
            preferences = preferences,
            secretStore = secretStore,
            coroutineScope = backgroundScope
        )

        viewModel.updateSemesterStartDate(LocalDate.of(2026, 9, 1))
        viewModel.saveWebDavConfig(
            WebDavConfigInput(
                endpoint = "https://dav.example.com/daily",
                username = "xinyi",
                password = "super-secret-password"
            )
        )

        val state = viewModel.state.first { it.webDavEndpoint == "https://dav.example.com/daily" }

        assertEquals("https://dav.example.com/daily", preferences.webDavEndpoint.first())
        assertEquals("xinyi", secretStore.read(SecretId.WebDavUsername))
        assertEquals("super-secret-password", secretStore.read(SecretId.WebDavPassword))
        assertEquals(LocalDate.of(2026, 9, 1), state.semesterStartDate)
        assertTrue(state.webDavPasswordSummary.contains("已保存"))
        assertTrue(!state.webDavPasswordSummary.contains("super-secret-password"))
    }

    private fun createTestPreferences(): DailyPreferences {
        val directory = createTempDir(prefix = "daily-settings-prefs")
        return DailyPreferences.create(
            scope = CoroutineScope(Dispatchers.Unconfined),
            produceFile = { File(directory, "daily.preferences_pb") }
        )
    }
}
