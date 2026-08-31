package com.daily.life.feature.health

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val HealthPageInk = Color(0xFF171B2C)
private val HealthPageMutedText = Color(0xFF858A98)
private val HealthNavigationIcon = Color(0xFF5E6575)

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
    val recentPoints = state.weights
        .sortedBy { it.recordedAt }
        .takeLast(30)
        .map { WeightPoint(it.recordedAt.atZone(zoneId).toLocalDate(), it.weightJin) }
    var activeSheet by remember { mutableStateOf<HealthSheet?>(null) }
    var recordingDateTime by remember { mutableStateOf(LocalDateTime.now()) }
    var editedPeriod by remember { mutableStateOf<PeriodRecord?>(null) }
    var showAllWeightHistory by remember { mutableStateOf(false) }
    var showAllPeriodHistory by remember { mutableStateOf(false) }
    val sortedWeights = state.weights.sortedByDescending { it.recordedAt }
    val visibleWeights = if (showAllWeightHistory) sortedWeights else sortedWeights.take(3)
    val visiblePeriods = if (showAllPeriodHistory) presentation.history else presentation.history.take(3)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFF7F4FF), Color(0xFFFCFBFF))
                )
            ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { HealthHeader() }
        item {
            HealthOverviewCard(
                presentation = presentation,
                state = state,
                onPreviousMonth = onPreviousMonth,
                onSelectRecordDate = { date ->
                    recordingDateTime = LocalDateTime.of(date, LocalTime.now())
                    activeSheet = HealthSheet.Weight
                },
                onNextMonth = onNextMonth
            )
        }
        item {
            HealthQuickRecordCard(
                onRecordWeight = {
                    recordingDateTime = LocalDateTime.now()
                    activeSheet = HealthSheet.Weight
                },
                onRecordPeriod = {
                    editedPeriod = null
                    activeSheet = HealthSheet.Period
                }
            )
        }
        item { HealthTrendCard(points = recentPoints) }
        item {
            HealthGoalCard(
                targetWeight = state.targetWeightJin,
                currentWeight = state.weights.maxByOrNull { it.recordedAt }?.weightJin,
                onModify = { activeSheet = HealthSheet.Target }
            )
        }
        item {
            HealthPeriodCard(presentation = presentation)
        }
        item {
            HealthHistoryPanel(
                weights = visibleWeights,
                periodHistory = visiblePeriods,
                zoneId = zoneId,
                weightCanExpand = sortedWeights.size > visibleWeights.size,
                weightExpanded = showAllWeightHistory,
                onToggleWeight = { showAllWeightHistory = !showAllWeightHistory },
                periodCanExpand = presentation.history.size > visiblePeriods.size,
                periodExpanded = showAllPeriodHistory,
                onTogglePeriod = { showAllPeriodHistory = !showAllPeriodHistory },
                onEditPeriod = { record ->
                    editedPeriod = record
                    activeSheet = HealthSheet.Period
                },
                onDeletePeriod = onDeletePeriod
            )
        }
        state.statusMessage?.let { message ->
            item { Text(message, color = SkySuccess, fontSize = 14.sp) }
        }
        state.errorMessage?.let { message ->
            item { Text(message, color = SkyWarm, fontSize = 14.sp) }
        }
    }

    activeSheet?.let { sheet ->
        HealthBottomSheet(onDismiss = { activeSheet = null }) {
            when (sheet) {
                HealthSheet.Weight -> HealthWeightEditorContent(
                    recordingDateTime = recordingDateTime,
                    onRecordingDateTimeChange = { recordingDateTime = it },
                    onSave = { weight ->
                        onRecordWeight(weight, recordingDateTime)
                        activeSheet = null
                    },
                    onDismiss = { activeSheet = null }
                )

                HealthSheet.Period -> HealthPeriodEditorContent(
                    record = editedPeriod,
                    onSave = { startDate, endDate ->
                        editedPeriod?.let { onUpdatePeriod(it.copy(startDate = startDate, endDate = endDate)) }
                            ?: onRecordPeriod(startDate, endDate)
                        activeSheet = null
                    },
                    onDismiss = { activeSheet = null }
                )

                HealthSheet.Target -> HealthTargetEditorContent(
                    targetWeight = state.targetWeightJin,
                    onSave = {
                        onSetTargetWeight(it)
                        activeSheet = null
                    },
                    onDismiss = { activeSheet = null }
                )
            }
        }
    }
}

private enum class HealthSheet { Weight, Period, Target }

@Composable
private fun HealthHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "健康",
                color = HealthPageInk,
                fontSize = 31.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "记录体重与经期变化，慢慢关注自己。",
                modifier = Modifier.padding(top = 4.dp),
                color = HealthPageMutedText,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }
        Box(modifier = Modifier.size(64.dp)) {
            Image(
                painter = painterResource(R.drawable.health_header_sheep),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center),
                contentScale = ContentScale.Fit
            )
            Text("✦", modifier = Modifier.align(Alignment.TopStart), color = SkyWarm.copy(alpha = 0.72f), fontSize = 15.sp)
            Text("✦", modifier = Modifier.align(Alignment.BottomEnd), color = SkyAccent.copy(alpha = 0.56f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun HealthOverviewCard(
    presentation: HealthDashboardPresentation,
    state: HealthState,
    onPreviousMonth: () -> Unit,
    onSelectRecordDate: (LocalDate) -> Unit,
    onNextMonth: () -> Unit
) {
    val context = LocalContext.current
    val initialDate = state.selectedMonth.atDay(1)
    val latestPeriod = state.periodRecords.maxByOrNull { it.endDate }
    HealthReferenceCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HealthDecoratedTitle(presentation.overviewTitle)
            Spacer(Modifier.weight(1f))
            HealthHeaderIcon(Icons.Outlined.ArrowBackIosNew, "上个月", onPreviousMonth)
            HealthHeaderIcon(
                Icons.Outlined.CalendarMonth,
                "选择体重记录日期",
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> onSelectRecordDate(LocalDate.of(year, month + 1, day)) },
                        initialDate.year,
                        initialDate.monthValue - 1,
                        initialDate.dayOfMonth
                    ).show()
                },
                size = 21.dp
            )
            HealthHeaderIcon(Icons.Outlined.ArrowForwardIos, "下个月", onNextMonth)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HealthOverviewMetric(
                label = "当前体重",
                value = if (state.weights.isEmpty()) presentation.latestWeight else "${presentation.latestWeight} 斤",
                cardColor = SkyPurpleSurface,
                modifier = Modifier.weight(1f),
                isEmpty = state.weights.isEmpty(),
                supporting = "记录一次体重后显示"
            )
            HealthOverviewMetric(
                label = "目标体重",
                value = presentation.targetSummary.removePrefix("目标 "),
                cardColor = SkyPurpleSurface,
                modifier = Modifier.weight(1f),
                isEmpty = state.targetWeightJin == null,
                supporting = "可在目标体重卡中设置"
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HealthOverviewMetric(
                label = "本月体重记录",
                value = presentation.monthRecordSummary.removePrefix("本月记录 "),
                cardColor = SkyPurpleSurface,
                modifier = Modifier.weight(1f),
                isEmpty = presentation.monthRecordSummary == "尚未记录",
                supporting = "本月还没有体重记录"
            )
            HealthOverviewMetric(
                label = "最近经期",
                value = latestPeriod?.let { "${it.startDate.monthValue}.${it.startDate.dayOfMonth}–${it.endDate.monthValue}.${it.endDate.dayOfMonth}" } ?: "尚未记录经期",
                cardColor = SkyPinkSurface,
                modifier = Modifier.weight(1f),
                isEmpty = latestPeriod == null,
                supporting = "记录后将显示日期"
            )
        }
    }
}

@Composable
private fun HealthOverviewMetric(
    label: String,
    value: String,
    cardColor: Color,
    modifier: Modifier = Modifier,
    isEmpty: Boolean = false,
    supporting: String? = null
) {
    Surface(
        modifier = modifier.height(84.dp),
        color = cardColor.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(label, color = SkyMutedText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isEmpty) {
                Text(
                    value,
                    modifier = Modifier.padding(top = 4.dp),
                    color = HealthPageMutedText,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                supporting?.let {
                    Text(
                        it,
                        modifier = Modifier.padding(top = 1.dp),
                        color = HealthPageMutedText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    value,
                    modifier = Modifier.padding(top = 4.dp),
                    color = HealthPageInk,
                    fontSize = 24.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HealthHeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 20.dp
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = HealthNavigationIcon, modifier = Modifier.size(size))
    }
}

@Composable
private fun HealthQuickRecordCard(
    onRecordWeight: () -> Unit,
    onRecordPeriod: () -> Unit
) {
    HealthReferenceCard(contentPadding = 14.dp) {
        HealthDecoratedTitle("快捷记录")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HealthQuickRecordAction(
                label = "记录体重",
                supporting = "记下今天的变化",
                illustrationRes = R.drawable.health_quick_weight,
                color = SkyPrimary,
                surfaceColor = SkyPurpleSurface,
                onClick = onRecordWeight,
                modifier = Modifier.weight(1f)
            )
            HealthQuickRecordAction(
                label = "记录经期",
                supporting = "更新本次日期",
                illustrationRes = R.drawable.health_quick_period,
                color = SkyWarm,
                surfaceColor = SkyPinkSurface,
                onClick = onRecordPeriod,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HealthQuickRecordAction(
    label: String,
    supporting: String,
    illustrationRes: Int,
    color: Color,
    surfaceColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = surfaceColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Image(
                painter = painterResource(illustrationRes),
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.BottomStart),
                contentScale = ContentScale.Fit
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(end = 38.dp)
            ) {
                Text(label, color = HealthPageInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(supporting, modifier = Modifier.padding(top = 2.dp), color = HealthPageMutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(
                color = color,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Outlined.ArrowForwardIos,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.padding(9.dp).size(14.dp)
                )
            }
        }
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
    HealthReferenceCard(modifier = Modifier.height(218.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HealthDecoratedTitle("体重趋势")
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
            .height(124.dp)
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
private fun HealthGoalCard(
    targetWeight: Double?,
    currentWeight: Double?,
    onModify: () -> Unit
) {
    val distance = when {
        targetWeight == null -> "设置目标后可查看距离与进度"
        currentWeight == null -> "记录体重后可查看距离与进度"
        else -> {
            val delta = currentWeight - targetWeight
            if (delta > 0) "距目标 ${formatHealthWeight(delta)} 斤" else "已达到目标"
        }
    }
    HealthReferenceCard(modifier = Modifier.height(174.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(0.6f)) {
                HealthSectionTitle("目标体重")
                if (targetWeight == null) {
                    Text(
                        text = "目标体重尚未设置",
                        modifier = Modifier.padding(top = 8.dp),
                        color = HealthPageMutedText,
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = distance,
                        modifier = Modifier.padding(top = 4.dp),
                        color = HealthPageMutedText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                } else {
                    Text(
                        text = "目标 ${formatHealthWeight(targetWeight)} 斤",
                        modifier = Modifier.padding(top = 7.dp),
                        color = HealthPageInk,
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "当前 ${currentWeight?.let(::formatHealthWeight) ?: "尚未记录"}${if (currentWeight == null) "" else " 斤"} · $distance",
                        modifier = Modifier.padding(top = 3.dp),
                        color = SkyMutedText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.4f),
                horizontalAlignment = Alignment.End
            ) {
                Image(
                    painter = painterResource(R.drawable.health_quick_weight),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(8.dp))
                HealthPrimaryActionButton(text = "修改目标", color = SkyPrimary, onClick = onModify)
            }
        }
    }
}

@Composable
private fun HealthPeriodCard(presentation: HealthDashboardPresentation) {
    val latestPeriod = presentation.history.firstOrNull()?.dateRange
    HealthReferenceCard(modifier = Modifier.height(174.dp), cardColor = Color(0xFFFFFCFD)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(0.6f)) {
                HealthSectionTitle("经期记录")
                if (latestPeriod == null) {
                    Text(
                        text = "尚未记录经期",
                        modifier = Modifier.padding(top = 8.dp),
                        color = HealthPageMutedText,
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "记录后可查看预测日期",
                        modifier = Modifier.padding(top = 4.dp),
                        color = HealthPageMutedText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                } else {
                    Text(
                        text = "最近 $latestPeriod",
                        modifier = Modifier.padding(top = 7.dp),
                        color = SkyWarm,
                        fontSize = 19.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = presentation.cycleSummary,
                        modifier = Modifier.padding(top = 4.dp),
                        color = SkyMutedText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = presentation.nextPeriodSummary,
                        modifier = Modifier.padding(top = 2.dp),
                        color = SkyMutedText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
                Image(
                    painter = painterResource(R.drawable.health_quick_period),
                contentDescription = null,
                modifier = Modifier
                    .weight(0.4f)
                    .size(72.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun HealthPrimaryActionButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(48.dp).width(124.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HealthHistoryPanel(
    weights: List<WeightRecord>,
    periodHistory: List<PeriodHistoryPresentation>,
    zoneId: ZoneId,
    weightCanExpand: Boolean,
    weightExpanded: Boolean,
    onToggleWeight: () -> Unit,
    periodCanExpand: Boolean,
    periodExpanded: Boolean,
    onTogglePeriod: () -> Unit,
    onEditPeriod: (PeriodRecord) -> Unit,
    onDeletePeriod: (Long) -> Unit
) {
    HealthReferenceCard {
        HealthSectionTitle("历史记录")
        Surface(color = SkyPurpleSurface.copy(alpha = 0.62f), shape = RoundedCornerShape(18.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthHistoryCategoryTitle("体重记录", weightCanExpand, weightExpanded, onToggleWeight)
                if (weights.isEmpty()) {
                    Text("尚未记录体重", color = HealthPageMutedText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                } else {
                    weights.forEachIndexed { index, record ->
                        HealthWeightHistoryRow(record, zoneId)
                        if (index != weights.lastIndex) HealthHistoryDivider()
                    }
                }
            }
        }
        Surface(color = SkyPinkSurface.copy(alpha = 0.66f), shape = RoundedCornerShape(18.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthHistoryCategoryTitle("经期记录", periodCanExpand, periodExpanded, onTogglePeriod)
                if (periodHistory.isEmpty()) {
                    Text("尚未记录经期", color = HealthPageMutedText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                } else {
                    periodHistory.forEachIndexed { index, item ->
                        HealthPeriodHistoryRow(
                            item = item,
                            onEdit = { onEditPeriod(item.record) },
                            onDelete = { onDeletePeriod(item.record.id) }
                        )
                        if (index != periodHistory.lastIndex) HealthHistoryDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthHistoryCategoryTitle(
    title: String,
    canExpand: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(top = 2.dp),
            color = SkyMutedText,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium
        )
        if (canExpand || expanded) {
            TextButton(onClick = onToggle) {
                Text(if (expanded) "收起" else "查看全部", color = SkyAccent, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun HealthWeightHistoryRow(record: WeightRecord, zoneId: ZoneId) {
    val date = record.recordedAt.atZone(zoneId).toLocalDate()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = date.format(DateTimeFormatter.ofPattern("M月d日")),
            modifier = Modifier.weight(1f),
            color = SkyInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${formatHealthWeight(record.weightJin)} 斤",
            color = SkyAccent,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HealthPeriodHistoryRow(
    item: PeriodHistoryPresentation,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            item.dateRange,
            modifier = Modifier.weight(1f),
            color = SkyInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        TextButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "编辑经期记录", tint = SkyMutedText, modifier = Modifier.size(18.dp))
        }
        TextButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除经期记录", tint = SkyWarm, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HealthHistoryDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SkyCoolBorder.copy(alpha = 0.45f))
    )
}

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
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun HealthSheetHeader(title: String, onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), color = SkyInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onDismiss) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = SkyMutedText)
        }
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
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onRecordingDateTimeChange(recordingDateTime.withYear(year).withMonth(month + 1).withDayOfMonth(day))
                    },
                    recordingDateTime.year,
                    recordingDateTime.monthValue - 1,
                    recordingDateTime.dayOfMonth
                ).show()
            },
            modifier = Modifier.weight(1f).height(50.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, SkyCoolBorder)
        ) { Text(recordingDateTime.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), color = SkyInk, fontSize = 15.sp) }
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onRecordingDateTimeChange(recordingDateTime.withHour(hour).withMinute(minute)) },
                    recordingDateTime.hour,
                    recordingDateTime.minute,
                    true
                ).show()
            },
            modifier = Modifier.weight(0.62f).height(50.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, SkyCoolBorder)
        ) { Text(recordingDateTime.format(DateTimeFormatter.ofPattern("HH:mm")), color = SkyInk, fontSize = 15.sp) }
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
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary, contentColor = Color.White)
    ) { Text("保存", fontSize = 16.sp) }
}

@Composable
private fun HealthPeriodEditorContent(
    record: PeriodRecord?,
    onSave: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var startDate by remember(record) { mutableStateOf(record?.startDate ?: LocalDate.now()) }
    var endDate by remember(record) { mutableStateOf(record?.endDate ?: LocalDate.now()) }
    HealthSheetHeader(if (record == null) "记录本次经期" else "编辑经期记录", onDismiss)
    DailyDatePickerField(startDate.toString(), "开始日期", onDateSelected = { startDate = it })
    DailyDatePickerField(endDate.toString(), "结束日期", onDateSelected = { endDate = it })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onSave(startDate, endDate) },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SkyWarm, contentColor = Color.White)
        ) { Text("保存", fontSize = 16.sp) }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SkyCoolBorder)
        ) { Text("取消", color = SkyMutedText, fontSize = 16.sp) }
    }
}

@Composable
private fun HealthTargetEditorContent(
    targetWeight: Double?,
    onSave: (Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember(targetWeight) { mutableStateOf(targetWeight?.let(::formatHealthWeight).orEmpty()) }
    HealthSheetHeader("修改目标体重", onDismiss)
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("目标体重（斤）") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(18.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onSave(input.toDoubleOrNull()) },
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SkyPrimary, contentColor = Color.White)
        ) { Text("保存", fontSize = 16.sp) }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, SkyCoolBorder)
        ) { Text("取消", color = SkyMutedText, fontSize = 16.sp) }
    }
}

@Composable
private fun HealthSectionTitle(text: String) {
    Text(text, color = HealthPageInk, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun HealthDecoratedTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("✦", color = SkyAccent.copy(alpha = 0.78f), fontSize = 17.sp)
        Text(
            text,
            modifier = Modifier.padding(start = 5.dp),
            color = HealthPageInk,
            fontSize = 19.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HealthReferenceCard(
    modifier: Modifier = Modifier,
    cardColor: Color = SkySurface,
    contentPadding: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

private fun formatHealthWeight(value: Double): String = String.format(Locale.US, "%.1f", value)
