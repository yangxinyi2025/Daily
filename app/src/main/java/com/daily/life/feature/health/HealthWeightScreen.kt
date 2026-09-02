package com.daily.life.feature.health

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daily.life.core.designsystem.QuietSkyPageHeader
import com.daily.life.core.designsystem.QuietSkyListRow
import com.daily.life.core.designsystem.SkyAccent
import com.daily.life.core.designsystem.SkyCoolBorder
import com.daily.life.core.designsystem.SkyInk
import com.daily.life.core.designsystem.SkyMutedText
import com.daily.life.core.designsystem.SkyPrimary
import com.daily.life.core.designsystem.SkySurface
import com.daily.life.core.designsystem.SkySuccess
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun HealthWeightScreen(
    state: HealthState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onSelectTab: (HealthTab) -> Unit,
    onRecordWeight: (Double) -> Unit,
    onSetTargetWeight: (Double?) -> Unit
) {
    var showWeightEditor by remember { mutableStateOf(false) }
    var showTargetEditor by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val latest = state.weights.maxByOrNull { it.recordedAt }
    val delta = weightChangeFromLatest(state.weights)
    val monthWeightCount = state.weights.count {
        java.time.YearMonth.from(it.recordedAt.atZone(zone)) == state.selectedMonth
    }
    val recentPoints = state.weights
        .sortedBy { it.recordedAt }
        .takeLast(30)
        .map { record -> WeightPoint(record.recordedAt.atZone(zone).toLocalDate(), record.weightJin) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 8.dp)
    ) {
        QuietSkyPageHeader(
            title = "健康",
            subtitle = "把身体状态，慢慢记录下来。",
            actions = {
                IconButton(onClick = { showWeightEditor = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "记录体重")
                }
            }
        )
        Spacer(Modifier.height(12.dp))
        HealthTabSelector(selected = state.selectedTab, onSelectTab = onSelectTab)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                WeightOverviewCard(
                    state = state,
                    latestWeight = latest?.weightJin,
                    delta = delta,
                    monthWeightCount = monthWeightCount,
                    onPreviousMonth = onPreviousMonth,
                    onCurrentMonth = onCurrentMonth,
                    onNextMonth = onNextMonth
                )
            }
            item {
                WeightTrendCard(points = recentPoints)
            }
            item {
                WeightTargetCard(
                    targetWeight = state.targetWeightJin,
                    showEditor = showTargetEditor,
                    onToggleEditor = { showTargetEditor = !showTargetEditor },
                    onSetTargetWeight = onSetTargetWeight
                )
            }
            if (showWeightEditor) {
                item {
                    HealthWeightEditor(
                        onSave = {
                            onRecordWeight(it)
                            showWeightEditor = false
                        },
                        onDismiss = { showWeightEditor = false }
                    )
                }
            }
            state.statusMessage?.let { message -> item { Text(message, color = SkyMutedText) } }
            state.errorMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable
internal fun HealthTabSelector(
    selected: HealthTab,
    onSelectTab: (HealthTab) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HealthTab.values().forEach { tab ->
            val active = tab == selected
            Surface(
                modifier = Modifier.clickable { onSelectTab(tab) },
                shape = RoundedCornerShape(50.dp),
                color = if (active) SkyAccent.copy(alpha = 0.16f) else Color.Transparent,
                border = if (active) null else BorderStroke(1.dp, SkyCoolBorder)
            ) {
                Text(
                    text = tab.label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) SkyInk else SkyMutedText
                )
            }
        }
    }
}

@Composable
private fun WeightOverviewCard(
    state: HealthState,
    latestWeight: Double?,
    delta: Double?,
    monthWeightCount: Int,
    onPreviousMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    HealthCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${state.selectedMonth.monthValue}月体重概况",
                style = MaterialTheme.typography.labelMedium,
                color = SkyMutedText
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.ArrowBackIosNew, contentDescription = "上个月", modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = onCurrentMonth, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "回到本月", modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = onNextMonth, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.ArrowForwardIos, contentDescription = "下个月", modifier = Modifier.size(15.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (latestWeight == null) {
                Text(
                    text = "尚未记录",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = SkyMutedText
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatHealthJin(latestWeight),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = SkyInk
                    )
                    Text(" 斤", style = MaterialTheme.typography.labelMedium, color = SkyMutedText, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            TrendBadge(delta = delta)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                text = state.targetWeightJin?.let { "目标 ${formatHealthJin(it)} 斤" } ?: "目标体重尚未设置",
                fontSize = if (state.targetWeightJin == null) 16.sp else 12.sp,
                fontWeight = if (state.targetWeightJin == null) FontWeight.Medium else FontWeight.Normal,
                color = SkyMutedText
            )
            Text(
                text = if (monthWeightCount == 0) "本月尚未记录" else "本月记录 $monthWeightCount 次",
                fontSize = if (monthWeightCount == 0) 16.sp else 12.sp,
                fontWeight = if (monthWeightCount == 0) FontWeight.Medium else FontWeight.Normal,
                color = SkyMutedText
            )
        }
    }
}

@Composable
private fun TrendBadge(delta: Double?) {
    val text = when {
        delta == null -> "记录不足"
        delta < 0 -> "较上次 ↓ ${formatHealthJin(-delta)}斤"
        delta > 0 -> "较上次 ↑ ${formatHealthJin(delta)}斤"
        else -> "与上次持平"
    }
    Surface(
        color = if (delta != null && delta <= 0) SkySuccess.copy(alpha = 0.14f) else SkyAccent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (delta != null && delta <= 0) SkySuccess else SkyAccent
        )
    }
}

@Composable
private fun WeightTrendCard(points: List<WeightPoint>) {
    HealthCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("体重趋势", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("最近 30 天", style = MaterialTheme.typography.labelSmall, color = SkyMutedText)
        }
        if (points.size < 2) {
            QuietSkyListRow(
                icon = Icons.Outlined.FavoriteBorder,
                iconTint = SkyAccent,
                title = "记录两次体重后，这里会显示趋势",
                subtitle = "保持轻松，持续记录就好"
            )
        } else {
            WeightTrendChart(points = points)
        }
    }
}

@Composable
private fun WeightTrendChart(points: List<WeightPoint>) {
    val sorted = points.sortedBy { it.date }
    Canvas(modifier = Modifier.fillMaxWidth().height(170.dp).padding(top = 12.dp)) {
        val min = sorted.minOf { it.weightJin }
        val max = sorted.maxOf { it.weightJin }
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        repeat(3) { index ->
            val y = size.height * index / 2f
            drawLine(
                color = SkyCoolBorder.copy(alpha = 0.7f),
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 1f
            )
        }
        val path = Path()
        sorted.forEachIndexed { index, point ->
            val x = size.width * index / sorted.lastIndex.coerceAtLeast(1)
            val y = size.height - ((point.weightJin - min) / range * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = SkyAccent, style = Stroke(width = 4f))
        sorted.forEachIndexed { index, point ->
            val x = size.width * index / sorted.lastIndex.coerceAtLeast(1)
            val y = size.height - ((point.weightJin - min) / range * size.height).toFloat()
            drawCircle(color = SkySurface, radius = 5f, center = androidx.compose.ui.geometry.Offset(x, y))
            drawCircle(color = SkyAccent, radius = 3f, center = androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@Composable
private fun WeightTargetCard(
    targetWeight: Double?,
    showEditor: Boolean,
    onToggleEditor: () -> Unit,
    onSetTargetWeight: (Double?) -> Unit
) {
    var input by remember(targetWeight) { mutableStateOf(targetWeight?.let(::formatHealthJin).orEmpty()) }
    HealthCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(36.dp), color = SkyAccent.copy(alpha = 0.14f), shape = RoundedCornerShape(13.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = SkyAccent, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.padding(start = 11.dp).weight(1f)) {
                Text(
                    text = targetWeight?.let { "目标体重 · ${formatHealthJin(it)} 斤" } ?: "目标体重尚未设置",
                    fontSize = if (targetWeight == null) 16.sp else 14.sp,
                    fontWeight = if (targetWeight == null) FontWeight.Medium else FontWeight.Normal,
                    color = if (targetWeight == null) SkyMutedText else SkyInk
                )
                Text(
                    text = if (targetWeight == null) "设置目标后可查看距离与进度" else "持续记录，慢慢靠近目标",
                    fontSize = 13.sp,
                    color = SkyMutedText
                )
            }
            Text(
                text = if (showEditor) "收起" else "调整目标 ›",
                modifier = Modifier.clickable(onClick = onToggleEditor),
                style = MaterialTheme.typography.labelMedium,
                color = SkyPrimary
            )
        }
        if (showEditor) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("斤数") }
                )
                Button(onClick = { onSetTargetWeight(input.toDoubleOrNull()) }) { Text("保存") }
            }
        }
    }
}

@Composable
private fun HealthWeightEditor(
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    HealthCard {
        Text("记录体重", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("当前体重（斤）") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { input.toDoubleOrNull()?.let(onSave) }, modifier = Modifier.weight(1f)) { Text("保存") }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
        }
    }
}

@Composable
internal fun HealthCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = SkySurface),
        border = BorderStroke(1.dp, SkyCoolBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private fun formatHealthJin(value: Double): String = String.format(Locale.US, "%.1f", value)
