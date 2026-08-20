package com.daily.life.feature.home

import com.daily.life.core.navigation.DailyDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

data class HomeState(
    val greeting: String = "你好",
    val dateLabel: String = "",
    val cards: List<HomeCardState> = emptyList()
)

data class HomeCardState(
    val title: String,
    val value: String,
    val actionLabel: String?,
    val destination: DailyDestination?
)

data class TimetableHomeSummary(
    val todayCourseCount: Int = 0,
    val nextCourseLabel: String? = null,
    val isEmpty: Boolean = true
)

data class ScheduleHomeSummary(
    val nextEventLabel: String? = null,
    val upcomingCount: Int = 0,
    val isEmpty: Boolean = true
)

data class HealthHomeSummary(
    val latestWeightJin: Double? = null,
    val latestActivityLabel: String? = null,
    val isEmpty: Boolean = true
)

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
