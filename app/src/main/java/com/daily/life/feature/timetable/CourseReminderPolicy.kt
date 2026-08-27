package com.daily.life.feature.timetable

sealed interface CourseReminderOverride {
    data object FollowGlobal : CourseReminderOverride
    data object Disabled : CourseReminderOverride
    data class Custom(val minutesBefore: Int) : CourseReminderOverride
}

object CourseReminderPolicy {
    fun resolve(globalMinutes: Int?, override: CourseReminderOverride): Int? = when (override) {
        CourseReminderOverride.FollowGlobal -> globalMinutes
        CourseReminderOverride.Disabled -> null
        is CourseReminderOverride.Custom -> override.minutesBefore.coerceIn(1, 180)
    }
}
