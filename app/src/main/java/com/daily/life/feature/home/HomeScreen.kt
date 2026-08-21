package com.daily.life.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daily.life.core.designsystem.QuietSkyListRow
import com.daily.life.core.designsystem.QuietSkyPageHeader
import com.daily.life.core.designsystem.QuietSkySectionCard
import com.daily.life.core.designsystem.SkyAccent
import com.daily.life.core.designsystem.SkyOutline
import com.daily.life.core.designsystem.SkyPrimary
import com.daily.life.core.designsystem.SkySurface
import com.daily.life.core.navigation.DailyDestination

@Composable
fun HomeScreen(
    state: HomeState,
    onOpenSettings: () -> Unit,
    onDestinationSelected: (DailyDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuietSkyPageHeader(
            title = "首页",
            subtitle = "今天的重点，一眼看清。",
            actions = {
                Icon(
                    imageVector = Icons.Outlined.NotificationsNone,
                    contentDescription = "通知",
                    modifier = Modifier.padding(12.dp)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "打开设置"
                    )
                }
            }
        )

        HomeOverviewCard(state = state)

        CourseSection(
            courses = state.todayCourses,
            onOpenTimetable = { onDestinationSelected(DailyDestination.Timetable) }
        )

        ScheduleSection(
            schedules = state.todaySchedules,
            onOpenSchedule = { onDestinationSelected(DailyDestination.Schedule) }
        )
    }
}

@Composable
private fun HomeOverviewCard(state: HomeState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = SkySurface),
        border = BorderStroke(1.dp, SkyOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = SkyAccent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Text(
                        text = "今天",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = "${state.greeting}，今天还有 ${state.upcomingEventCount} 件事",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                OverviewMetric(
                    label = "课程",
                    value = state.todayCourseCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                OverviewMetric(
                    label = "日程",
                    value = state.upcomingEventCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                OverviewMetric(
                    label = "预算",
                    value = state.budgetUsageLabel(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge,
            color = SkyPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CourseSection(
    courses: List<HomeCourseRow>,
    onOpenTimetable: () -> Unit
) {
    QuietSkySectionCard(
        modifier = Modifier.clickable(onClick = onOpenTimetable)
    ) {
        Text(text = "今日课表", style = MaterialTheme.typography.titleLarge)
        if (courses.isEmpty()) {
            QuietSkyListRow(
                icon = Icons.Outlined.CalendarMonth,
                title = "今天还没有课程",
                iconContentDescription = "课表",
                trailing = {
                    TextButton(onClick = onOpenTimetable) {
                        Text(text = "查看课表")
                    }
                }
            )
        } else {
            courses.forEach { course ->
                QuietSkyListRow(
                    icon = Icons.Outlined.CalendarMonth,
                    title = "第 ${course.startPeriod} 节 · ${course.courseName}",
                    subtitle = course.detail.takeIf { it.isNotBlank() },
                    iconContentDescription = "课程"
                )
            }
        }
    }
}

@Composable
private fun ScheduleSection(
    schedules: List<HomeScheduleRow>,
    onOpenSchedule: () -> Unit
) {
    QuietSkySectionCard(
        modifier = Modifier.clickable(onClick = onOpenSchedule)
    ) {
        Text(text = "今日待办", style = MaterialTheme.typography.titleLarge)
        if (schedules.isEmpty()) {
            QuietSkyListRow(
                icon = Icons.Outlined.EventNote,
                iconTint = SkyAccent,
                title = "今天还没有待办",
                iconContentDescription = "日程",
                trailing = {
                    TextButton(onClick = onOpenSchedule) {
                        Text(text = "查看日程")
                    }
                }
            )
        } else {
            schedules.forEach { schedule ->
                QuietSkyListRow(
                    icon = Icons.Outlined.EventNote,
                    iconTint = SkyAccent,
                    title = schedule.title,
                    subtitle = schedule.timeLabel,
                    iconContentDescription = "日程",
                    trailing = {
                        Icon(
                            imageVector = Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = "未完成",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                )
            }
        }
    }
}

private fun HomeState.budgetUsageLabel(): String {
    val budget = monthlyBudgetCents?.takeIf { it > 0L } ?: return "未设置"
    return "${monthlySpendingCents * 100 / budget}%"
}
