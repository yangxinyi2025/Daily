package com.daily.life.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daily.life.core.navigation.DailyDestination
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    timetableRepository: TimetableSummaryRepository,
    scheduleRepository: ScheduleSummaryRepository,
    healthRepository: HealthSummaryRepository,
    billRepository: BillSummaryRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    coroutineScope: CoroutineScope? = null
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    val state: StateFlow<HomeState> =
        combine(
            timetableRepository.summary,
            scheduleRepository.summary,
            healthRepository.summary,
            billRepository.summary
        ) { timetable, schedule, health, bill ->
            createHomeState(timetable, schedule, health, bill)
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = createHomeState(
                timetable = TimetableHomeSummary(),
                schedule = ScheduleHomeSummary(),
                health = HealthHomeSummary(),
                bill = BillHomeSummary()
            )
        )

    private fun createHomeState(
        timetable: TimetableHomeSummary,
        schedule: ScheduleHomeSummary,
        health: HealthHomeSummary,
        bill: BillHomeSummary
    ): HomeState = HomeState(
        greeting = "你好",
        dateLabel = DATE_FORMATTER.format(LocalDate.now(clock)),
        cards = listOf(
            timetable.toCard(),
            schedule.toCard(),
            health.toWeightCard(),
            health.toActivityCard(),
            bill.toCard()
        )
    )

    private fun TimetableHomeSummary.toCard(): HomeCardState = HomeCardState(
        title = "今日课表",
        value = if (isEmpty) {
            "今天还没有课程"
        } else {
            "$todayCourseCount 节课 · ${nextCourseLabel ?: "今日课程已结束"}"
        },
        actionLabel = if (isEmpty) "导入第一份课表" else "查看课表",
        destination = DailyDestination.Timetable
    )

    private fun ScheduleHomeSummary.toCard(): HomeCardState = HomeCardState(
        title = "最近日程",
        value = if (isEmpty) {
            "近期没有日程"
        } else {
            "${nextEventLabel ?: "查看近期日程"} · $upcomingCount 项待办"
        },
        actionLabel = if (isEmpty) "添加第一项日程" else "查看日程",
        destination = DailyDestination.Schedule
    )

    private fun HealthHomeSummary.toWeightCard(): HomeCardState = HomeCardState(
        title = "健康记录",
        value = latestWeightJin?.let { "${formatWeight(it)} 斤" } ?: "还没有体重记录",
        actionLabel = if (latestWeightJin == null) "记录今天体重" else "查看体重趋势",
        destination = DailyDestination.Health
    )

    private fun HealthHomeSummary.toActivityCard(): HomeCardState = HomeCardState(
        title = "今日活动",
        value = latestActivityLabel ?: "今天还没有活动记录",
        actionLabel = if (latestActivityLabel == null) "记录今天活动" else "查看活动",
        destination = DailyDestination.Health
    )

    private fun BillHomeSummary.toCard(): HomeCardState = HomeCardState(
        title = "本月账单",
        value = if (isEmpty) {
            "本月还没有账单"
        } else if (monthlyBudgetCents == null) {
            formatCurrency(monthlyExpenseCents)
        } else {
            "${formatCurrency(monthlyExpenseCents)} / ${formatCurrency(monthlyBudgetCents)}"
        },
        actionLabel = if (isEmpty) "导入第一份账单" else "查看账单",
        destination = DailyDestination.Bill
    )

    private fun formatWeight(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun formatCurrency(cents: Long): String =
        String.format(Locale.US, "¥%,.2f", cents / 100.0)

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
    }
}
