package com.daily.life.feature.health

import androidx.compose.runtime.Composable
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun HealthScreen(
    state: HealthState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectTab: (HealthTab) -> Unit,
    onRecordWeight: (Double, LocalDateTime) -> Unit,
    onSetTargetWeight: (Double?) -> Unit,
    onRecordPeriod: (LocalDate, LocalDate) -> Unit,
    onUpdatePeriod: (PeriodRecord) -> Unit,
    onDeletePeriod: (Long) -> Unit
) {
    HealthDashboardScreen(
        state = state,
        onPreviousMonth = onPreviousMonth,
        onNextMonth = onNextMonth,
        onRecordWeight = onRecordWeight,
        onSetTargetWeight = onSetTargetWeight,
        onRecordPeriod = onRecordPeriod,
        onUpdatePeriod = onUpdatePeriod,
        onDeletePeriod = onDeletePeriod
    )
}
