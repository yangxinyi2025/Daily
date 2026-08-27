package com.daily.life.feature.health

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daily.life.R
import com.daily.life.core.designsystem.DailyDatePickerField
import com.daily.life.core.designsystem.SkyAccent
import com.daily.life.core.designsystem.SkyBackground
import com.daily.life.core.designsystem.SkyCoolBorder
import com.daily.life.core.designsystem.SkyInk
import com.daily.life.core.designsystem.SkyMutedText
import com.daily.life.core.designsystem.SkyPinkSurface
import com.daily.life.core.designsystem.SkyPrimary
import com.daily.life.core.designsystem.SkyPurpleSurface
import com.daily.life.core.designsystem.SkySuccess
import com.daily.life.core.designsystem.SkySurface
import com.daily.life.core.designsystem.SkyWarm
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun HealthDashboardScreen(
    state: HealthState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onRecordWeight: (Double) -> Unit,
    onSetTargetWeight: (Double?) -> Unit,
    onRecordPeriod: (LocalDate, LocalDate) -> Unit,
    onUpdatePeriod: (PeriodRecord) -> Unit,
    onDeletePeriod: (Long) -> Unit
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val presentation = healthDashboardPresentation(state, zoneId)
    val recentPoints = state.weights
        .sortedBy { it.recordedAt }
        .takeLast(30)
        .map { WeightPoint(it.recordedAt.atZone(zoneId).toLocalDate(), it.weightJin) }
    var showWeightEditor by remember { mutableStateOf(false) }
    var editedPeriod by remember { mutableStateOf<PeriodRecord?>(null) }
    var showPeriodEditor by remember { mutableStateOf(false) }
    val sectionOrder = healthDashboardSectionOrder(showWeightEditor, showPeriodEditor)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            top = 14.dp,
            end = 18.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HealthHeader(onAddWeight = { showWeightEditor = true })
        }
        item {
            HealthOverviewCard(
                presentation = presentation,
                state = state,
                onPreviousMonth = onPreviousMonth,
                onCurrentMonth = onCurrentMonth,
                onNextMonth = onNextMonth
            )
        }
        item { HealthTrendCard(points = recentPoints) }
        if (HealthDashboardSection.WeightEntry in sectionOrder) {
            item {
                HealthWeightEntryCard(
                    onSave = {
                        onRecordWeight(it)
                        showWeightEditor = false
                    },
                    onDismiss = { showWeightEditor = false }
                )
            }
        }
        item {
            HealthGoalCard(
                targetWeight = state.targetWeightJin,
                onSetTargetWeight = onSetTargetWeight
            )
        }
        item {
            HealthPeriodCard(
                presentation = presentation,
                onRecord = {
                    editedPeriod = null
                    showPeriodEditor = true
                }
            )
        }
        if (HealthDashboardSection.PeriodEntry in sectionOrder) {
            item {
                HealthPeriodEditorCard(
                    record = editedPeriod,
                    onSave = { startDate, endDate ->
                        editedPeriod?.let { onUpdatePeriod(it.copy(startDate = startDate, endDate = endDate)) }
                            ?: onRecordPeriod(startDate, endDate)
                        showPeriodEditor = false
                    },
                    onDismiss = { showPeriodEditor = false }
                )
            }
        }
        item { HealthSectionTitle("历史记录") }
        if (presentation.history.isEmpty()) {
            item {
                HealthReferenceCard {
                    Text("还没有经期记录", color = SkyMutedText, fontSize = 14.sp)
                }
            }
        } else {
            items(presentation.history, key = { it.record.id }) { item ->
                HealthHistoryCard(
                    item = item,
                    onEdit = {
                        editedPeriod = item.record
                        showPeriodEditor = true
                    },
                    onDelete = { onDeletePeriod(item.record.id) }
                )
            }
        }
        state.statusMessage?.let { message ->
            item { Text(message, color = SkySuccess, fontSize = 14.sp) }
        }
        state.errorMessage?.let { message ->
            item { Text(message, color = SkyWarm, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun HealthHeader(onAddWeight: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "健康",
                color = SkyInk,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "把身体状态，慢慢记录下来。",
                modifier = Modifier.padding(top = 2.dp),
                color = SkyMutedText,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
        IconButton(onClick = onAddWeight, modifier = Modifier.size(46.dp)) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "记录体重",
                tint = SkyInk,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun HealthOverviewCard(
    presentation: HealthDashboardPresentation,
    state: HealthState,
    onPreviousMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val delta = weightChangeFromLatest(state.weights)
    HealthReferenceCard(modifier = Modifier.height(124.dp)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = presentation.overviewTitle,
                    color = SkyMutedText,
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                HealthHeaderIcon(Icons.Outlined.ArrowBackIosNew, "上个月", onPreviousMonth)
                HealthHeaderIcon(Icons.Outlined.CalendarMonth, "回到本月", onCurrentMonth, 24.dp)
                HealthHeaderIcon(Icons.Outlined.ArrowForwardIos, "下个月", onNextMonth)
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 15.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = presentation.latestWeight,
                    color = SkyInk,
                    fontSize = 31.sp,
                    lineHeight = 37.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "斤",
                    modifier = Modifier.padding(start = 5.dp, bottom = 4.dp),
                    color = SkyMutedText,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                HealthDeltaBadge(delta = delta)
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(end = 126.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = presentation.targetSummary,
                    modifier = Modifier.weight(1f),
                    color = SkyMutedText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(10.dp))
                Spacer(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(SkyCoolBorder)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = presentation.monthRecordSummary,
                    modifier = Modifier.weight(1f),
                    color = SkyMutedText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Image(
                painter = painterResource(R.drawable.health_scale_illustration),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 11.dp)
                    .width(126.dp)
                    .height(102.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun HealthHeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 18.dp
) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = SkyInk, modifier = Modifier.size(size))
    }
}

@Composable
private fun HealthDeltaBadge(delta: Double?) {
    val isDown = delta != null && delta < 0
    val label = when {
        delta == null -> "记录不足"
        isDown -> "较上次 ↓ ${formatHealthWeight(-delta)}斤"
        delta > 0 -> "较上次 ↑ ${formatHealthWeight(delta)}斤"
        else -> "与上次持平"
    }
    Surface(
        modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
        color = if (isDown) Color(0xFFEAF7EE) else SkyPurpleSurface,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (isDown) SkySuccess else SkyAccent,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun HealthTrendCard(points: List<WeightPoint>) {
    HealthReferenceCard(modifier = Modifier.height(142.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HealthSectionTitle("体重趋势")
            Spacer(Modifier.weight(1f))
            Text("最近 30 天", color = SkyMutedText, fontSize = 15.sp)
        }
        if (points.size < 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("记录两次体重后，这里会显示趋势", color = SkyMutedText, fontSize = 14.sp)
            }
        } else {
            HealthWeightTrendChart(points)
        }
    }
}

@Composable
private fun HealthWeightTrendChart(points: List<WeightPoint>) {
    val sorted = points.sortedBy { it.date }
    val chartMin = min(100.0, sorted.minOf { it.weightJin } - 2.0)
    val chartMax = max(130.0, sorted.maxOf { it.weightJin } + 2.0)
    val range = (chartMax - chartMin).takeIf { it > 0.0 } ?: 1.0
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .padding(top = 6.dp)
    ) {
        val left = 28.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val top = 6.dp.toPx()
        repeat(4) { index ->
            val y = top + (bottom - top) * index / 3f
            drawLine(
                color = SkyCoolBorder.copy(alpha = 0.9f),
                start = androidx.compose.ui.geometry.Offset(left, y),
                end = androidx.compose.ui.geometry.Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        val line = Path()
        val fill = Path()
        sorted.forEachIndexed { index, point ->
            val x = left + (size.width - left) * index / sorted.lastIndex.coerceAtLeast(1)
            val y = bottom - ((point.weightJin - chartMin) / range * (bottom - top)).toFloat()
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, bottom)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        if (sorted.isNotEmpty()) {
            fill.lineTo(size.width, bottom)
            fill.close()
            drawPath(fill, brush = Brush.verticalGradient(listOf(SkyAccent.copy(alpha = 0.22f), Color.Transparent)))
            drawPath(line, color = SkyAccent, style = Stroke(width = 3.dp.toPx()))
        }
        sorted.forEachIndexed { index, point ->
            val x = left + (size.width - left) * index / sorted.lastIndex.coerceAtLeast(1)
            val y = bottom - ((point.weightJin - chartMin) / range * (bottom - top)).toFloat()
            drawCircle(SkySurface, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
            drawCircle(SkyAccent, 3.5.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@Composable
private fun HealthGoalCard(targetWeight: Double?, onSetTargetWeight: (Double?) -> Unit) {
    var input by remember(targetWeight) { mutableStateOf(targetWeight?.let(::formatHealthWeight).orEmpty()) }
    HealthReferenceCard {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    color = SkyPurpleSurface,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        Icons.Outlined.TrackChanges,
                        contentDescription = null,
                        tint = SkyAccent,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        "目标体重 · ${targetWeight?.let(::formatHealthWeight) ?: "未设置"} 斤",
                        color = SkyInk,
                        fontSize = 18.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "持续记录，慢慢靠近目标",
                        modifier = Modifier.padding(top = 3.dp),
                        color = SkyMutedText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            Text("✦", modifier = Modifier.align(Alignment.TopEnd), color = SkyAccent.copy(alpha = 0.26f), fontSize = 28.sp)
        }
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("斤数", color = SkyAccent, fontSize = 15.sp)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(18.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = SkyInk),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SkyPrimary,
                    unfocusedBorderColor = SkyPrimary.copy(alpha = 0.70f),
                    cursorColor = SkyPrimary
                )
            )
            Button(
                onClick = { onSetTargetWeight(input.toDoubleOrNull()) },
                modifier = Modifier.width(96.dp).height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary, contentColor = Color.White)
            ) { Text("保存", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun HealthPeriodCard(presentation: HealthDashboardPresentation, onRecord: () -> Unit) {
    val hasPrediction = presentation.nextPeriodSummary.startsWith("下次预计")
    HealthReferenceCard(
        modifier = Modifier.height(172.dp),
        cardColor = Color(0xFFFFFCFD)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(176.dp)
            ) {
                HealthSectionTitle("经期记录")
                Text(
                    text = presentation.nextPeriodSummary,
                    modifier = Modifier.padding(top = 8.dp),
                    color = SkyWarm,
                    fontSize = if (hasPrediction) 21.sp else 18.sp,
                    lineHeight = if (hasPrediction) 28.sp else 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = presentation.cycleSummary,
                    modifier = Modifier.padding(top = 2.dp),
                    color = SkyMutedText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )
            }
            Button(
                onClick = onRecord,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(188.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = SkyWarm, contentColor = Color.White)
            ) { Text("记录本次经期", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            Image(
                painter = painterResource(R.drawable.health_period_illustration),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .width(138.dp)
                    .height(122.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun HealthHistoryCard(
    item: PeriodHistoryPresentation,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    HealthReferenceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.dateRange, modifier = Modifier.weight(1f), color = SkyInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            HealthSecondaryButton("编辑", Icons.Outlined.Edit, onEdit)
            Spacer(Modifier.width(10.dp))
            HealthSecondaryButton("删除", Icons.Outlined.DeleteOutline, onDelete)
        }
    }
}

@Composable
private fun HealthSecondaryButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(42.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, SkyWarm),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SkyWarm)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
        Text(label, modifier = Modifier.padding(start = 3.dp), fontSize = 14.sp)
    }
}

@Composable
private fun HealthWeightEntryCard(onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    HealthReferenceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HealthSectionTitle("记录体重")
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.Close, contentDescription = "取消", modifier = Modifier.clickable(onClick = onDismiss), tint = SkyMutedText)
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("当前体重（斤）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(18.dp)
        )
        Button(
            onClick = { input.toDoubleOrNull()?.let(onSave) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary, contentColor = Color.White)
        ) { Text("保存", fontSize = 16.sp) }
    }
}

@Composable
private fun HealthPeriodEditorCard(
    record: PeriodRecord?,
    onSave: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var startDate by remember(record) { mutableStateOf(record?.startDate ?: LocalDate.now()) }
    var endDate by remember(record) { mutableStateOf(record?.endDate ?: LocalDate.now()) }
    HealthReferenceCard {
        HealthSectionTitle(if (record == null) "记录本次经期" else "编辑经期记录")
        DailyDatePickerField(startDate.toString(), "开始日期", onDateSelected = { startDate = it })
        DailyDatePickerField(endDate.toString(), "结束日期", onDateSelected = { endDate = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onSave(startDate, endDate) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = SkyWarm, contentColor = Color.White)
            ) { Text("保存") }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, SkyCoolBorder)
            ) { Text("取消", color = SkyMutedText) }
        }
    }
}

@Composable
private fun HealthSectionTitle(text: String) {
    Text(text, color = SkyInk, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun HealthReferenceCard(
    modifier: Modifier = Modifier,
    cardColor: Color = SkySurface,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, SkyCoolBorder.copy(alpha = 0.68f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

private fun formatHealthWeight(value: Double): String = String.format(Locale.US, "%.1f", value)
