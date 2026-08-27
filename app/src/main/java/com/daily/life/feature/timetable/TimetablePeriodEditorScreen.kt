package com.daily.life.feature.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard

@Composable
fun TimetablePeriodEditorScreen(
    state: TimetablePeriodEditorState,
    onRowChange: (TimetablePeriodTimeRowState) -> Unit,
    onRestoreDefaults: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("节次时间", style = MaterialTheme.typography.headlineLarge)
        Text("每个学期可以有自己的上课时间。课程节次与周次不会被修改。", style = MaterialTheme.typography.bodyMedium)
        DailyCard {
            state.rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("第 ${row.period} 节", modifier = Modifier.padding(top = 16.dp))
                    OutlinedTextField(row.start, { onRowChange(row.copy(start = it)) }, Modifier.weight(1f), label = { Text("开始") }, singleLine = true)
                    OutlinedTextField(row.end, { onRowChange(row.copy(end = it)) }, Modifier.weight(1f), label = { Text("结束") }, singleLine = true)
                }
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onRestoreDefaults, modifier = Modifier.fillMaxWidth()) { Text("恢复默认时间") }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("保存") }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}
