package com.daily.life.feature.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

private val CalendarFlipUiStateSaver = Saver<CalendarFlipUiState, List<String?>>(
    save = { state ->
        listOf(state.shownMonth.toString(), state.pendingMonth?.toString(), state.direction?.name)
    },
    restore = { saved ->
        runCatching {
            CalendarFlipUiState(
                shownMonth = YearMonth.parse(requireNotNull(saved[0])),
                pendingMonth = saved[1]?.let(YearMonth::parse),
                direction = saved[2]?.let(CalendarFlipDirection::valueOf)
            )
        }.getOrNull()
    }
)

@Composable
internal fun FlipCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    days: List<LocalDate>,
    rules: Map<LocalDate, ScheduleCalendarRuleUi>,
    eventDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onMonthCommitted: (YearMonth) -> Unit
) {
    var flipState by rememberSaveable(month, stateSaver = CalendarFlipUiStateSaver) {
        mutableStateOf(CalendarFlipUiState(shownMonth = month))
    }
    val progress = remember { Animatable(0f) }
    val motionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val reducedMotion = motionScale == 0f
    val durationMillis = if (reducedMotion) 120 else 320

    LaunchedEffect(flipState.pendingMonth, flipState.direction, durationMillis) {
        val pendingMonth = flipState.pendingMonth ?: return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = durationMillis))
        flipState = completeCalendarFlip(flipState)
        onMonthCommitted(pendingMonth)
    }

    val currentMonth = flipState.shownMonth
    val pendingMonth = flipState.pendingMonth
    val currentDays = if (currentMonth == month) days else monthCalendarDays(currentMonth.atDay(1))
    val pageProgress = progress.value

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SchedulePageBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp)) {
            if (pendingMonth == null) {
                CalendarPaperStack(
                    month = currentMonth,
                    selectedDate = selectedDate,
                    days = currentDays,
                    rules = rules,
                    eventDates = eventDates,
                    onDateSelected = onDateSelected
                )
            } else if (flipState.direction == CalendarFlipDirection.NEXT) {
                CalendarPaperStack(
                    month = pendingMonth,
                    selectedDate = selectedDate,
                    days = monthCalendarDays(pendingMonth.atDay(1)),
                    rules = emptyMap(),
                    eventDates = eventDates,
                    onDateSelected = onDateSelected
                )
                CalendarPaperStack(
                    month = currentMonth,
                    selectedDate = selectedDate,
                    days = currentDays,
                    rules = rules,
                    eventDates = eventDates,
                    onDateSelected = onDateSelected,
                    modifier = Modifier
                        .alpha(if (reducedMotion) 1f - pageProgress else 1f)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            rotationX = if (reducedMotion) 0f else -90f * pageProgress
                        }
                )
            } else {
                CalendarPaperStack(
                    month = currentMonth,
                    selectedDate = selectedDate,
                    days = currentDays,
                    rules = rules,
                    eventDates = eventDates,
                    onDateSelected = onDateSelected
                )
                CalendarPaperStack(
                    month = pendingMonth,
                    selectedDate = selectedDate,
                    days = monthCalendarDays(pendingMonth.atDay(1)),
                    rules = emptyMap(),
                    eventDates = eventDates,
                    onDateSelected = onDateSelected,
                    modifier = Modifier
                        .alpha(if (reducedMotion) pageProgress else 1f)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            rotationX = if (reducedMotion) 0f else 90f * (1f - pageProgress)
                        }
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CalendarArrow(
                    icon = Icons.Outlined.ArrowBackIosNew,
                    contentDescription = "上个月",
                    enabled = !flipState.isFlipping
                ) {
                    flipState = requestCalendarFlip(flipState, CalendarFlipDirection.PREVIOUS)
                }
                CalendarArrow(
                    icon = Icons.Outlined.ArrowForwardIos,
                    contentDescription = "下个月",
                    enabled = !flipState.isFlipping
                ) {
                    flipState = requestCalendarFlip(flipState, CalendarFlipDirection.NEXT)
                }
            }
        }
    }
}

@Composable
private fun CalendarPaperStack(
    month: YearMonth,
    selectedDate: LocalDate,
    days: List<LocalDate>,
    rules: Map<LocalDate, ScheduleCalendarRuleUi>,
    eventDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        CalendarPaperBack(Modifier.offset(y = 8.dp).padding(horizontal = 8.dp), ScheduleGreen.copy(alpha = 0.42f))
        CalendarPaperBack(Modifier.offset(y = 4.dp).padding(horizontal = 4.dp), ScheduleSurfaceCream.copy(alpha = 0.88f))
        CalendarPaperPage(month, selectedDate, days, rules, eventDates, onDateSelected)
    }
}

@Composable
private fun CalendarPaperBack(modifier: Modifier, color: Color) {
    Surface(
        modifier = modifier.fillMaxWidth().height(382.dp),
        shape = RoundedCornerShape(22.dp),
        color = color
    ) {}
}

@Composable
private fun CalendarPaperPage(
    month: YearMonth,
    selectedDate: LocalDate,
    days: List<LocalDate>,
    rules: Map<LocalDate, ScheduleCalendarRuleUi>,
    eventDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = ScheduleSurfaceCream
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${month.year}年${month.monthValue}月",
                    color = ScheduleInk,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(7) {
                    Surface(
                        modifier = Modifier.size(width = 18.dp, height = 7.dp),
                        shape = RoundedCornerShape(50),
                        color = ScheduleGreen.copy(alpha = 0.9f)
                    ) {}
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        color = ScheduleInk.copy(alpha = 0.64f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            days.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        CalendarPaperDay(
                            date = date,
                            isOutsideMonth = date.month != month.month,
                            isSelected = date == selectedDate,
                            rule = rules[date],
                            hasEvent = date in eventDates,
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (rules.values.any { it.badge != null || it.manualMarker != null }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("休 = 节假日", color = ScheduleInk.copy(alpha = 0.65f), fontSize = 11.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("调 = 调休", color = ScheduleInk.copy(alpha = 0.65f), fontSize = 11.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("改 = 手动", color = ScheduleInk.copy(alpha = 0.65f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CalendarPaperDay(
    date: LocalDate,
    isOutsideMonth: Boolean,
    isSelected: Boolean,
    rule: ScheduleCalendarRuleUi?,
    hasEvent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val textColor = when {
        isSelected -> ScheduleInk
        isOutsideMonth -> ScheduleInk.copy(alpha = 0.28f)
        else -> ScheduleInk
    }
    Box(
        modifier = modifier
            .aspectRatio(1.02f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = if (isSelected) SchedulePurple.copy(alpha = 0.58f) else Color.Transparent
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = date.dayOfMonth.toString(),
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        rule?.badge?.let { badge ->
            Text(
                text = if (badge == ScheduleCalendarBadge.Holiday) "休" else "调",
                color = if (badge == ScheduleCalendarBadge.Holiday) ScheduleOrange else ScheduleGreen.copy(alpha = 1f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        rule?.manualMarker?.let { marker ->
            Text(
                text = marker,
                color = SchedulePurple,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 1.dp)
            )
        }
        if (hasEvent) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(4.dp)
                    .background(ScheduleOrange, CircleShape)
            )
        }
    }
}

@Composable
private fun CalendarArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = ScheduleInk, modifier = Modifier.size(16.dp))
    }
}
