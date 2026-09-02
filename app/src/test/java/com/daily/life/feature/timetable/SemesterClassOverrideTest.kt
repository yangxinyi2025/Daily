package com.daily.life.feature.timetable

import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.SemesterClassOverrideEntity
import com.daily.life.core.database.SemesterEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SemesterClassOverrideTest {
    private lateinit var database: DailyDatabase

    @Before
    fun setUp() {
        database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun overridesAreScopedBySemesterAndUpsertByActualDate() = runTest {
        val firstSemester = database.semesterDao().insert(
            SemesterEntity(name = "第一学期", startDate = LocalDate.of(2026, 9, 1), createdAt = 1L)
        )
        val secondSemester = database.semesterDao().insert(
            SemesterEntity(name = "第二学期", startDate = LocalDate.of(2027, 2, 1), createdAt = 2L)
        )
        val date = LocalDate.of(2026, 9, 5)
        val dao = database.semesterClassOverrideDao()
        dao.upsertAll(
            listOf(
                SemesterClassOverrideEntity(firstSemester, date, ClassOverride.HAS_CLASS.name, 1L),
                SemesterClassOverrideEntity(secondSemester, date, ClassOverride.NO_CLASS.name, 2L)
            )
        )
        dao.upsertAll(listOf(SemesterClassOverrideEntity(firstSemester, date, ClassOverride.NO_CLASS.name, 3L)))

        assertEquals(ClassOverride.NO_CLASS.name, dao.findBySemester(firstSemester).single().overrideKind)
        assertEquals(ClassOverride.NO_CLASS.name, dao.observeBySemester(secondSemester).first().single().overrideKind)
    }
}
