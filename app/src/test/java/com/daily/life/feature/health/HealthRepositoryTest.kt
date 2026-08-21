package com.daily.life.feature.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.ActivityType
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HealthRepositoryTest {
    private lateinit var database: DailyDatabase
    private lateinit var preferences: DailyPreferences
    private lateinit var preferencesFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = DailyDatabase.buildInMemory(context)
        preferencesFile = File.createTempFile("daily-health-test", ".preferences_pb")
        preferencesFile.delete()
        preferences = DailyPreferences.create(produceFile = { preferencesFile })
    }

    @After
    fun tearDown() {
        database.close()
        preferencesFile.delete()
    }

    @Test
    fun manualWeightAndTargetRemainAvailableOffline() = runTest {
        val repository = HealthRepository(
            healthDao = database.healthDao(),
            preferences = preferences,
            activitySource = { null },
            clock = java.time.Clock.fixed(
                Instant.parse("2026-08-20T08:00:00Z"),
                ZoneOffset.UTC
            )
        )

        repository.setTargetWeight(130.0)
        repository.recordWeight(
            recordedAt = Instant.parse("2026-08-20T07:00:00Z"),
            weightJin = 136.0
        )

        assertEquals(130.0, preferences.targetWeightJin.first(), 0.0)
        assertEquals(136.0, repository.observeWeightRecords().first().single().weightJin, 0.0)

        val report = repository.generateMonthlyReport(YearMonth.of(2026, 8))
        assertEquals(136.0, report.monthAverageJin, 0.0)
        assertEquals(ReportDataState.INSUFFICIENT, report.dataState)
        assertTrue(database.healthDao().findMonthlyReport(YearMonth.of(2026, 8)) != null)
    }

    @Test
    fun activitySourceIsCreatedOnlyWhenTheUserReadsActivity() = runTest {
        var sourceFactoryCalls = 0
        val source = object : ActivityDataSource {
            override suspend fun availability() = DataSourceAvailability.available("测试适配器")

            override suspend fun read(start: Instant, end: Instant): List<ActivityRecord> = listOf(
                ActivityRecord(
                    recordedAt = Instant.parse("2026-08-20T07:00:00Z"),
                    activityType = ActivityType.WALK,
                    steps = 2_000L,
                    source = "TEST"
                )
            )
        }
        val repository = HealthRepository(
            healthDao = database.healthDao(),
            preferences = preferences,
            activitySources = {
                sourceFactoryCalls += 1
                listOf(source)
            }
        )

        assertEquals(0, sourceFactoryCalls)

        val result = repository.readActivity(
            start = Instant.parse("2026-08-20T00:00:00Z"),
            end = Instant.parse("2026-08-21T00:00:00Z")
        )

        assertEquals(1, sourceFactoryCalls)
        assertEquals(1, result.importedCount)
        assertEquals(2_000L, database.healthDao().observeActivitiesBetween(0L, Long.MAX_VALUE).first().single().steps)
    }
}
