package com.daily.life.feature.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daily.life.core.database.ReminderMode
import com.daily.life.core.designsystem.DailyCard
import com.daily.life.core.designsystem.DailyPageScaffold

@Composable
fun ScheduleScreen(
    state: ScheduleState,
    onViewModeChange: (ScheduleViewMode) -> Unit,
    onDateSelected: (java.time.LocalDate) -> Unit,
    onCreate: () -> Unit,
    onQuickCreate: (ScheduleQuickAction) -> Unit,
    onEdit: (ScheduleEvent) -> Unit,
    onDelete: (Long) -> Unit,
    onSaveEditor: () -> Unit,
    onDismissEditor: () -> Unit,
    onEditorChange: (ScheduleEditorState) -> Unit
) {
    DailyPageScaffold(title = "日程") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScheduleViewMode.values().forEach { mode ->
                        if (mode == state.viewMode) {
                            Button(onClick = { onViewModeChange(mode) }) { Text(mode.label()) }
                        } else {
                            OutlinedButton(onClick = { onViewModeChange(mode) }) { Text(mode.label()) }
                        }
                    }
                }
            }
            item {
                DailyCard {
                    Text(text = "日期：${state.selectedDate}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onDateSelected(state.selectedDate.minusDays(1)) }) {
                            Text("前一天")
                        }
                        OutlinedButton(onClick = { onDateSelected(java.time.LocalDate.now()) }) {
                            Text("今天")
                        }
                        OutlinedButton(onClick = { onDateSelected(state.selectedDate.plusDays(1)) }) {
                            Text("后一天")
                        }
                    }
                }
            }
            item {
                DailyCard {
                    Text(text = "快捷创建")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScheduleQuickAction.values().forEach { action ->
                            OutlinedButton(onClick = { onQuickCreate(action) }) {
                                Text(action.title)
                            }
                        }
                    }
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                        Text("新建日程")
                    }
                }
            }
            item {
                Text(text = "时间线")
            }
            if (state.events.isEmpty()) {
                item {
                    DailyCard { Text("这段时间还没有日程") }
                }
            } else {
                items(state.events, key = ScheduleEvent::id) { event ->
                    DailyCard(
                        modifier = Modifier.clickable { onEdit(event) }
                    ) {
                        Text(text = event.title)
                        Text(text = event.eventAt.atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString())
                        Text(
                            text = if (event.reminderMode == ReminderMode.ALARM) "闹钟提醒" else "消息提醒"
                        )
                        OutlinedButton(onClick = { onDelete(event.id) }) { Text("删除") }
                    }
                }
            }
            state.statusMessage?.let { message -> item { Text(message) } }
            state.editor?.let { editor ->
                item {
                    ScheduleEditorScreen(
                        state = editor,
                        onChange = onEditorChange,
                        onSave = onSaveEditor,
                        onDismiss = onDismissEditor,
                        onDelete = editor.id?.let { id -> { onDelete(id); onDismissEditor() } }
                    )
                }
            }
        }
    }
}

private fun ScheduleViewMode.label(): String = when (this) {
    ScheduleViewMode.MONTH -> "月"
    ScheduleViewMode.WEEK -> "周"
    ScheduleViewMode.DAY -> "日"
}
