package com.daily.life.feature.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daily.life.R
import com.daily.life.core.designsystem.SkyAccent
import com.daily.life.core.designsystem.SkyBackground
import com.daily.life.core.designsystem.SkyCoolBorder
import com.daily.life.core.designsystem.SkyInk
import com.daily.life.core.designsystem.SkyMutedText
import com.daily.life.core.designsystem.SkyPrimary
import com.daily.life.core.designsystem.SkyPurpleSurface
import com.daily.life.core.designsystem.SkySecondary
import com.daily.life.core.designsystem.SkySurface
import com.daily.life.core.designsystem.SkyWarm
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun ScheduleMonthScreen(
    state: ScheduleState,
    onViewModeChange: (ScheduleViewMode) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onCreate: () -> Unit,
    onQuickCreate: (ScheduleQuickAction) -> Unit,
    onEdit: (ScheduleEvent) -> Unit,
    onDelete: (Long) -> Unit,
    onSaveEditor: () -> Unit,
    onDismissEditor: () -> Unit,
    onEditorChange: (ScheduleEditorState) -> Unit,
    canReadSystemCalendar: Boolean
) {
    val zone = remember { ZoneId.systemDefault() }
    val calendarDays = remember(state.selectedDate) { monthCalendarDays(state.selectedDate) }
    val calendarBadges = rememberSystemCalendarBadges(state.selectedDate, canReadSystemCalendar)
    val eventDates = remember(state.events) { state.events.map { it.eventAt.atZone(zone).toLocalDate() } }
    val selectedEvents = state.events
        .filter { it.eventAt.atZone(zone).toLocalDate() == state.selectedDate }
        .sortedBy { it.eventAt }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(SkyBackground),
        contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { SchedulePageHeader(onCreate = onCreate) }
        item {
            ScheduleMonthCard(
                state = state,
                days = calendarDays,
                badges = calendarBadges,
                eventDates = eventDates,
                onDateSelected = onDateSelected,
                onPreviousMonth = { onDateSelected(state.selectedDate.minusMonths(1).withDayOfMonth(1)) },
                onNextMonth = { onDateSelected(state.selectedDate.plusMonths(1).withDayOfMonth(1)) }
            )
        }
        item {
            ScheduleSelectedDayCard(
                state = state,
                events = selectedEvents,
                zone = zone,
                onCreate = onCreate,
                onEdit = onEdit,
                onDelete = onDelete
            )
        }
        item { ScheduleQuickCreate(onCreate = onCreate, onQuickCreate = onQuickCreate) }
        if (ScheduleDashboardSection.Editor in scheduleDashboardSectionOrder(state.editor != null)) {
            state.editor?.let { editor ->
                item {
                    ScheduleEditorScreen(
                        state = editor,
                        onChange = onEditorChange,
                        onSave = onSaveEditor,
                        onDismiss = onDismissEditor,
                        onDelete = editor.id?.let { id -> { onDelete(id); onDismissEditor() } }
                    )
                }
            }
        }
        state.statusMessage?.let { message -> item { Text(message, color = SkySecondary, fontSize = 14.sp) } }
    }
}

@Composable
private fun rememberSystemCalendarBadges(
    month: LocalDate,
    canReadSystemCalendar: Boolean
): Map<LocalDate, ScheduleCalendarBadge> {
    val context = LocalContext.current
    val reader = remember(context) { SystemCalendarDayBadgeReader.from(context) }
    val badges by produceState(
        initialValue = emptyMap(),
        key1 = month,
        key2 = canReadSystemCalendar
    ) {
        value = if (canReadSystemCalendar) reader.readMonth(month) else emptyMap()
    }
    return badges
}

@Composable
private fun SchedulePageHeader(onCreate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("日程", color = SkyInk, fontSize = 32.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
            Text("把重要的事，留在恰好的时间。", modifier = Modifier.padding(top = 2.dp), color = SkyMutedText, fontSize = 14.sp, lineHeight = 20.sp)
        }
        IconButton(onClick = onCreate, modifier = Modifier.size(46.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = "新建日程", tint = SkyAccent, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun ScheduleMonthCard(
    state: ScheduleState,
    days: List<LocalDate>,
    badges: Map<LocalDate, ScheduleCalendarBadge>,
    eventDates: List<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    ScheduleReferenceCard(contentPadding = PaddingValues(start = 16.dp, top = 15.dp, end = 16.dp, bottom = 12.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(45.dp)) {
            Image(
                painter = painterResource(R.drawable.schedule_calendar_illustration),
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-18).dp, y = (-22).dp).width(152.dp).height(96.dp).alpha(0.55f),
                contentScale = ContentScale.Fit
            )
            Row(modifier = Modifier.fillMaxWidth().padding(end = 94.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.selectedDate.monthValue}月", color = SkyInk, fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                Text("${state.selectedDate.year}年", modifier = Modifier.padding(start = 10.dp, top = 3.dp), color = SkyAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(top = 1.dp)) {
                ScheduleMonthArrow(Icons.Outlined.ArrowBackIosNew, "上个月", onPreviousMonth)
                Spacer(Modifier.width(11.dp))
                ScheduleMonthArrow(Icons.Outlined.ArrowForwardIos, "下个月", onNextMonth)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(label, modifier = Modifier.weight(1f), color = SkyMutedText, fontSize = 14.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
            }
        }
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    ScheduleCalendarDay(
                        date = date,
                        isOutsideMonth = date.month != state.selectedDate.month,
                        isSelected = date == state.selectedDate,
                        badge = badges[date],
                        hasEvent = eventDatesForCalendar(date, eventDates).isNotEmpty(),
                        onClick = { onDateSelected(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (badges.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                ScheduleBadgeLegend(ScheduleCalendarBadge.Holiday, "节假日")
                Spacer(Modifier.width(20.dp))
                ScheduleBadgeLegend(ScheduleCalendarBadge.AdjustedWorkday, "调休")
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ScheduleMonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(icon, contentDescription = contentDescription, tint = SkyInk, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun ScheduleCalendarDay(
    date: LocalDate,
    isOutsideMonth: Boolean,
    isSelected: Boolean,
    badge: ScheduleCalendarBadge?,
    hasEvent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val textColor = when {
        isSelected -> SkyAccent
        isOutsideMonth -> SkyMutedText.copy(alpha = 0.45f)
        else -> SkyInk
    }
    Box(modifier = modifier.aspectRatio(2.25f).clickable(onClick = onClick), contentAlignment = Alignment.TopCenter) {
        Surface(modifier = Modifier.size(36.dp), shape = CircleShape, color = if (isSelected) SkyPurpleSurface else Color.Transparent) {
            Box(contentAlignment = Alignment.Center) {
                Text(date.dayOfMonth.toString(), color = textColor, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        when {
            badge != null -> ScheduleBadge(
                badge = badge,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp)
            )
            hasEvent -> Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(4.dp).background(SkyAccent, CircleShape))
        }
    }
}

@Composable
private fun ScheduleBadge(badge: ScheduleCalendarBadge, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(12.dp),
        shape = CircleShape,
        color = if (badge == ScheduleCalendarBadge.Holiday) SkyWarm.copy(alpha = 0.82f) else Color(0xFF8FB6FA)
    ) {
        Box(contentAlignment = Alignment.Center) { Text(badge.label, color = Color.White, fontSize = 7.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ScheduleBadgeLegend(badge: ScheduleCalendarBadge, text: String) {
    ScheduleBadge(badge)
    Text(" = $text", modifier = Modifier.padding(start = 4.dp), color = SkyMutedText, fontSize = 12.sp)
}

@Composable
private fun ScheduleSelectedDayCard(
    state: ScheduleState,
    events: List<ScheduleEvent>,
    zone: ZoneId,
    onCreate: () -> Unit,
    onEdit: (ScheduleEvent) -> Unit,
    onDelete: (Long) -> Unit
) {
    ScheduleReferenceCard(modifier = if (events.isEmpty()) Modifier.height(124.dp) else Modifier) {
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(R.drawable.schedule_note_illustration),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 14.dp).width(142.dp).height(90.dp).alpha(0.20f),
                    contentScale = ContentScale.Fit
                )
                ScheduleEmptyDayCard(state, onCreate)
            }
        } else {
            ScheduleEventsDayCard(state, events, zone, onEdit, onDelete)
        }
    }
}

@Composable
private fun ScheduleEmptyDayCard(state: ScheduleState, onCreate: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(modifier = Modifier.size(38.dp), color = SkyPurpleSurface, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = SkyAccent, modifier = Modifier.padding(8.dp))
        }
        Column(modifier = Modifier.padding(start = 11.dp).weight(1f)) {
            Text("${state.selectedDate.monthValue}月${state.selectedDate.dayOfMonth}日 · ${state.selectedDate.dayOfWeek.chineseLabel()}", color = SkyInk, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
            Text("这一天还没有日程", modifier = Modifier.padding(top = 6.dp), color = SkyInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text("把想做的事安排下来", modifier = Modifier.padding(top = 2.dp), color = SkyMutedText, fontSize = 14.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("暂无安排", color = SkyMutedText, fontSize = 12.sp)
            Text("新建", modifier = Modifier.padding(top = 22.dp).clickable(onClick = onCreate), color = SkyAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ScheduleEventsDayCard(state: ScheduleState, events: List<ScheduleEvent>, zone: ZoneId, onEdit: (ScheduleEvent) -> Unit, onDelete: (Long) -> Unit) {
    Column {
        Text("${state.selectedDate.monthValue}月${state.selectedDate.dayOfMonth}日 · ${state.selectedDate.dayOfWeek.chineseLabel()}", color = SkyInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        events.forEach { event ->
            val time = event.eventAt.atZone(zone).toLocalTime().toString().take(5)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable { onEdit(event) }, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(3.dp).height(32.dp).background(SkyAccent, RoundedCornerShape(3.dp)))
                Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(event.title, color = SkyInk, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$time · ${event.reminderMode.displayLabel()}", color = SkyMutedText, fontSize = 13.sp)
                }
                Text("删除", color = SkyWarm, fontSize = 13.sp, modifier = Modifier.clickable { onDelete(event.id) })
            }
        }
    }
}

@Composable
private fun ScheduleQuickCreate(onCreate: () -> Unit, onQuickCreate: (ScheduleQuickAction) -> Unit) {
    ScheduleReferenceCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(30.dp), shape = CircleShape, color = SkyPurpleSurface) {
                Icon(Icons.Outlined.Bolt, contentDescription = null, tint = SkyAccent, modifier = Modifier.padding(5.dp))
            }
            Text("快捷创建", modifier = Modifier.padding(start = 8.dp), color = SkyInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.clickable(onClick = onCreate), verticalAlignment = Alignment.CenterVertically) {
                Text("新建日程", color = SkyAccent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Outlined.ChevronRight, contentDescription = "新建日程", tint = SkyAccent, modifier = Modifier.size(18.dp))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScheduleQuickAction.values().forEach { action -> ScheduleQuickActionChip(action) { onQuickCreate(action) } }
        }
    }
}

@Composable
private fun ScheduleQuickActionChip(action: ScheduleQuickAction, onClick: () -> Unit) {
    val (icon, tint) = when (action) {
        ScheduleQuickAction.EXAM -> Icons.Outlined.School to SkyAccent
        ScheduleQuickAction.BIRTHDAY -> Icons.Outlined.Cake to Color(0xFFFF9D8D)
        ScheduleQuickAction.SMALL_THING -> Icons.Outlined.StarBorder to SkyAccent
    }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(42.dp).wrapContentWidth(),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, SkyCoolBorder),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint)
    ) {
        Icon(icon, contentDescription = action.title, tint = tint, modifier = Modifier.size(20.dp))
        Text(action.title, modifier = Modifier.padding(start = 7.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun ScheduleReferenceCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SkySurface),
        border = BorderStroke(1.dp, SkyCoolBorder.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

private fun java.time.DayOfWeek.chineseLabel(): String = when (this) {
    java.time.DayOfWeek.MONDAY -> "周一"
    java.time.DayOfWeek.TUESDAY -> "周二"
    java.time.DayOfWeek.WEDNESDAY -> "周三"
    java.time.DayOfWeek.THURSDAY -> "周四"
    java.time.DayOfWeek.FRIDAY -> "周五"
    java.time.DayOfWeek.SATURDAY -> "周六"
    java.time.DayOfWeek.SUNDAY -> "周日"
}
