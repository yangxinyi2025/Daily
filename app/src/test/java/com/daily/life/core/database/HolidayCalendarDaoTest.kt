package com.daily.life.core.database

import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.calendar.CalendarDayKind
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
class HolidayCalendarDaoTest {
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
    fun replacingEventsForOneSourceDoesNotDeleteAnotherSource() = runTest {
        val dao = database.holidayCalendarDao()
        val sourceA = HolidayCalendarSourceEntity(
            id = "builtin-cn",
            name = "Built-in",
            url = "content://builtin/cn",
            builtIn = true,
            enabled = true
        )
        val sourceB = HolidayCalendarSourceEntity(
            id = "custom-1",
            name = "Custom",
            url = "https://example.com/calendar.ics",
            builtIn = false,
            enabled = true
        )
        dao.upsertSource(sourceA)
        dao.upsertSource(sourceB)
        dao.replaceEventsForSource(
            sourceA.id,
            listOf(
                event(
                    sourceId = sourceA.id,
                    eventKey = "a-1",
                    startDate = LocalDate.of(2026, 10, 1),
                    endDateInclusive = LocalDate.of(2026, 10, 1),
                    label = "国庆"
                )
            )
        )
        dao.replaceEventsForSource(
            sourceB.id,
            listOf(
                event(
                    sourceId = sourceB.id,
                    eventKey = "b-1",
                    startDate = LocalDate.of(2026, 10, 2),
                    endDateInclusive = LocalDate.of(2026, 10, 2),
                    label = "自定义"
                )
            )
        )

        dao.replaceEventsForSource(
            sourceA.id,
            listOf(
                event(
                    sourceId = sourceA.id,
                    eventKey = "a-2",
                    startDate = LocalDate.of(2026, 10, 3),
                    endDateInclusive = LocalDate.of(2026, 10, 3),
                    label = "调休"
                )
            )
        )

        val saved = dao.findEventsBetween(
            start = LocalDate.of(2026, 10, 1),
            end = LocalDate.of(2026, 10, 4)
        )

        assertEquals(
            mapOf("a-2" to sourceA.id, "b-1" to sourceB.id),
            saved.associate { row -> row.eventKey to row.sourceId }
        )
    }

    @Test
    fun dayOverrideUpsertsByDate() = runTest {
        val dao = database.holidayCalendarDao()
        val first = CalendarDayOverrideEntity(
            date = LocalDate.of(2026, 10, 8),
            targetKind = CalendarDayKind.MAKEUP_WORKDAY,
            note = "节后补班",
            createdAt = 1L,
            updatedAt = 1L
        )

        dao.upsertDayOverrides(listOf(first))
        dao.upsertDayOverrides(
            listOf(
                first.copy(
                    targetKind = CalendarDayKind.HOLIDAY_REST,
                    note = "手动修正",
                    updatedAt = 2L
                )
            )
        )

        val saved = dao.observeDayOverridesBetween(
            start = LocalDate.of(2026, 10, 1),
            end = LocalDate.of(2026, 10, 31)
        ).first()

        assertEquals(1, saved.size)
        assertEquals(CalendarDayKind.HOLIDAY_REST, saved.first().targetKind)
        assertEquals("手动修正", saved.first().note)
        assertEquals(1L, saved.first().createdAt)
        assertEquals(2L, saved.first().updatedAt)
    }

    @Test
    fun deletingOneOverrideDateKeepsOtherDates() = runTest {
        val dao = database.holidayCalendarDao()
        val firstDate = LocalDate.of(2026, 10, 8)
        val secondDate = LocalDate.of(2026, 10, 9)
        dao.upsertDayOverrides(
            listOf(
                CalendarDayOverrideEntity(
                    date = firstDate,
                    targetKind = CalendarDayKind.MAKEUP_WORKDAY,
                    note = "第一天",
                    createdAt = 1L,
                    updatedAt = 1L
                ),
                CalendarDayOverrideEntity(
                    date = secondDate,
                    targetKind = CalendarDayKind.HOLIDAY_REST,
                    note = "第二天",
                    createdAt = 2L,
                    updatedAt = 2L
                )
            )
        )

        dao.deleteDayOverrides(listOf(firstDate))

        val saved = dao.observeDayOverridesBetween(
            start = LocalDate.of(2026, 10, 1),
            end = LocalDate.of(2026, 10, 31)
        ).first()

        assertEquals(listOf(secondDate), saved.map(CalendarDayOverrideEntity::date))
        assertEquals(listOf("第二天"), saved.map(CalendarDayOverrideEntity::note))
    }

    private fun event(
        sourceId: String,
        eventKey: String,
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        label: String
    ) = HolidayCalendarEventEntity(
        sourceId = sourceId,
        eventKey = eventKey,
        startDate = startDate,
        endDateInclusive = endDateInclusive,
        summary = label,
        description = null,
        kind = CalendarDayKind.HOLIDAY_REST,
        sourceDayOfWeek = null,
        sourceDate = null,
        fetchedAt = 1L
    )
}
