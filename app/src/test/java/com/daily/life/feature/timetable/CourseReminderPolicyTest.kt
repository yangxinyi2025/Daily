package com.daily.life.feature.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseReminderPolicyTest {
    @Test
    fun resolvesGlobalCustomAndDisabledOverrides() {
        assertEquals(10, CourseReminderPolicy.resolve(10, CourseReminderOverride.FollowGlobal))
        assertEquals(25, CourseReminderPolicy.resolve(10, CourseReminderOverride.Custom(25)))
        assertNull(CourseReminderPolicy.resolve(10, CourseReminderOverride.Disabled))
    }
}
