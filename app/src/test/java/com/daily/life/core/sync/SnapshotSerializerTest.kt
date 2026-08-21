package com.daily.life.core.sync

import com.daily.life.core.database.ActivityType
import com.daily.life.core.database.AdviceSource
import com.daily.life.core.database.ReportGenerationStatus
import com.daily.life.core.database.TransactionDirection
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
