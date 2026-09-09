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

    @Before
    fun setUp() = deleteDatabaseFiles()

    @After
    fun tearDown() = deleteDatabaseFiles()

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
    fun migrationFrom12To13DropsBillTablesAndPreservesCourses() {
        val database = createVersion12Database().writableDatabase
        database.execSQL("CREATE TABLE courses (id INTEGER PRIMARY KEY NOT NULL, courseName TEXT NOT NULL)")
        database.execSQL("INSERT INTO courses (id, courseName) VALUES (1, '数据库')")
        database.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY NOT NULL)")
        database.execSQL("CREATE TABLE budgets (month TEXT PRIMARY KEY NOT NULL)")

        DailyDatabase.MIGRATION_12_13.migrate(database)

        assertFalse(database.hasTable("transactions"))
        assertFalse(database.hasTable("budgets"))
        assertTrue(database.hasTable("courses"))
        assertTrue(database.hasRow("SELECT courseName FROM courses WHERE courseName = '数据库'"))
        database.close()
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

    private fun createVersion12Database() = FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(12) {
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

    private fun deleteDatabaseFiles() {
        context.deleteDatabase(databaseName)
        context.getDatabasePath("$databaseName-wal").delete()
        context.getDatabasePath("$databaseName-shm").delete()
        context.getDatabasePath("$databaseName-journal").delete()
    }
}
