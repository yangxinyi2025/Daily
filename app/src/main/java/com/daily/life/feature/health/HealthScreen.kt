package com.daily.life.feature.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.daily.life.core.database.ActivityType
import com.daily.life.core.designsystem.DailyCard
import com.daily.life.core.designsystem.DailyEmptyState
import com.daily.life.core.designsystem.DailyPageScaffold
import com.daily.life.core.designsystem.DailyPrimaryAction
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HealthScreen(
    state: HealthState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onRecordWeight: (Double) -> Unit,
    onSetTargetWeight: (Double?) -> Unit,
    onReadActivity: () -> Unit,
    onRegenerateReport: () -> Unit
) {
    var showWeightEditor by remember { mutableStateOf(false) }
    DailyPageScaffold(title = "健康") {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("健康页面")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPreviousMonth) { Text("上月") }
                    Button(onClick = onCurrentMonth) { Text(state.selectedMonth.toString()) }
                    OutlinedButton(onClick = onNextMonth) { Text("下月") }
                }
            }
            item {
                DailyCard {
                    Text("体重")
                    Text(
                        text = state.weights.firstOrNull()?.let { "最近 ${formatJin(it.weightJin)} 斤" }
                            ?: "还没有体重记录"
                    )
                    DailyPrimaryAction(
                        text = "记录体重",
                        onClick = { showWeightEditor = !showWeightEditor }
                    )
                }
            }
            if (showWeightEditor) {
                item {
                    WeightEditor(
                        onSave = {
                            onRecordWeight(it)
                            showWeightEditor = false
                        }
                    )
                }
            }
            item {
                DailyCard {
                    Text("目标体重")
                    Text(state.targetWeightJin?.let { "${formatJin(it)} 斤" } ?: "尚未设置")
                    WeightTargetEditor(onSetTargetWeight = onSetTargetWeight)
                }
            }
            item {
                DailyCard {
                    Text("最近 30 天体重")
                    WeightLineChart(points = state.report?.recentWeights.orEmpty())
                }
            }
            item {
                DailyCard {
                    Text("活动数据")
                    Text("步行 ${state.report?.walkingSteps ?: 0L} 步")
                    Text("跑步 ${state.report?.runningSteps ?: 0L} 步")
                    OutlinedButton(onClick = onReadActivity, modifier = Modifier.fillMaxWidth()) {
                        Text("读取手机健康数据")
                    }
                    state.sourceMessage?.let { Text(it) }
                    state.sourceAvailability.forEach { availability ->
                        Text("${availability.label}：${availability.detail}")
                    }
                }
            }
            item {
                Text("本地月报")
                when (val report = state.report) {
                    null -> DailyEmptyState(
                        title = "月报准备中",
                        message = "本地记录会自动生成月报"
                    )
                    else -> ReportCard(report = report, onRegenerateReport = onRegenerateReport)
                }
            }
            if (state.activities.isNotEmpty()) {
                item { Text("活动明细") }
                items(state.activities, key = { it.id }) { activity ->
                    DailyCard {
                        Text(if (activity.activityType == ActivityType.WALK) "步行" else "跑步")
                        Text(activity.recordedAt.atZone(java.time.ZoneId.systemDefault()).format(DATE_TIME_FORMATTER))
                        Text("${activity.steps ?: 0L} 步")
                    }
                }
            }
            state.statusMessage?.let { item { Text(it) } }
            state.errorMessage?.let { item { Text(it) } }
            if (state.isLoading) item { Text("正在更新健康数据…") }
        }
    }
}

@Composable
private fun WeightTargetEditor(onSetTargetWeight: (Double?) -> Unit) {
    var input by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("设置斤数") }
        )
        OutlinedButton(onClick = { onSetTargetWeight(input.toDoubleOrNull()) }) {
            Text("保存")
        }
    }
}

@Composable
private fun ReportCard(report: LocalReport, onRegenerateReport: () -> Unit) {
    DailyCard {
        Text(report.weightTrendSummary)
        Text(report.activitySummary)
        report.targetDifferenceJin?.let { difference ->
            Text("距离目标 ${formatJin(difference)} 斤")
        }
        OutlinedButton(onClick = onRegenerateReport, modifier = Modifier.fillMaxWidth()) {
            Text("重新生成月报")
        }
    }
}

@Composable
private fun WeightLineChart(points: List<WeightPoint>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(vertical = 12.dp)
    ) {
        if (points.size < 2) return@Canvas
        val min = points.minOf { it.weightJin }
        val max = points.maxOf { it.weightJin }
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = size.width * index / (points.lastIndex.coerceAtLeast(1))
            val y = size.height - ((point.weightJin - min) / range * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = Color(0xFF5E8CFF), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
    }
}

private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun formatJin(value: Double): String = String.format(Locale.US, "%.1f", value)
