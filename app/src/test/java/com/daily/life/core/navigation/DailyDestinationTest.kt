package com.daily.life.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyDestinationTest {
    @Test
    fun primaryDestinationsContainOnlyFourCoreRoutes() {
        assertEquals(
            listOf("home", "timetable", "schedule", "health"),
            DailyDestination.primaryDestinations.map { it.route }
        )
    }

    @Test
    fun settingsDestinationHasStableRoute() {
        assertEquals("settings", DailyDestination.Settings.route)
    }
}
