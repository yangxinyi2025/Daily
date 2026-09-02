package com.daily.life.core.sync

import com.daily.life.core.database.ActivityType
import com.daily.life.core.database.AdviceSource
import com.daily.life.core.database.CourseWeekEntity
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.database.ReportGenerationStatus
import com.daily.life.core.database.ReminderMode
import com.daily.life.core.database.ScheduleEventEntity
import com.daily.life.core.database.TransactionDirection
import com.daily.life.core.database.TransactionEntity
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotSerializerTest {
    private val serializer = SnapshotSerializer()

    @Test
    fun snapshotRoundTripPreservesCentsAndSchemaVersion() {
        val decoded = serializer.decode(serializer.encode(sampleSnapshot()))

        assertEquals(sampleSnapshot().schemaVersion, decoded.schemaVersion)
        assertEquals(12_345L, decoded.transactions.single().amountCents)
        assertEquals(sampleSnapshot().settings.webDavEndpoint, decoded.settings.webDavEndpoint)
    }

    @Test
    fun scheduleLocationRoundTripsAndLegacySnapshotsRemainReadable() {
        val snapshot = sampleSnapshot().copy(
            scheduleEvents = listOf(
                ScheduleEventEntity(
                    id = 7L,
                    title = "讲座",
                    eventAt = 1_700_000_000_000L,
                    reminderOffsetMinutes = 15,
                    reminderMode = ReminderMode.NOTIFICATION,
                    repeatYearly = false,
                    location = "教学楼 A201",
                    createdAt = 1_700_000_000_000L,
                    updatedAt = 1_700_000_000_000L
                )
            )
        )

        val encoded = serializer.encode(snapshot)
        assertEquals("教学楼 A201", serializer.decode(encoded).scheduleEvents.single().location)

        val legacyEncoded = encoded.toString(Charsets.UTF_8)
            .replace(",\"location\":\"教学楼 A201\"", "")
            .toByteArray()
        assertEquals(null, serializer.decode(legacyEncoded).scheduleEvents.single().location)
    }

    @Test
    fun encodingIsDeterministicWhenRecordListsHaveDifferentInputOrder() {
        val first = SemesterEntity(
            id = 1L,
            name = "一",
            startDate = LocalDate.of(2026, 9, 1),
            createdAt = 1_700_000_000_000L
        )
        val second = first.copy(id = 2L, name = "二")

        val left = sampleSnapshot().copy(semesters = listOf(second, first))
        val right = sampleSnapshot().copy(semesters = listOf(first, second))

        assertEquals(serializer.encode(left).toList(), serializer.encode(right).toList())
    }

    @Test
    fun snapshotEncodingNeverContainsSecrets() {
        val encoded = serializer.encode(sampleSnapshot()).toString(Charsets.UTF_8)

        assertFalse(encoded.contains("super-secret"))
        assertFalse(encoded.contains("deepseek-key"))
        assertTrue(encoded.contains("dav.example.com"))
    }

    @Test
    fun unsupportedSchemaVersionIsRejectedAsTypedError() {
        val encoded = serializer.encode(sampleSnapshot()).toString(Charsets.UTF_8)
            .replace("\"schemaVersion\":1", "\"schemaVersion\":999")

        try {
            serializer.decode(encoded.toByteArray())
            throw AssertionError("Expected unsupported schema error")
        } catch (error: SnapshotValidationException.UnsupportedSchemaVersion) {
            assertEquals(999, error.actual)
        }
    }

    @Test
    fun invalidRecordTimestampsAreRejected() {
        val malformed = sampleSnapshot().copy(
            transactions = listOf(
                TransactionEntity(
                    id = 1L,
                    occurredAt = 1_700_000_000_000L,
                    amountCents = 12_345L,
                    direction = TransactionDirection.EXPENSE,
                    category = "餐饮",
                    counterparty = "测试商户",
                    source = "TEST",
                    createdAt = 2_000L,
                    updatedAt = 1_000L
                )
            )
        )

        assertMalformed(malformed)
    }

    @Test
    fun danglingCourseWeekReferencesAreRejected() {
        val malformed = sampleSnapshot().copy(
            courseWeeks = listOf(CourseWeekEntity(courseId = 99L, week = 1))
        )

        assertMalformed(malformed)
    }

    @Test
    fun invalidSnapshotSettingsAreRejected() {
        val malformed = sampleSnapshot().copy(
            settings = sampleSnapshot().settings.copy(defaultBudgetCents = -1L)
        )

        assertMalformed(malformed)
    }

    @Test
    fun restoreRequiresExplicitConfirmationBeforeReplacingLocalData() = runTest {
        val directory = createTempDir(prefix = "daily-snapshot-restore")
        val store = FakeSnapshotStore(sampleSnapshot())
        val repository = LocalBackupRepository(
            store = store,
            serializer = serializer,
            snapshotDirectory = directory,
            deviceId = "test-device",
            clock = java.time.Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), java.time.ZoneOffset.UTC)
        )
        val file = File(directory, "incoming.snapshot").apply {
            writeBytes(serializer.encode(sampleSnapshot()))
        }

        try {
            repository.restoreSnapshot(file, replaceExisting = false)
            throw AssertionError("Expected confirmation error")
        } catch (_: RestoreConfirmationRequired) {
            assertFalse(store.replaced)
        }
    }

    @Test
    fun restoreRejectsSnapshotFromAnotherDevice() = runTest {
        val directory = createTempDir(prefix = "daily-snapshot-device")
        val store = FakeSnapshotStore(sampleSnapshot())
        val repository = LocalBackupRepository(
            store = store,
            serializer = serializer,
            snapshotDirectory = directory,
            deviceId = "test-device"
        )
        val file = File(directory, "incoming.snapshot").apply {
            writeBytes(serializer.encode(sampleSnapshot().copy(deviceId = "other-device")))
        }

        try {
            repository.restoreSnapshot(file, replaceExisting = true)
            throw AssertionError("Expected device validation error")
        } catch (_: SnapshotValidationException) {
            assertFalse(store.replaced)
        }
    }

    private fun assertMalformed(snapshot: DailySnapshot) {
        try {
            serializer.decode(serializer.encode(snapshot))
            throw AssertionError("Expected malformed snapshot error")
        } catch (_: SnapshotValidationException.MalformedSnapshot) {
            // Expected.
        }
    }

    private fun sampleSnapshot(): DailySnapshot = DailySnapshot(
        schemaVersion = SnapshotSerializer.CURRENT_SCHEMA_VERSION,
        deviceId = "test-device",
        createdAt = Instant.parse("2026-08-20T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-20T00:00:00Z"),
        settings = SnapshotSettings(
            semesterStartDate = LocalDate.of(2026, 9, 1),
            currentSemesterId = 1L,
            targetWeightJin = 130.0,
            defaultBudgetCents = 300_000L,
            webDavEndpoint = "https://dav.example.com/daily",
            autoSyncEnabled = false
        ),
        semesters = emptyList(),
        courses = emptyList(),
        courseWeeks = emptyList(),
        scheduleEvents = emptyList(),
        weights = emptyList(),
        activities = emptyList(),
        monthlyReports = listOf(
            com.daily.life.core.database.MonthlyReportEntity(
                id = 1L,
                month = YearMonth.of(2026, 8),
                weightTrendSummary = "stable",
                activitySummary = "active",
                aiAdviceText = null,
                generatedAt = 1_700_000_000_000L,
                adviceSource = AdviceSource.LOCAL,
                generationStatus = ReportGenerationStatus.COMPLETE
            )
        ),
        transactions = listOf(
            com.daily.life.core.database.TransactionEntity(
                id = 1L,
                occurredAt = 1_700_000_000_000L,
                amountCents = 12_345L,
                direction = TransactionDirection.EXPENSE,
                category = "餐饮",
                counterparty = "测试商户",
                source = "TEST",
                notes = "local only"
            )
        ),
        budgets = emptyList(),
        importLogs = emptyList()
    )

    private class FakeSnapshotStore(initial: DailySnapshot) : LocalSnapshotStore {
        private var value = initial
        var replaced = false

        override suspend fun read(): DailySnapshot = value

        override suspend fun replace(snapshot: DailySnapshot) {
            replaced = true
            value = snapshot
        }
    }
}
