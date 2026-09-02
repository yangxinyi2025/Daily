package com.daily.life.core.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarPermissionPolicyTest {
    @Test
    fun requiresBothReadAndWritePermissionsBeforeCalendarSync() {
        assertTrue(requiresCalendarPermission(readGranted = false, writeGranted = false))
        assertTrue(requiresCalendarPermission(readGranted = true, writeGranted = false))
        assertTrue(requiresCalendarPermission(readGranted = false, writeGranted = true))
        assertFalse(requiresCalendarPermission(readGranted = true, writeGranted = true))
    }
}
