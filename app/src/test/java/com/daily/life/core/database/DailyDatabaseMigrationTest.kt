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
