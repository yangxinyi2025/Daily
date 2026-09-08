package com.daily.life.feature.health

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daily.life.core.designsystem.DailyDatePickerField
import com.daily.life.core.designsystem.SkyCoolBorder
import com.daily.life.core.designsystem.SkyInk
import com.daily.life.core.designsystem.SkyMutedText
import com.daily.life.core.designsystem.SkyPrimary
import com.daily.life.core.designsystem.SkySuccess
import com.daily.life.core.designsystem.SkySurface
import com.daily.life.core.designsystem.SkyWarm
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HealthDashboardScreen(
    state: HealthState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRecordWeight: (Double, LocalDateTime) -> Unit,
    onSetTargetWeight: (Double?) -> Unit,
    onRecordPeriod: (LocalDate, LocalDate) -> Unit,
    onUpdatePeriod: (PeriodRecord) -> Unit,
    onDeletePeriod: (Long) -> Unit
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val presentation = healthDashboardPresentation(state, zoneId)
    val overview = healthOverviewDisplay(state, presentation)
    var activeSheet by remember { mutableStateOf<HealthSheet?>(null) }
    var recordingDateTime by remember { mutableStateOf(LocalDateTime.now()) }
    var editedPeriod by remember { mutableStateOf<PeriodRecord?>(null) }
    var selectedRange by rememberSaveable { mutableStateOf(WeightTrendRange.Days30) }
    var showingHistory by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now(zoneId)
    val trendPoints = remember(state.weights, selectedRange, today, zoneId) {
        selectWeightTrendPoints(state.weights, selectedRange, today, zoneId)
    }

    if (showingHistory) {
        HealthHistoryScreen(
            weights = state.weights.sortedByDescending(WeightRecord::recordedAt),
            periodHistory = presentation.history,
            zoneId = zoneId,
            onBack = { showingHistory = false },
            onEditPeriod = { record ->
                editedPeriod = record
                activeSheet = HealthSheet.Period
            },
            onDeletePeriod = onDeletePeriod
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFF6F8E7)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp, 18.dp, 18.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { HealthHeader(onOpenHistory = { showingHistory = true }) }
            item {
                WeightOverviewCard(
                    currentWeight = overview.currentWeight,
                    targetWeight = overview.targetWeight,
                    trendPoints = trendPoints,
                    selectedRange = selectedRange,
                    onRangeSelected = { selectedRange = it },
                    onRecordWeight = { recordingDateTime = LocalDateTime.now(); activeSheet = HealthSheet.Weight },
                    onEditTarget = { activeSheet = HealthSheet.Target },
                    onEditWeight = { recordingDateTime = LocalDateTime.now(); activeSheet = HealthSheet.Weight }
                )
            }
            item {
                PeriodOverviewCard(
                    lastPeriod = overview.lastPeriod,
                    nextPeriod = overview.nextPeriod,
                    countdownDays = daysUntilPeriod(state.nextPeriodStart, today),
                    onEditPeriod = { editedPeriod = null; activeSheet = HealthSheet.Period }
                )
            }
            state.statusMessage?.let { message -> item { Text(message, color = SkySuccess, fontSize = 14.sp) } }
            state.errorMessage?.let { message -> item { Text(message, color = SkyWarm, fontSize = 14.sp) } }
        }
    }

    activeSheet?.let { sheet ->
        HealthBottomSheet(onDismiss = { activeSheet = null }) {
            when (sheet) {
                HealthSheet.Weight -> HealthWeightEditorContent(
                    recordingDateTime = recordingDateTime,
                    onRecordingDateTimeChange = { recordingDateTime = it },
                    onSave = { weight -> onRecordWeight(weight, recordingDateTime); activeSheet = null },
                    onDismiss = { activeSheet = null }
                )
                HealthSheet.Period -> HealthPeriodEditorContent(
                    record = editedPeriod,
                    onSave = { start, end ->
                        editedPeriod?.let { onUpdatePeriod(it.copy(startDate = start, endDate = end)) }
                            ?: onRecordPeriod(start, end)
                        activeSheet = null
                    },
                    onDismiss = { activeSheet = null }
                )
                HealthSheet.Target -> HealthTargetEditorContent(
                    targetWeight = state.targetWeightJin,
                    onSave = { onSetTargetWeight(it); activeSheet = null },
                    onDismiss = { activeSheet = null }
                )
            }
        }
    }
}

private enum class HealthSheet { Weight, Period, Target }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HealthBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SkySurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun HealthSheetHeader(title: String, onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), color = SkyInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = SkyMutedText) }
    }
}

@Composable
private fun HealthWeightEditorContent(
    recordingDateTime: LocalDateTime,
    onRecordingDateTimeChange: (LocalDateTime) -> Unit,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current
    HealthSheetHeader("记录体重", onDismiss)
    Text("记录日期与时间", color = SkyMutedText, fontSize = 14.sp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = {
                DatePickerDialog(context, { _, year, month, day ->
                    onRecordingDateTimeChange(recordingDateTime.withYear(year).withMonth(month + 1).withDayOfMonth(day))
                }, recordingDateTime.year, recordingDateTime.monthValue - 1, recordingDateTime.dayOfMonth).show()
            },
            modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, SkyCoolBorder)
        ) { Text(recordingDateTime.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), color = SkyInk, fontSize = 15.sp) }
        OutlinedButton(
            onClick = {
                TimePickerDialog(context, { _, hour, minute -> onRecordingDateTimeChange(recordingDateTime.withHour(hour).withMinute(minute)) }, recordingDateTime.hour, recordingDateTime.minute, true).show()
            },
            modifier = Modifier.weight(0.62f).height(50.dp), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, SkyCoolBorder)
        ) { Text(recordingDateTime.format(DateTimeFormatter.ofPattern("HH:mm")), color = SkyInk, fontSize = 15.sp) }
    }
    OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { Text("当前体重（斤）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(18.dp))
    Button(onClick = { input.toDoubleOrNull()?.let(onSave) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary, contentColor = Color.White)) { Text("保存", fontSize = 16.sp) }
}

@Composable
private fun HealthPeriodEditorContent(record: PeriodRecord?, onSave: (LocalDate, LocalDate) -> Unit, onDismiss: () -> Unit) {
    var startDate by remember(record) { mutableStateOf(record?.startDate ?: LocalDate.now()) }
    var endDate by remember(record) { mutableStateOf(record?.endDate ?: LocalDate.now()) }
    HealthSheetHeader(if (record == null) "记录本次经期" else "编辑经期记录", onDismiss)
    DailyDatePickerField(startDate.toString(), "开始日期", onDateSelected = { startDate = it })
    DailyDatePickerField(endDate.toString(), "结束日期", onDateSelected = { endDate = it })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { onSave(startDate, endDate) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = SkyWarm, contentColor = Color.White)) { Text("保存", fontSize = 16.sp) }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, SkyCoolBorder)) { Text("取消", color = SkyMutedText, fontSize = 16.sp) }
    }
}

@Composable
private fun HealthTargetEditorContent(targetWeight: Double?, onSave: (Double?) -> Unit, onDismiss: () -> Unit) {
    var input by remember(targetWeight) { mutableStateOf(targetWeight?.let(::formatHealthWeight).orEmpty()) }
    HealthSheetHeader("修改目标体重", onDismiss)
    OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), label = { Text("目标体重（斤）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { onSave(input.toDoubleOrNull()) }, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary, contentColor = Color.White)) { Text("保存", fontSize = 16.sp) }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, SkyCoolBorder)) { Text("取消", color = SkyMutedText, fontSize = 16.sp) }
    }
}

private fun formatHealthWeight(value: Double): String = String.format(Locale.US, "%.1f", value)
