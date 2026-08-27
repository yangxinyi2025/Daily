package com.daily.life.feature.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyDatePickerField
import com.daily.life.core.designsystem.QuietSkyPageHeader
import com.daily.life.core.designsystem.SkyAccent
import com.daily.life.core.designsystem.SkyMutedText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun PeriodScreen(
    state: HealthState,
    onSelectTab: (HealthTab) -> Unit,
    onRecord: (LocalDate, LocalDate) -> Unit,
    onUpdate: (PeriodRecord) -> Unit,
    onDelete: (Long) -> Unit
) {
    var editingRecord by remember { mutableStateOf<PeriodRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 8.dp)
    ) {
        QuietSkyPageHeader(
            title = "健康",
            subtitle = "用自己的节奏，记录每一次周期。"
        )
        Spacer(Modifier.padding(top = 6.dp))
        HealthTabSelector(selected = state.selectedTab, onSelectTab = onSelectTab)
        Spacer(Modifier.padding(top = 6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                HealthCard {
                    Text("经期记录", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = state.nextPeriodStart?.let { "下次预计 ${it.format(PERIOD_DATE_FORMATTER)}" }
                            ?: "记录一次开始与结束日期后，会为你预测下次时间",
                        color = SkyMutedText
                    )
                    Text("当前按 ${state.menstrualCycleDays} 天周期预测", color = SkyAccent)
                    Button(onClick = {
                        editingRecord = null
                        showEditor = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("记录本次经期")
                    }
                }
            }
            if (showEditor) {
                item {
                    PeriodEditor(
                        record = editingRecord,
                        onSave = { startDate, endDate ->
                            editingRecord?.let { onUpdate(it.copy(startDate = startDate, endDate = endDate)) }
                                ?: onRecord(startDate, endDate)
                            showEditor = false
                        },
                        onDismiss = { showEditor = false }
                    )
                }
            }
            if (state.periodRecords.isEmpty()) {
                item { Text("还没有经期记录", color = SkyMutedText) }
            } else {
                item { Text("历史记录", fontWeight = FontWeight.SemiBold) }
                items(state.periodRecords, key = PeriodRecord::id) { record ->
                    HealthCard {
                        Text(
                            "${record.startDate.format(PERIOD_DATE_FORMATTER)} — ${record.endDate.format(PERIOD_DATE_FORMATTER)}"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    editingRecord = record
                                    showEditor = true
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("编辑") }
                            OutlinedButton(
                                onClick = { onDelete(record.id) },
                                modifier = Modifier.weight(1f)
                            ) { Text("删除") }
                        }
                    }
                }
            }
            state.statusMessage?.let { message -> item { Text(message, color = SkyMutedText) } }
            state.errorMessage?.let { message -> item { Text(message, color = androidx.compose.ui.graphics.Color(0xFFB3261E)) } }
        }
    }
}

@Composable
private fun PeriodEditor(
    record: PeriodRecord?,
    onSave: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var startDate by remember(record) { mutableStateOf(record?.startDate ?: LocalDate.now()) }
    var endDate by remember(record) { mutableStateOf(record?.endDate ?: LocalDate.now()) }
    HealthCard {
        Text(if (record == null) "记录本次经期" else "编辑经期记录", fontWeight = FontWeight.SemiBold)
        DailyDatePickerField(
            value = startDate.toString(),
            label = "开始日期",
            onDateSelected = { startDate = it }
        )
        DailyDatePickerField(
            value = endDate.toString(),
            label = "结束日期",
            onDateSelected = { endDate = it }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(startDate, endDate) }, modifier = Modifier.weight(1f)) { Text("保存") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
        }
    }
}

private val PERIOD_DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日")
