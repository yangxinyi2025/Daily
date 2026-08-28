package com.daily.life.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HolidayCalendarMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "daily-holiday-calendar-migration-test.db"

    @Before
    fun setUp() = deleteDatabaseFiles()

    @After
    fun tearDown() = deleteDatabaseFiles()

    @Test
    fun migrationFrom8To9AddsHolidayCalendarTablesAndKeepsSemesterAdjustments() = runTest {
        seedVersion8DatabaseWithCurrentRoomSchema()

        val migrated = Room.databaseBuilder(context, DailyDatabase::class.java, databaseName)
            .addMigrations(DailyDatabase.MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()

        try {
            val database = migrated.openHelper.writableDatabase

            assertTrue(database.hasTable("holiday_calendar_sources"))
            assertTrue(database.hasTable("holiday_calendar_events"))
            assertTrue(database.hasTable("calendar_day_overrides"))
            assertTrue(database.hasIndex("index_holiday_calendar_events_sourceId"))
            assertTrue(database.hasIndex("index_holiday_calendar_events_startDate_endDateInclusive"))
            assertTrue(database.hasIndex("index_calendar_day_overrides_updatedAt"))
            migrated.semesterCalendarAdjustmentDao().findBySemester(1L).single().also { row ->
                assertEquals(1L, row.semesterId)
                assertEquals("2026-10-10", row.actualDate.toString())
                assertEquals(5, row.sourceDayOfWeek)
                assertEquals("2026-10-03", row.sourceDate.toString())
                assertEquals("补周五", row.sourceLabel)
            }
        } finally {
            migrated.close()
        }
    }

    private suspend fun seedVersion8DatabaseWithCurrentRoomSchema() {
        val database = Room.databaseBuilder(context, DailyDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            database.semesterDao().insert(
                SemesterEntity(
                    id = 1L,
                    name = "2026 秋季",
                    startDate = java.time.LocalDate.of(2026, 9, 1),
                    createdAt = 1_700_000_000_000L
                )
            )
            database.semesterCalendarAdjustmentDao().upsertAll(
                listOf(
                    SemesterCalendarAdjustmentEntity(
                        semesterId = 1L,
                        actualDate = java.time.LocalDate.of(2026, 10, 10),
                        sourceDayOfWeek = 5,
                        sourceDate = java.time.LocalDate.of(2026, 10, 3),
                        sourceLabel = "补周五",
                        updatedAt = 1_700_000_001_000L
                    )
                )
            )
        } finally {
            database.close()
        }

        val databasePath = context.getDatabasePath(databaseName)
        SQLiteDatabase.openDatabase(databasePath.path, null, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
            sqlite.execSQL("DROP TABLE IF EXISTS `calendar_day_overrides`")
            sqlite.execSQL("DROP TABLE IF EXISTS `holiday_calendar_events`")
            sqlite.execSQL("DROP TABLE IF EXISTS `holiday_calendar_sources`")
            sqlite.execSQL("DROP INDEX IF EXISTS `index_calendar_day_overrides_updatedAt`")
            sqlite.execSQL("DROP INDEX IF EXISTS `index_holiday_calendar_events_startDate_endDateInclusive`")
            sqlite.execSQL("DROP INDEX IF EXISTS `index_holiday_calendar_events_sourceId`")
            sqlite.execSQL("DROP INDEX IF EXISTS `index_holiday_calendar_sources_url`")
            sqlite.execSQL("PRAGMA user_version = 8")
        }
    }

    private fun SupportSQLiteDatabase.hasTable(name: String): Boolean =
        hasRow("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$name'")

    private fun SupportSQLiteDatabase.hasIndex(name: String): Boolean =
        hasRow("SELECT name FROM sqlite_master WHERE type = 'index' AND name = '$name'")

    private fun SupportSQLiteDatabase.hasRow(sql: String): Boolean = query(sql).use { it.moveToFirst() }

    private fun deleteDatabaseFiles() {
        context.deleteDatabase(databaseName)
        context.getDatabasePath("$databaseName-wal").delete()
        context.getDatabasePath("$databaseName-shm").delete()
        context.getDatabasePath("$databaseName-journal").delete()
    }
}
