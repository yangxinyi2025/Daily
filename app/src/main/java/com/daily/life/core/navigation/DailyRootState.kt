package com.daily.life.core.navigation

data class DailyRootState(
    val currentDestination: DailyDestination,
    val onDestinationSelected: (DailyDestination) -> Unit
)
