package com.daily.life.feature.schedule

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import com.daily.life.core.calendar.SystemCalendarScheduleReader

/** Reads only explicit holiday and make-up-workday markers exposed by the user's system Calendar. */
internal class SystemCalendarDayBadgeReader(
    private val reader: SystemCalendarScheduleReader,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    suspend fun readMonth(month: LocalDate): Map<LocalDate, ScheduleCalendarBadge> =
        reader.readBetween(month.withDayOfMonth(1), month.withDayOfMonth(1).plusMonths(1).minusDays(1))
            .associate { day ->
                day.date to when (day.kind) {
                    com.daily.life.core.calendar.SystemCalendarSpecialDayKind.Holiday -> ScheduleCalendarBadge.Holiday
                    com.daily.life.core.calendar.SystemCalendarSpecialDayKind.MakeupWorkday -> ScheduleCalendarBadge.AdjustedWorkday
                }
            }

    companion object {
        fun from(context: Context): SystemCalendarDayBadgeReader =
            SystemCalendarDayBadgeReader(SystemCalendarScheduleReader.from(context))
    }
}
