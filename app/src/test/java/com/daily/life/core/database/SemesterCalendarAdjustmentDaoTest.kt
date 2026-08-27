package com.daily.life.core.database

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SemesterCalendarAdjustmentDaoTest {
    private val database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storesAndReplacesAdjustmentBySemesterAndActualDate() = runTest {
        val dao = database.semesterCalendarAdjustmentDao()
        val first = SemesterCalendarAdjustmentEntity(
            semesterId = 7L,
            actualDate = LocalDate.of(2026, 10, 10),
            sourceDayOfWeek = 5,
            sourceDate = null,
            sourceLabel = "补周五",
            updatedAt = 1L
        )
        dao.upsertAll(listOf(first))
        dao.upsertAll(listOf(first.copy(sourceDayOfWeek = 4, sourceLabel = "补周四", updatedAt = 2L)))

        val saved = dao.findBySemester(7L).first()

        assertEquals(1, dao.findBySemester(7L).size)
        assertEquals(4, saved.sourceDayOfWeek)
        assertEquals("补周四", saved.sourceLabel)
    }

    @Test
    fun deletingSemesterAdjustmentsDoesNotDeleteAnotherSemester() = runTest {
        val dao = database.semesterCalendarAdjustmentDao()
        dao.upsertAll(
            listOf(
                adjustment(7L, LocalDate.of(2026, 10, 10)),
                adjustment(8L, LocalDate.of(2026, 10, 11))
            )
        )

        dao.deleteBySemester(7L)

        assertEquals(emptyList<SemesterCalendarAdjustmentEntity>(), dao.findBySemester(7L))
        assertEquals(1, dao.findBySemester(8L).size)
    }

    private fun adjustment(semesterId: Long, date: LocalDate) = SemesterCalendarAdjustmentEntity(
        semesterId = semesterId,
        actualDate = date,
        sourceDayOfWeek = 5,
        sourceDate = null,
        sourceLabel = "补周五",
        updatedAt = 1L
    )
}
