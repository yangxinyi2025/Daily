package com.daily.life.feature.timetable

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.NoOpReminderScheduler
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SemesterPeriodRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: DailyDatabase
    private lateinit var preferencesFile: File

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, DailyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesFile = File(context.cacheDir, "semester-period-repository.preferences_pb")
        preferencesFile.delete()
    }

    @After
    fun tearDown() {
        database.close()
        preferencesFile.delete()
    }

    @Test
    fun observingLegacySemesterPeriodTimesPersistsTwelveDefaults() = runBlocking {
        val semesterId = database.semesterDao().insert(
            SemesterEntity(
                name = "旧学期",
                startDate = LocalDate.of(2026, 9, 1),
                isCurrent = true,
                createdAt = 0L
            )
        )
        val repository = RoomTimetableRepository(
            database = database,
            preferences = DailyPreferences.create(
                scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
                produceFile = { preferencesFile }
            ),
            reminderScheduler = NoOpReminderScheduler
        )

        assertEquals(12, repository.observePeriodTimes(semesterId).first().size)
        assertEquals(12, database.semesterPeriodDao().findBySemester(semesterId).size)
    }
}
