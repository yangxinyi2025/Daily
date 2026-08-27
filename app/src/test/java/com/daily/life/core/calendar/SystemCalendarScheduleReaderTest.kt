package com.daily.life.core.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCalendarScheduleReaderTest {
    @Test
    fun readsTheInclusiveDateRangeAndReturnsParsedDays() = runTest {
        var requestedRange: LongRange? = null
        val reader = SystemCalendarScheduleReader(
            zone = ZoneOffset.UTC,
            canReadCalendar = { true },
            queryEvents = { startMillis, endMillis ->
                requestedRange = startMillis..endMillis
                listOf(
                    SystemCalendarScheduleEvent(
                        startAt = Instant.parse("2026-10-10T00:00:00Z"),
                        endAt = Instant.parse("2026-10-11T00:00:00Z"),
                        title = "补周五",
                        description = null
                    )
                )
            }
        )

        val days = reader.readBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

        assertEquals(LocalDate.of(2026, 10, 10), days.single().date)
        assertEquals(5, days.single().sourceDayOfWeek)
        assertTrue(requestedRange!!.first < requestedRange!!.last)
    }

    @Test
    fun returnsEmptyWhenCalendarReadPermissionIsDenied() = runTest {
        var queryCalled = false
        val reader = SystemCalendarScheduleReader(
            zone = ZoneOffset.UTC,
            canReadCalendar = { false },
            queryEvents = { _, _ ->
                queryCalled = true
                emptyList()
            }
        )

        val days = reader.readBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

        assertTrue(days.isEmpty())
        assertTrue(!queryCalled)
    }

    @Test
    fun queryFailuresAreSafeForImport() = runTest {
        val reader = SystemCalendarScheduleReader(
            zone = ZoneOffset.UTC,
            canReadCalendar = { true },
            queryEvents = { _, _ -> error("calendar unavailable") }
        )

        assertTrue(reader.readBetween(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).isEmpty())
    }
}
