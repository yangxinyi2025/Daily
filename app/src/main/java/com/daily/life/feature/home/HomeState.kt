package com.daily.life.feature.home

import com.daily.life.core.navigation.DailyDestination
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class HomeState(
    val greeting: String = "你好",
    val dateLabel: String = "",
    val todayCourseCount: Int = 0,
    val nextCourseLabel: String? = null,
    val hasTimetableData: Boolean = false,
    val todayCourses: List<HomeCourseRow> = emptyList(),
    val upcomingEventCount: Int = 0,
    val nextEventLabel: String? = null,
    val todaySchedules: List<HomeScheduleRow> = emptyList(),
    val latestWeightJin: Double? = null,
    val latestPeriod: HomePeriodSummary? = null,
    val nextPeriodStart: LocalDate? = null,
    val monthlySpendingCents: Long = 0L,
    val monthlyBudgetCents: Long? = null,
    val hasBillData: Boolean = false
)

{
    val cards: List<HomeCardState>
        get() = listOf(
            HomeCardState(
                title = "今日课表",
                value = if (hasTimetableData) {
                    "$todayCourseCount 节课 · ${nextCourseLabel ?: "今日课程已结束"}"
                } else {
                    "今天还没有课程"
                },
                actionLabel = if (hasTimetableData) "查看课表" else "导入第一份课表",
                destination = DailyDestination.Timetable
            ),
            HomeCardState(
                title = "最近日程",
                value = if (upcomingEventCount > 0) {
                    "${nextEventLabel ?: "查看近期日程"} · $upcomingEventCount 项待办"
                } else {
                    "近期没有日程"
                },
                actionLabel = if (upcomingEventCount > 0) "查看日程" else "添加第一项日程",
                destination = DailyDestination.Schedule
            ),
            HomeCardState(
                title = "健康记录",
                value = latestWeightJin?.let { "${formatWeight(it)} 斤" } ?: "还没有体重记录",
                actionLabel = if (latestWeightJin == null) "记录今天体重" else "查看体重趋势",
                destination = DailyDestination.Health
            ),
            HomeCardState(
                title = "本月账单",
                value = when {
                    !hasBillData -> "本月还没有账单"
                    monthlyBudgetCents == null -> formatCurrency(monthlySpendingCents)
                    else -> "${formatCurrency(monthlySpendingCents)} / ${formatCurrency(monthlyBudgetCents)}"
                },
                actionLabel = if (hasBillData) "查看账单" else "导入第一份账单",
                destination = DailyDestination.Bill
            )
        )
}

data class HomeCardState(
    val title: String,
    val value: String,
    val actionLabel: String?,
    val destination: DailyDestination?
)

data class HomeCourseRow(
    val startPeriod: Int,
    val courseName: String,
    val timeLabel: String = "",
    val detail: String
)

data class HomeScheduleRow(
    val id: Long,
    val title: String,
    val timeLabel: String,
    val location: String? = null
)

internal data class HomeCardContent(
    val items: List<HomeCardItem>,
    val hasContent: Boolean
)

internal data class HomeCardItem(
    val primaryText: String,
    val secondaryText: String?,
    val trailingText: String? = null
)

internal fun courseCardContent(state: HomeState): HomeCardContent {
    return if (state.todayCourses.isEmpty()) {
        HomeCardContent(
            items = listOf(HomeCardItem("今天还没有课程", null)),
            hasContent = false
        )
    } else {
        HomeCardContent(
            items = state.todayCourses.map { course ->
                HomeCardItem(
                    primaryText = course.courseName,
                    secondaryText = course.detail.takeIf { it.isNotBlank() },
                    trailingText = course.timeLabel.ifBlank { "第 ${course.startPeriod} 节" }
                )
            },
            hasContent = true
        )
    }
}

internal fun scheduleCardContent(state: HomeState): HomeCardContent {
    return if (state.todaySchedules.isEmpty()) {
        HomeCardContent(
            items = listOf(HomeCardItem("今天还没有待办", null)),
            hasContent = false
        )
    } else {
        HomeCardContent(
            items = state.todaySchedules.map { schedule ->
                HomeCardItem(
                    primaryText = schedule.title,
                    secondaryText = schedule.location?.takeIf { it.isNotBlank() } ?: "无",
                    trailingText = schedule.timeLabel
                )
            },
            hasContent = true
        )
    }
}

data class TimetableHomeSummary(
    val todayCourseCount: Int = 0,
    val nextCourseLabel: String? = null,
    val todayCourses: List<HomeCourseRow> = emptyList(),
    val isEmpty: Boolean = true
)

data class ScheduleHomeSummary(
    val nextEventLabel: String? = null,
    val upcomingCount: Int = 0,
    val todaySchedules: List<HomeScheduleRow> = emptyList(),
    val isEmpty: Boolean = true
)

data class HealthHomeSummary(
    val latestWeightJin: Double? = null,
    val latestPeriod: HomePeriodSummary? = null,
    val nextPeriodStart: LocalDate? = null,
    val isEmpty: Boolean = true
)

data class HomePeriodSummary(
    val startDate: LocalDate,
    val endDate: LocalDate
)

enum class HomeSection { COURSE, CALENDAR, HEALTH }

internal data class HomeHealthPresentation(
    val weightKg: String,
    val periodValue: String,
    val periodCaption: String
)

internal fun healthHomePresentation(state: HomeState): HomeHealthPresentation {
    val periodDays = state.latestPeriod?.let { period ->
        ChronoUnit.DAYS.between(period.startDate, period.endDate).toInt() + 1
    }
    return HomeHealthPresentation(
        weightKg = state.latestWeightJin?.div(2.0)
            ?.let { String.format(Locale.US, "%.1f", it) }
            ?: "尚未记录",
        periodValue = periodDays?.let { "$it 天" } ?: "尚未记录经期",
        periodCaption = if (state.nextPeriodStart == null) "记录后可预测经期" else "距离预计经期"
    )
}

data class BillHomeSummary(
    val monthlyExpenseCents: Long = 0L,
    val monthlyBudgetCents: Long? = null,
    val isEmpty: Boolean = true
)

interface TimetableSummaryRepository {
    val summary: Flow<TimetableHomeSummary>
}

interface ScheduleSummaryRepository {
    val summary: Flow<ScheduleHomeSummary>
}

interface HealthSummaryRepository {
    val summary: Flow<HealthHomeSummary>
}

interface BillSummaryRepository {
    val summary: Flow<BillHomeSummary>
}

class FakeTimetableSummaryRepository(
    override val summary: Flow<TimetableHomeSummary> = MutableStateFlow(TimetableHomeSummary())
) : TimetableSummaryRepository

class FakeScheduleSummaryRepository(
    override val summary: Flow<ScheduleHomeSummary> = MutableStateFlow(ScheduleHomeSummary())
) : ScheduleSummaryRepository

class FakeHealthSummaryRepository(
    override val summary: Flow<HealthHomeSummary> = MutableStateFlow(HealthHomeSummary())
) : HealthSummaryRepository

class FakeBillSummaryRepository(
    override val summary: Flow<BillHomeSummary> = MutableStateFlow(BillHomeSummary())
) : BillSummaryRepository

private fun formatWeight(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

private fun formatCurrency(cents: Long): String =
    String.format(java.util.Locale.US, "¥%,.2f", cents / 100.0)
