package com.daily.life.feature.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.daily.life.core.designsystem.DailyDatePickerField
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun TimetableImportScreen(
    state: TimetableImportState,
    onChooseFile: () -> Unit,
    onSemesterInputChange: (String, String) -> Unit,
    onRowChange: (TimetableImportRowState) -> Unit,
    onPeriodTimeChange: (TimetablePeriodTimeRowState) -> Unit,
    onReplaceExistingChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onMakeupSourceChange: (LocalDate, Int?) -> Unit,
    onClassOverrideChange: (LocalDate, ClassOverride) -> Unit = { _, _ -> }
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

        if (state.calendarSpecialDays.isNotEmpty() || state.calendarReadWarning != null) {
            DailyCard {
                Text(text = "节假日与调休校准", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "节假日按系统日历隐藏课程；每个补班日请选择实际执行的课程日。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.calendarSpecialDays
                    .filter { it.kind == com.daily.life.core.calendar.SystemCalendarSpecialDayKind.Holiday }
                    .sortedBy { it.date }
                    .forEach { day ->
                        Text("${day.date.format(IMPORT_DATE_FORMATTER)} · 休 · ${day.label}")
                    }
                state.calendarAdjustmentChoices.forEach { choice ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${choice.actualDate.format(IMPORT_DATE_FORMATTER)} · ${choice.label}")
                            if (choice.selectedSourceDayOfWeek == null) {
                                Text(
                                    "请选择补课来源",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        MakeupSourcePicker(
                            selectedDay = choice.selectedSourceDayOfWeek,
                            options = choice.options,
                            onSelected = { day -> onMakeupSourceChange(choice.actualDate, day) }
                        )
                    }
                }
                state.classOverrideChoices.forEach { choice ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${choice.actualDate.format(IMPORT_DATE_FORMATTER)} · 课程覆盖", modifier = Modifier.weight(1f))
                        ClassOverridePicker(choice.override) { selected ->
                            onClassOverrideChange(choice.actualDate, selected)
                        }
                    }
                }
                state.calendarReadWarning?.let { warning ->
                    Text(warning, color = MaterialTheme.colorScheme.error)
                }
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
            DailyDatePickerField(
                value = state.semesterStartDate,
                label = "开始日期",
                onDateSelected = { date -> onSemesterInputChange(state.semesterName, date.toString()) },
                modifier = Modifier.testTag("timetable_import_semester_start")
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

        if (state.previewRows.isNotEmpty()) {
            DailyCard {
                Text(text = "节次时间核对", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (state.periodTimesDetectedFromPdf) "已从 PDF 识别部分时间，请确认后导入" else "未在 PDF 中识别到节次时间，请核对后导入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.periodTimes.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("第 ${row.period} 节", modifier = Modifier.padding(top = 16.dp))
                        OutlinedTextField(
                            value = row.start,
                            onValueChange = { onPeriodTimeChange(row.copy(start = it)) },
                            modifier = Modifier.weight(1f),
                            label = { Text("开始") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = row.end,
                            onValueChange = { onPeriodTimeChange(row.copy(end = it)) },
                            modifier = Modifier.weight(1f),
                            label = { Text("结束") },
                            singleLine = true
                        )
                    }
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
private fun MakeupSourcePicker(
    selectedDay: Int?,
    options: List<Int>,
    onSelected: (Int) -> Unit
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedDay?.let(::importDayLabel) ?: "请选择")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { day ->
                DropdownMenuItem(
                    text = { Text(importDayLabel(day)) },
                    onClick = {
                        expanded = false
                        onSelected(day)
                    }
                )
            }
        }
    }
}

@Composable
private fun ClassOverridePicker(
    selected: ClassOverride,
    onSelected: (ClassOverride) -> Unit
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(
                when (selected) {
                    ClassOverride.FOLLOW_CALENDAR -> "跟随日历"
                    ClassOverride.HAS_CLASS -> "有课"
                    ClassOverride.NO_CLASS -> "无课"
                }
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ClassOverride.values().forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                ClassOverride.FOLLOW_CALENDAR -> "跟随日历"
                                ClassOverride.HAS_CLASS -> "有课"
                                ClassOverride.NO_CLASS -> "无课"
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

private fun importDayLabel(day: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(day - 1) { "请选择" }

private val IMPORT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

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
