package com.daily.life.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyDestinationTest {
    @Test
    fun primaryDestinationsHaveStableRoutes() {
        assertEquals("home", DailyDestination.Home.route)
        assertEquals("timetable", DailyDestination.Timetable.route)
        assertEquals("schedule", DailyDestination.Schedule.route)
        assertEquals("health", DailyDestination.Health.route)
        assertEquals("bill", DailyDestination.Bill.route)
    }

    @Test
    fun settingsDestinationHasStableRoute() {
        assertEquals("settings", DailyDestination.Settings.route)
    }
}
