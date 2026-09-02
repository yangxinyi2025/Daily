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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daily.life.R
import com.daily.life.core.navigation.DailyDestination

private val BottomNavActive = Color(0xFF204A0A)
private val BottomNavMuted = Color(0xFF6E8466)

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
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFDDEFC8)
            ) {}
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FigmaNavigationIcon(destination = destination)
            Text(
                text = destination.label,
                modifier = Modifier.padding(top = 2.dp),
                color = if (selected) BottomNavActive else BottomNavMuted,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun FigmaNavigationIcon(destination: DailyDestination) {
    Image(
        painter = painterResource(destination.cuteIconResource()),
        contentDescription = destination.label,
        modifier = Modifier.size(26.dp)
    )
}

@DrawableRes
private fun DailyDestination.cuteIconResource(): Int = when (this) {
    DailyDestination.Home -> R.drawable.nav_cute_home
    DailyDestination.Timetable -> R.drawable.nav_cute_timetable
    DailyDestination.Schedule -> R.drawable.nav_cute_schedule
    DailyDestination.Health -> R.drawable.nav_cute_health
    DailyDestination.Bill -> R.drawable.nav_cute_bill
    DailyDestination.Settings -> R.drawable.nav_cute_home
}
