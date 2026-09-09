package com.daily.life.feature.settings

import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun savingTargetWeightUpdatesPreferences() = runTest {
        val preferences = createTestPreferences()
        val viewModel = SettingsViewModel(
            semesterRepository = EmptySemesterSettingsRepository,
            preferences = preferences,
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
            coroutineScope = backgroundScope
        )

        viewModel.updateSemesterStartDate(LocalDate.of(2026, 9, 1))

        assertEquals(LocalDate.of(2026, 9, 1), preferences.semesterStartDate.first())
        assertEquals(
            LocalDate.of(2026, 9, 1),
            viewModel.state.first { it.semesterStartDate == LocalDate.of(2026, 9, 1) }.semesterStartDate
        )
    }

    private fun createTestPreferences(): DailyPreferences {
        val directory = createTempDir(prefix = "daily-settings-prefs")
        return DailyPreferences.create(
            scope = CoroutineScope(Dispatchers.Unconfined),
            produceFile = { File(directory, "daily.preferences_pb") }
        )
    }
}
