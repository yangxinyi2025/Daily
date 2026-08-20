package com.daily.life.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard
import com.daily.life.core.designsystem.DailyPrimaryAction
import com.daily.life.core.navigation.DailyDestination

@Composable
fun HomeScreen(
    state: HomeState,
    onOpenSettings: () -> Unit,
    onDestinationSelected: (DailyDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = state.greeting, style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = state.dateLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "打开设置"
                )
            }
        }

        Text(text = "今日速览", style = MaterialTheme.typography.titleLarge)
        state.cards.forEach { card ->
            HomeSummaryCard(card, onDestinationSelected)
        }
    }
}

@Composable
private fun HomeSummaryCard(
    card: HomeCardState,
    onDestinationSelected: (DailyDestination) -> Unit
) {
    DailyCard {
        Text(text = card.title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = card.value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val destination = card.destination
        val actionLabel = card.actionLabel
        if (destination != null && actionLabel != null) {
            DailyPrimaryAction(
                text = actionLabel,
                onClick = { onDestinationSelected(destination) }
            )
        }
    }
}
