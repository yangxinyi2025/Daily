package com.daily.life.feature.health

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.ActivityRecordEntity
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

        assertEquals(130.0, requireNotNull(preferences.targetWeightJin.first()), 0.0)
        assertEquals(136.0, repository.observeWeightRecords().first().single().weightJin, 0.0)

        val report = repository.generateMonthlyReport(YearMonth.of(2026, 8))
        assertEquals(136.0, requireNotNull(report.monthAverageJin), 0.0)
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

    @Test
    fun repeatedActivityImportsUpsertByStableIdentityAndKeepManualRecords() = runTest {
        database.healthDao().insertActivity(
            ActivityRecordEntity(
                recordedAt = Instant.parse("2026-08-20T06:30:00Z").toEpochMilli(),
                activityType = ActivityType.WALK,
                steps = 1_200L,
                distanceMeters = 900.0,
                durationMinutes = 10,
                source = "MANUAL"
            )
        )
        val source = object : ActivityDataSource {
            override suspend fun availability() = DataSourceAvailability.available("测试适配器")

            override suspend fun read(start: Instant, end: Instant): List<ActivityRecord> = listOf(
                ActivityRecord(
                    recordedAt = Instant.parse("2026-08-20T07:00:00Z"),
                    activityType = ActivityType.WALK,
                    steps = 2_000L,
                    distanceMeters = 1_500.0,
                    durationMinutes = 20,
                    source = "HEALTH_CONNECT",
                    rawRecordId = "walk-20260820-0700"
                ),
                ActivityRecord(
                    recordedAt = Instant.parse("2026-08-20T08:00:00Z"),
                    activityType = ActivityType.RUN,
                    steps = 1_500L,
                    distanceMeters = 1_000.0,
                    durationMinutes = 12,
                    source = "PHONE_SENSOR"
                )
            )
        }
        val repository = HealthRepository(
            healthDao = database.healthDao(),
            preferences = preferences,
            activitySources = { listOf(source) }
        )

        repository.readActivity(
            start = Instant.parse("2026-08-20T00:00:00Z"),
            end = Instant.parse("2026-08-21T00:00:00Z")
        )
        repository.readActivity(
            start = Instant.parse("2026-08-20T00:00:00Z"),
            end = Instant.parse("2026-08-21T00:00:00Z")
        )

        val records = database.healthDao().observeActivitiesBetween(0L, Long.MAX_VALUE).first()

        assertEquals(3, records.size)
        assertEquals(1, records.count { it.source == "MANUAL" })
        assertEquals(1, records.count { it.rawRecordId == "walk-20260820-0700" })
        assertEquals(1, records.count { it.source == "PHONE_SENSOR" && it.rawRecordId == null })
        assertEquals(4_700L, records.sumOf { it.steps ?: 0L })
    }
}
