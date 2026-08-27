package com.daily.life.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        todayCourseCount = timetable.todayCourseCount,
        nextCourseLabel = timetable.nextCourseLabel,
        hasTimetableData = !timetable.isEmpty,
        todayCourses = timetable.todayCourses,
        upcomingEventCount = schedule.upcomingCount,
        nextEventLabel = schedule.nextEventLabel,
        todaySchedules = schedule.todaySchedules,
        latestWeightJin = health.latestWeightJin,
        monthlySpendingCents = bill.monthlyExpenseCents,
        monthlyBudgetCents = bill.monthlyBudgetCents,
        hasBillData = !bill.isEmpty
    )

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
    }
}
