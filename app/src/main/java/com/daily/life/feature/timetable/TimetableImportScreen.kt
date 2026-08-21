package com.daily.life.feature.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.DailyCard

@Composable
fun TimetableImportScreen(
    state: TimetableImportState,
    onChooseFile: () -> Unit,
    onSemesterInputChange: (String, String) -> Unit,
    onRowChange: (TimetableImportRowState) -> Unit,
    onReplaceExistingChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "导入课表", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "选择 PDF 后先检查并编辑预览；确认前不会修改现有课表。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        DailyCard {
            Button(
                onClick = onChooseFile,
                enabled = !state.isLoading,
                modifier = Modifier.testTag("timetable_import_choose")
            ) {
                Text(if (state.fileName == null) "选择 PDF 文件" else "重新选择 PDF")
            }
            state.fileName?.let { Text("文件：$it") }
            if (state.isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Text("正在解析…")
                }
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        DailyCard {
            Text(text = "学期信息", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.semesterName,
                onValueChange = { onSemesterInputChange(it, state.semesterStartDate) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timetable_import_semester_name"),
                label = { Text("学期名称") },
                singleLine = true
            )
            OutlinedTextField(
                value = state.semesterStartDate,
                onValueChange = { onSemesterInputChange(state.semesterName, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timetable_import_semester_start"),
                label = { Text("开始日期") },
                supportingText = { Text("格式：YYYY-MM-DD") },
                singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.replaceExisting,
                    onCheckedChange = onReplaceExistingChange
                )
                Text("替换同名学期的现有课程")
            }
        }

        if (state.warnings.isNotEmpty() || state.unsupportedRows.isNotEmpty()) {
            DailyCard {
                Text(text = "解析提示", style = MaterialTheme.typography.titleLarge)
                state.warnings.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
                state.unsupportedRows.forEach { row ->
                    Text(
                        text = "• ${row.reason}：${row.rawText}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        state.previewRows.forEach { row ->
            ImportPreviewRow(row = row, onRowChange = onRowChange)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .testTag("timetable_import_cancel")
            ) {
                Text("取消")
            }
            Button(
                onClick = onConfirm,
                enabled = state.canConfirm && !state.isLoading,
                modifier = Modifier
                    .weight(1f)
                    .testTag("timetable_import_confirm")
            ) {
                Text("确认导入")
            }
        }
    }
}

@Composable
private fun ImportPreviewRow(
    row: TimetableImportRowState,
    onRowChange: (TimetableImportRowState) -> Unit
) {
    DailyCard {
        Text(
            text = "预览课程 ${row.index + 1}${if (row.needsReview) " · 需检查" else ""}",
            style = MaterialTheme.typography.titleMedium,
            color = if (row.needsReview) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = row.courseName,
            onValueChange = { onRowChange(row.copy(courseName = it)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("timetable_import_course_${row.index}"),
            label = { Text("课程名") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = row.dayOfWeek,
                onValueChange = { onRowChange(row.copy(dayOfWeek = it)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("timetable_import_day_${row.index}"),
                label = { Text("星期") },
                singleLine = true
            )
            OutlinedTextField(
                value = row.periodRange,
                onValueChange = { onRowChange(row.copy(periodRange = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("节次") },
                singleLine = true
            )
        }
        OutlinedTextField(
            value = row.weekRuleText,
            onValueChange = { onRowChange(row.copy(weekRuleText = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("周次") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = row.location,
                onValueChange = { onRowChange(row.copy(location = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("地点") },
                singleLine = true
            )
            OutlinedTextField(
                value = row.teacher,
                onValueChange = { onRowChange(row.copy(teacher = it)) },
                modifier = Modifier.weight(1f),
                label = { Text("教师") },
                singleLine = true
            )
        }
        row.warnings.forEach { warning ->
            Text("• $warning", color = MaterialTheme.colorScheme.error)
        }
        Text(
            text = "原始行：${row.rawRow}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
