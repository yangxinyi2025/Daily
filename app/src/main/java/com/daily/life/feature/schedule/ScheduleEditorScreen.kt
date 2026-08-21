package com.daily.life.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daily.life.core.database.ReminderMode
import com.daily.life.core.designsystem.DailyCard
import com.daily.life.core.designsystem.DailyPrimaryAction

@Composable
fun ScheduleEditorScreen(
    state: ScheduleEditorState,
    onChange: (ScheduleEditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    DailyCard {
        Text(text = if (state.id == null) "新建日程" else "编辑日程")
        OutlinedTextField(
            value = state.title,
            onValueChange = { onChange(state.copy(title = it)) },
            label = { Text("标题") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.date,
                onValueChange = { onChange(state.copy(date = it)) },
                label = { Text("日期 yyyy-MM-dd") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.time,
                onValueChange = { onChange(state.copy(time = it)) },
                label = { Text("时间 HH:mm") },
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = state.reminderOffsetMinutes,
            onValueChange = { onChange(state.copy(reminderOffsetMinutes = it)) },
            label = { Text("提前提醒（分钟）") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderMode.values().forEach { mode ->
                if (state.reminderMode == mode) {
                    Button(onClick = { onChange(state.copy(reminderMode = mode)) }) {
                        Text(if (mode == ReminderMode.NOTIFICATION) "消息提醒" else "闹钟提醒")
                    }
                } else {
                    OutlinedButton(onClick = { onChange(state.copy(reminderMode = mode)) }) {
                        Text(if (mode == ReminderMode.NOTIFICATION) "消息提醒" else "闹钟提醒")
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.repeatYearly,
                onCheckedChange = { onChange(state.copy(repeatYearly = it)) }
            )
            Text("每年重复")
        }
        OutlinedTextField(
            value = state.notes,
            onValueChange = { onChange(state.copy(notes = it)) },
            label = { Text("消息/备注") },
            modifier = Modifier.fillMaxWidth()
        )
        state.errorMessage?.let { Text(text = it) }
        DailyPrimaryAction(text = "保存", onClick = onSave)
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("取消")
        }
        if (onDelete != null) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text("删除此日程")
            }
        }
    }
}
