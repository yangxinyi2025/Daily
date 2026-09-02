package com.daily.life.core.navigation

sealed class DailyDestination(
    val route: String,
    val label: String
) {
    data object Home : DailyDestination(route = "home", label = "首页")
    data object Timetable : DailyDestination(route = "timetable", label = "课表")
    data object Schedule : DailyDestination(route = "schedule", label = "日程")
    data object Health : DailyDestination(route = "health", label = "健康")
    data object Bill : DailyDestination(route = "bill", label = "账单")
    data object Settings : DailyDestination(route = "settings", label = "设置")

    companion object {
        val primaryDestinations = listOf(Home, Timetable, Schedule, Health, Bill)
        val allDestinations = primaryDestinations + Settings

        fun fromRoute(route: String?): DailyDestination {
            return allDestinations.firstOrNull { it.route == route } ?: Home
        }
    }
}
