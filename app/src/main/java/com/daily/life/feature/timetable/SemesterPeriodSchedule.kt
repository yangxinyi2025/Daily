package com.daily.life.feature.timetable

import java.time.LocalTime

data class SemesterPeriodTime(
    val period: Int,
    val startTime: LocalTime,
    val endTime: LocalTime
)

fun defaultSemesterPeriodTimes(): List<SemesterPeriodTime> = listOf(
    "08:00" to "08:45",
    "08:50" to "09:35",
    "09:50" to "10:35",
    "10:40" to "11:25",
    "11:30" to "12:15",
    "13:30" to "14:15",
    "14:20" to "15:05",
    "15:20" to "16:05",
    "16:10" to "16:55",
    "18:30" to "19:15",
    "19:20" to "20:05",
    "20:10" to "20:55"
).mapIndexed { index, (start, end) ->
    SemesterPeriodTime(
        period = index + 1,
        startTime = LocalTime.parse(start),
        endTime = LocalTime.parse(end)
    )
}

fun validateSemesterPeriodTimes(times: List<SemesterPeriodTime>): String? {
    val sorted = times.sortedBy(SemesterPeriodTime::period)
    if (sorted.map(SemesterPeriodTime::period) != (1..12).toList()) {
        return "请完整设置第 1 至 12 节课时间"
    }
    sorted.forEach { period ->
        if (!period.endTime.isAfter(period.startTime)) {
            return "第 ${period.period} 节结束时间必须晚于开始时间"
        }
    }
    sorted.zipWithNext().forEach { (current, next) ->
        if (next.startTime.isBefore(current.endTime)) {
            return "第 ${current.period} 与第 ${next.period} 节时间重叠"
        }
    }
    return null
}

fun periodLabels(times: List<SemesterPeriodTime>): List<TimetablePeriodLabel> =
    times.sortedBy(SemesterPeriodTime::period).map { period ->
        TimetablePeriodLabel(
            period = period.period,
            label = "${period.period}\n${period.startTime}\n${period.endTime}"
        )
    }
