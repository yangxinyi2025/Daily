package com.daily.life.feature.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HealthViewModelTest {
    private lateinit var database: DailyDatabase
    private lateinit var preferences: DailyPreferences
    private lateinit var preferencesFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = DailyDatabase.buildInMemory(context)
        preferencesFile = File.createTempFile("daily-health-view-model", ".preferences_pb")
        preferencesFile.delete()
        preferences = DailyPreferences.create(produceFile = { preferencesFile })
    }

    @After
    fun tearDown() {
        database.close()
        preferencesFile.delete()
    }

    @Test
    fun recordingPeriodPublishesThirtyDayPrediction() = runTest {
        val viewModel = HealthViewModel(
            repository = HealthRepository(database.healthDao(), preferences),
            periodRepository = PeriodRepository(database.periodDao(), preferences),
            preferences = preferences,
            clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
            coroutineScope = backgroundScope
        )

        viewModel.recordPeriod(
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 5)
        )

        val state = viewModel.state.first { it.nextPeriodStart != null }
        assertEquals(LocalDate.of(2026, 8, 31), state.nextPeriodStart)
        assertEquals(1, state.periodRecords.size)
    }

    @Test
    fun recordingWeightUsesTheChosenDate() = runTest {
        val viewModel = HealthViewModel(
            repository = HealthRepository(database.healthDao(), preferences),
            periodRepository = PeriodRepository(database.periodDao(), preferences),
            preferences = preferences,
            clock = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC),
            coroutineScope = backgroundScope
        )

        viewModel.recordWeight(
            weightJin = 118.5,
            recordedOn = LocalDate.of(2026, 8, 17)
        )

        val record = viewModel.state.first { it.weights.size == 1 }.weights.single()
        assertEquals(LocalDate.of(2026, 8, 17), record.recordedAt.atZone(ZoneOffset.UTC).toLocalDate())
    }

    @Test
    fun recordingWeightPreservesTheChosenTime() = runTest {
        val viewModel = HealthViewModel(
            repository = HealthRepository(database.healthDao(), preferences),
            periodRepository = PeriodRepository(database.periodDao(), preferences),
            preferences = preferences,
            clock = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC),
            coroutineScope = backgroundScope
        )

        viewModel.recordWeight(
            weightJin = 118.5,
            recordedAt = LocalDateTime.of(2026, 8, 17, 9, 45)
        )

        val record = viewModel.state.first { it.weights.size == 1 }.weights.single()
        assertEquals(Instant.parse("2026-08-17T09:45:00Z"), record.recordedAt)
    }
}
