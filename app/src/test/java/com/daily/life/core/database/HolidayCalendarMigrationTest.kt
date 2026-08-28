package com.daily.life.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
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
    fun migrationFrom8To9AddsHolidayCalendarTablesAndKeepsSemesterAdjustments() {
        val database = createVersion8Database().writableDatabase
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `semesters` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `startDate` TEXT NOT NULL,
                `endDate` TEXT,
                `isCurrent` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO semesters (id, name, startDate, endDate, isCurrent, createdAt)
            VALUES (1, '2026 秋季', '2026-09-01', NULL, 0, 1700000000000)
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `semester_calendar_adjustments` (
                `semesterId` INTEGER NOT NULL,
                `actualDate` TEXT NOT NULL,
                `sourceDayOfWeek` INTEGER NOT NULL,
                `sourceDate` TEXT,
                `sourceLabel` TEXT,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`semesterId`, `actualDate`),
                FOREIGN KEY(`semesterId`) REFERENCES `semesters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_semester_calendar_adjustments_semesterId` ON `semester_calendar_adjustments` (`semesterId`)"
        )
        database.execSQL(
            """
            INSERT INTO semester_calendar_adjustments (
                semesterId, actualDate, sourceDayOfWeek, sourceDate, sourceLabel, updatedAt
            ) VALUES (
                1, '2026-10-10', 5, '2026-10-03', '补周五', 1700000001000
            )
            """.trimIndent()
        )

        DailyDatabase.MIGRATION_8_9.migrate(database)

        assertTrue(database.hasTable("holiday_calendar_sources"))
        assertTrue(database.hasTable("holiday_calendar_events"))
        assertTrue(database.hasTable("calendar_day_overrides"))
        assertTrue(database.hasIndex("index_holiday_calendar_events_sourceId"))
        assertTrue(database.hasIndex("index_holiday_calendar_events_startDate_endDateInclusive"))
        assertTrue(database.hasIndex("index_calendar_day_overrides_updatedAt"))
        database.query(
            """
            SELECT semesterId, actualDate, sourceDayOfWeek, sourceDate, sourceLabel
            FROM semester_calendar_adjustments
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals("2026-10-10", cursor.getString(1))
            assertEquals(5, cursor.getInt(2))
            assertEquals("2026-10-03", cursor.getString(3))
            assertEquals("补周五", cursor.getString(4))
        }
        database.close()
    }

    private fun createVersion8Database() = FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
    )

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
