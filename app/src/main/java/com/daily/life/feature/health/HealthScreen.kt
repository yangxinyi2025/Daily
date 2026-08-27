package com.daily.life.feature.health

import androidx.compose.runtime.Composable
import java.time.LocalDate

@Composable
fun HealthScreen(
    state: HealthState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onSelectTab: (HealthTab) -> Unit,
    onRecordWeight: (Double) -> Unit,
    onSetTargetWeight: (Double?) -> Unit,
    onRecordPeriod: (LocalDate, LocalDate) -> Unit,
    onUpdatePeriod: (PeriodRecord) -> Unit,
    onDeletePeriod: (Long) -> Unit
) {
    HealthDashboardScreen(
        state = state,
        onPreviousMonth = onPreviousMonth,
        onNextMonth = onNextMonth,
        onCurrentMonth = onCurrentMonth,
        onRecordWeight = onRecordWeight,
        onSetTargetWeight = onSetTargetWeight,
        onRecordPeriod = onRecordPeriod,
        onUpdatePeriod = onUpdatePeriod,
        onDeletePeriod = onDeletePeriod
    )
}
