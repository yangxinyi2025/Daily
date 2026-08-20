package com.daily.life.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.daily.life.core.navigation.DailyDestination

@Composable
fun DailyBottomBar(
    current: DailyDestination,
    onDestinationSelected: (DailyDestination) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        DailyDestination.primaryDestinations.forEach { destination ->
            NavigationBarItem(
                selected = current == destination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label
                    )
                },
                label = {
                    Text(text = destination.label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

internal val DailyDestination.icon: ImageVector
    get() = when (this) {
        DailyDestination.Home -> Icons.Outlined.Home
        DailyDestination.Timetable -> Icons.Outlined.CalendarMonth
        DailyDestination.Schedule -> Icons.Outlined.EventNote
        DailyDestination.Health -> Icons.Outlined.FavoriteBorder
        DailyDestination.Bill -> Icons.Outlined.ReceiptLong
        DailyDestination.Settings -> Icons.Outlined.EventNote
    }
