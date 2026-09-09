package com.daily.life.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DailyDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "daily-period-migration-test.db"
    private val roomMigrationDatabaseName = "daily.db"

    @Before
    fun setUp() {
        deleteDatabaseFiles(databaseName)
        deleteDatabaseFiles(roomMigrationDatabaseName)
    }

    @After
    fun tearDown() {
        deleteDatabaseFiles(databaseName)
        deleteDatabaseFiles(roomMigrationDatabaseName)
    }

    @Test
    fun migrationFrom3To4AddsPeriodRecordsWithoutTouchingExistingData() {
        val database = createVersion3Database().writableDatabase
        database.execSQL("CREATE TABLE existing_user_data (value TEXT NOT NULL)")
        database.execSQL("INSERT INTO existing_user_data (value) VALUES ('keep')")

        DailyDatabase.MIGRATION_3_4.migrate(database)

        assertTrue(database.hasTable("period_records"))
        assertTrue(database.hasIndex("index_period_records_startDate"))
        assertTrue(database.hasIndex("index_period_records_endDate"))
        assertTrue(database.hasRow("SELECT value FROM existing_user_data WHERE value = 'keep'"))
        database.close()
    }

    @Test
    fun migrationFrom5To6AddsCalendarSyncLinksWithoutRemovingExistingRows() {
        val database = createVersion5Database().writableDatabase
        database.execSQL("CREATE TABLE existing_user_data (value TEXT NOT NULL)")
        database.execSQL("INSERT INTO existing_user_data (value) VALUES ('keep')")

        DailyDatabase.MIGRATION_5_6.migrate(database)

        assertTrue(database.hasTable("calendar_sync_links"))
        assertTrue(database.hasRow("SELECT value FROM existing_user_data WHERE value = 'keep'"))
        database.close()
    }

    @Test
    fun migrationFrom6To7AddsSemesterPeriodTimesWithoutRemovingExistingRows() {
        val database = createVersion6Database().writableDatabase
        database.execSQL("CREATE TABLE existing_course_data (courseName TEXT NOT NULL)")
        database.execSQL("INSERT INTO existing_course_data (courseName) VALUES ('高等数学')")

        DailyDatabase.MIGRATION_6_7.migrate(database)

        assertTrue(database.hasTable("semester_periods"))
        assertTrue(database.hasIndex("index_semester_periods_semesterId"))
        database.query("SELECT courseName FROM existing_course_data").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("高等数学", cursor.getString(0))
        }
        database.close()
    }

    @Test
    fun migrationFrom11To12AddsNullableScheduleLocationWithoutRemovingExistingRows() {
        val database = createVersion11Database().writableDatabase
        database.execSQL(
            """
            CREATE TABLE schedule_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                eventAt INTEGER NOT NULL,
                reminderOffsetMinutes INTEGER NOT NULL,
                reminderMode TEXT NOT NULL,
                repeatYearly INTEGER NOT NULL,
                notes TEXT,
                isDismissed INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO schedule_events (
                title, eventAt, reminderOffsetMinutes, reminderMode, repeatYearly,
                notes, isDismissed, createdAt, updatedAt
            ) VALUES ('已有日程', 0, 0, 'NOTIFICATION', 0, NULL, 0, 0, 0)
            """.trimIndent()
        )

        DailyDatabase.MIGRATION_11_12.migrate(database)

        assertTrue(database.hasColumn("schedule_events", "location"))
        assertTrue(database.hasRow("SELECT title FROM schedule_events WHERE title = '已有日程'"))
        database.close()
    }

    @Test
    fun dailyDatabaseOpeningVersion12DatabaseUsesRegisteredMigrationToDropBillTables() {
        val legacyDatabase = DailyDatabase.build(context)
        legacyDatabase.openHelper.writableDatabase.apply {
            execSQL(
                """
                INSERT INTO semesters (id, name, startDate, endDate, isCurrent, createdAt)
                VALUES (1, '2026 秋季', '2026-09-01', NULL, 1, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO courses (
                    id, semesterId, courseName, dayOfWeek, startPeriod, endPeriod,
                    weekRuleText, parsedWeeks, campus, location, teacher, courseCode,
                    credits, notes, courseReminderMode, courseReminderMinutes
                ) VALUES (
                    1, 1, '数据库', 2, 1, 2,
                    '第 1 周', '1', NULL, NULL, NULL, NULL,
                    NULL, NULL, 'FOLLOW_GLOBAL', NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    occurredAt INTEGER NOT NULL,
                    amountCents INTEGER NOT NULL,
                    direction TEXT NOT NULL,
                    category TEXT NOT NULL,
                    counterparty TEXT NOT NULL,
                    source TEXT NOT NULL,
                    paymentMethod TEXT,
                    transactionType TEXT,
                    status TEXT,
                    merchantOrderId TEXT,
                    orderId TEXT,
                    rawText TEXT,
                    notes TEXT,
                    importBatchId TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX index_transactions_occurredAt ON transactions (occurredAt)")
            execSQL("CREATE INDEX index_transactions_category ON transactions (category)")
            execSQL("CREATE INDEX index_transactions_direction ON transactions (direction)")
            execSQL(
                """
                CREATE TABLE budgets (
                    month TEXT NOT NULL,
                    budgetCents INTEGER NOT NULL,
                    triggeredPercentages TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(month)
                )
                """.trimIndent()
            )
            execSQL("PRAGMA user_version = 12")
        }
        legacyDatabase.close()

        val migratedDatabase = DailyDatabase.build(context)
        val database = migratedDatabase.openHelper.writableDatabase

        assertEquals(13, database.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        })
        assertFalse(database.hasTable("transactions"))
        assertFalse(database.hasTable("budgets"))
        assertTrue(database.hasTable("courses"))
        assertTrue(database.hasRow("SELECT courseName FROM courses WHERE courseName = '数据库'"))
        migratedDatabase.close()
    }

    private fun createVersion3Database() = FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
    )

    private fun createVersion5Database() = FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
    )

    private fun createVersion6Database() = FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
    )

    private fun createVersion11Database() = FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(11) {
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

    private fun SupportSQLiteDatabase.hasColumn(tableName: String, columnName: String): Boolean =
        query("PRAGMA table_info($tableName)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == columnName }
        }

    private fun SupportSQLiteDatabase.hasRow(sql: String): Boolean = query(sql).use { it.moveToFirst() }

    private fun deleteDatabaseFiles(name: String) {
        context.deleteDatabase(name)
        context.getDatabasePath("$name-wal").delete()
        context.getDatabasePath("$name-shm").delete()
        context.getDatabasePath("$name-journal").delete()
    }
}
