package com.daily.life.feature.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daily.life.R
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

private val HealthOverviewInk = Color(0xFF204A0A)
private val HealthOverviewMuted = Color(0xFF557049)
private val HealthOverviewCard = Color(0xFFF9F7EE)
private val WeightAccent = Color(0xFFB1D685)
private val WeightTile = Color(0xFFEDF6DF)
private val PeriodAccent = Color(0xFFFFB246)
private val PeriodTile = Color(0xFFFFF0D7)
private val PeriodBanner = Color(0xFFFFE2B1)
private val CardBorder = Color(0xFFCEDDBD)
private val TrendGrid = Color(0xFFD9E8C8)
private val TrendDateFormatter = DateTimeFormatter.ofPattern("M/d")

internal data class HealthOverviewDisplay(
    val currentWeight: String,
    val targetWeight: String,
    val lastPeriod: String,
    val nextPeriod: String
)

internal fun healthOverviewDisplay(
    state: HealthState,
    presentation: HealthDashboardPresentation
): HealthOverviewDisplay {
    state.errorMessage?.let { message ->
        return HealthOverviewDisplay(message, message, message, message)
    }
    val weightValue = if (!state.weightRecordsLoaded || !state.targetWeightLoaded) {
        "正在读取体重记录…"
    } else {
        null
    }
    val periodValue = if (!state.periodRecordsLoaded || !state.periodPredictionLoaded) {
        "正在读取经期记录…"
    } else {
        null
    }
    return HealthOverviewDisplay(
        currentWeight = weightValue ?: presentation.currentWeight,
        targetWeight = weightValue ?: presentation.targetWeight,
        lastPeriod = periodValue ?: presentation.lastPeriod,
        nextPeriod = periodValue ?: presentation.predictedPeriod
    )
}

@Composable
internal fun HealthHeader(onOpenHistory: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "健康",
                color = HealthOverviewInk,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "记录体重与经期变化，慢慢关注自己。",
                modifier = Modifier.padding(top = 4.dp),
                color = HealthOverviewMuted,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Surface(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onOpenHistory),
                color = WeightTile,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Text(
                    text = "历史记录",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = HealthOverviewInk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Box(
            modifier = Modifier.size(90.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.health_header_sheep),
                contentDescription = null,
                modifier = Modifier.size(82.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "♥",
                modifier = Modifier.align(Alignment.TopStart),
                color = PeriodAccent,
                fontSize = 14.sp
            )
            Text(
                text = "♥",
                modifier = Modifier.align(Alignment.BottomEnd),
                color = WeightAccent,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
internal fun WeightOverviewCard(
    currentWeight: String,
    targetWeight: String,
    trendPoints: List<WeightPoint>,
    selectedRange: WeightTrendRange,
    onRangeSelected: (WeightTrendRange) -> Unit,
    onRecordWeight: () -> Unit,
    onEditTarget: () -> Unit,
    onEditWeight: () -> Unit
) {
    HealthOverviewSurface {
        OverviewCardTitle(title = "体重概况", accent = WeightAccent, onEdit = onEditWeight)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WeightSummaryTile(
                label = "当前体重",
                value = currentWeight,
                illustrationRes = R.drawable.health_weight_scale,
                onClick = onRecordWeight,
                modifier = Modifier.weight(1f)
            )
            WeightSummaryTile(
                label = "目标体重",
                value = targetWeight,
                illustrationRes = R.drawable.health_weight_target,
                onClick = onEditTarget,
                modifier = Modifier.weight(1f)
            )
        }
        WeightTrendPanel(
            points = trendPoints,
            selectedRange = selectedRange,
            onRangeSelected = onRangeSelected,
            onRecordWeight = onRecordWeight
        )
    }
}

@Composable
internal fun PeriodOverviewCard(
    lastPeriod: String,
    nextPeriod: String,
    countdownDays: Int?,
    onEditPeriod: () -> Unit
) {
    HealthOverviewSurface {
        OverviewCardTitle(title = "经期概况", accent = PeriodAccent, onEdit = onEditPeriod)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PeriodSummaryTile(
                label = "上次经期",
                value = lastPeriod,
                illustrationRes = R.drawable.health_period_last,
                onClick = onEditPeriod,
                modifier = Modifier.weight(1f)
            )
            PeriodSummaryTile(
                label = "预计经期",
                value = nextPeriod,
                illustrationRes = R.drawable.health_period_prediction,
                onClick = onEditPeriod,
                modifier = Modifier.weight(1f)
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PeriodBanner,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, top = 13.dp, end = 10.dp, bottom = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "距离下次经期",
                        color = HealthOverviewMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = countdownDays?.let { "$it 天" } ?: "-- 天",
                        modifier = Modifier.padding(top = 2.dp),
                        color = HealthOverviewInk,
                        fontSize = 27.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (countdownDays == null) "记录后会自动计算" else "根据最近经期自动计算",
                        modifier = Modifier.padding(top = 2.dp),
                        color = HealthOverviewMuted,
                        fontSize = 12.sp
                    )
                }
                Image(
                    painter = painterResource(R.drawable.health_sleeping_sheep),
                    contentDescription = null,
                    modifier = Modifier.size(82.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun HealthOverviewSurface(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = HealthOverviewCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun OverviewCardTitle(title: String, accent: Color, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            modifier = Modifier
                .width(6.dp)
                .height(28.dp)
                .background(accent, RoundedCornerShape(4.dp))
        )
        Text(
            text = title,
            modifier = Modifier.padding(start = 10.dp),
            color = HealthOverviewInk,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onEdit) {
            Text("编辑", color = HealthOverviewMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WeightSummaryTile(
    label: String,
    value: String,
    illustrationRes: Int,
    onClick: () -> Unit,
    modifier: Modifier
) {
    SummaryTile(
        label = label,
        value = value,
        illustrationRes = illustrationRes,
        surfaceColor = WeightTile,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun PeriodSummaryTile(
    label: String,
    value: String,
    illustrationRes: Int,
    onClick: () -> Unit,
    modifier: Modifier
) {
    SummaryTile(
        label = label,
        value = value,
        illustrationRes = illustrationRes,
        surfaceColor = PeriodTile,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    illustrationRes: Int,
    surfaceColor: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 128.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = surfaceColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            Column(modifier = Modifier.padding(end = 34.dp)) {
                Text(
                    text = label,
                    color = HealthOverviewMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    modifier = Modifier.padding(top = 7.dp),
                    color = HealthOverviewInk,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Image(
                painter = painterResource(illustrationRes),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .align(Alignment.BottomEnd),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun WeightTrendPanel(
    points: List<WeightPoint>,
    selectedRange: WeightTrendRange,
    onRangeSelected: (WeightTrendRange) -> Unit,
    onRecordWeight: () -> Unit
) {
    Surface(color = Color(0xFFF4F8EA), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "体重趋势",
                    modifier = Modifier.weight(1f),
                    color = HealthOverviewInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                WeightTrendRange.values().forEach { range ->
                    val selected = range == selectedRange
                    Surface(
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onRangeSelected(range) },
                        color = if (selected) WeightAccent else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = range.label,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            color = HealthOverviewInk,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
            if (points.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(138.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onRecordWeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "记录两次体重后，这里会显示趋势",
                        color = HealthOverviewMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                WeightTrendChart(points)
            }
        }
    }
}

@Composable
private fun WeightTrendChart(points: List<WeightPoint>) {
    val sorted = points.sortedBy(WeightPoint::date)
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "斤",
                modifier = Modifier.padding(top = 3.dp),
                color = HealthOverviewMuted,
                fontSize = 11.sp
            )
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(116.dp)
            ) {
                val chartMin = min(100.0, sorted.minOf(WeightPoint::weightJin) - 2.0)
                val chartMax = max(130.0, sorted.maxOf(WeightPoint::weightJin) + 2.0)
                val range = (chartMax - chartMin).takeIf { it > 0.0 } ?: 1.0
                val top = 6.dp.toPx()
                val bottom = size.height - 8.dp.toPx()
                repeat(4) { index ->
                    val y = top + (bottom - top) * index / 3f
                    drawLine(
                        color = TrendGrid,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                val line = Path()
                sorted.forEachIndexed { index, point ->
                    val x = size.width * index / sorted.lastIndex.coerceAtLeast(1)
                    val y = bottom - ((point.weightJin - chartMin) / range * (bottom - top)).toFloat()
                    if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                }
                drawPath(line, color = HealthOverviewInk, style = Stroke(width = 2.5.dp.toPx()))
                sorted.forEachIndexed { index, point ->
                    val x = size.width * index / sorted.lastIndex.coerceAtLeast(1)
                    val y = bottom - ((point.weightJin - chartMin) / range * (bottom - top)).toFloat()
                    drawCircle(HealthOverviewCard, 4.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
                    drawCircle(HealthOverviewInk, 2.5.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TrendDateLabel(sorted.first())
            TrendDateLabel(sorted[sorted.lastIndex / 2])
            TrendDateLabel(sorted.last())
        }
    }
}

@Composable
private fun TrendDateLabel(point: WeightPoint) {
    Text(point.date.format(TrendDateFormatter), color = HealthOverviewMuted, fontSize = 11.sp)
}
