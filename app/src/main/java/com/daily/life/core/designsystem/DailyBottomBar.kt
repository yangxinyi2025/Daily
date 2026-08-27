package com.daily.life.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daily.life.R
import com.daily.life.core.navigation.DailyDestination

private val BottomNavPurple = SkyPrimary
private val BottomNavMuted = SkyMutedText

@Composable
fun DailyBottomBar(
    current: DailyDestination,
    onDestinationSelected: (DailyDestination) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp)
            .height(75.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 7.dp
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DailyDestination.primaryDestinations.forEach { destination ->
                FigmaNavigationItem(
                    destination = destination,
                    selected = current == destination,
                    onClick = { onDestinationSelected(destination) }
                )
            }
        }
    }
}

@Composable
private fun FigmaNavigationItem(
    destination: DailyDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(62.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            if (destination == DailyDestination.Home) {
                Image(
                    painter = painterResource(R.drawable.home_nav_selected),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(18.dp),
                    color = SkyPurpleSurface
                ) {}
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FigmaNavigationIcon(destination = destination, selected = selected)
            Text(
                text = destination.label,
                modifier = Modifier.padding(top = 2.dp),
                color = if (selected) BottomNavPurple else BottomNavMuted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun FigmaNavigationIcon(destination: DailyDestination, selected: Boolean) {
    if (selected || destination == DailyDestination.Home) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            modifier = Modifier.size(26.dp),
            tint = if (selected) BottomNavPurple else BottomNavMuted
        )
    } else {
        Image(
            painter = painterResource(destination.figmaIconResource()),
            contentDescription = destination.label,
            modifier = Modifier.size(26.dp)
        )
    }
}

@DrawableRes
private fun DailyDestination.figmaIconResource(): Int = when (this) {
    DailyDestination.Timetable -> R.drawable.nav_timetable
    DailyDestination.Schedule -> R.drawable.nav_schedule
    DailyDestination.Health -> R.drawable.nav_health
    DailyDestination.Bill -> R.drawable.nav_bill
    DailyDestination.Home,
    DailyDestination.Settings -> R.drawable.home_nav_selected
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
