package com.daily.life.feature.home

import com.daily.life.core.database.BudgetDao
import com.daily.life.core.database.CourseDao
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.HealthDao
import com.daily.life.core.database.PeriodDao
import com.daily.life.core.database.ScheduleEventDao
import com.daily.life.core.database.SemesterDao
import com.daily.life.core.database.SemesterPeriodDao
import com.daily.life.core.database.TransactionDao
import com.daily.life.core.database.TransactionDirection
import com.daily.life.core.datastore.DailyPreferences
import com.daily.life.feature.health.PeriodPredictionCalculator
import com.daily.life.feature.health.PeriodRecord
import com.daily.life.feature.timetable.defaultSemesterPeriodTimes
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class DaoTimetableSummaryRepository(
    semesterDao: SemesterDao,
    private val courseDao: CourseDao,
    private val semesterPeriodDao: SemesterPeriodDao,
    preferences: DailyPreferences,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val currentPeriodProvider: (LocalTime) -> Int? = ::defaultUpcomingPeriod,
    refreshTicks: Flow<Unit> = minuteTicker()
) : TimetableSummaryRepository {
    override val summary: Flow<TimetableHomeSummary> =
        combine(
            semesterDao.observeAll(),
            preferences.currentSemesterId,
            preferences.semesterStartDate,
            refreshTicks.onStart { emit(Unit) }
        ) { semesters, currentSemesterId, preferredStartDate, _ ->
            val today = LocalDate.now(clock)
            val currentPeriod = currentPeriodProvider(LocalTime.now(clock))
            val semester = semesters.firstOrNull { it.id == currentSemesterId }
                ?: semesters.firstOrNull { it.isCurrent }
            semester?.let {
                val semesterStartDate = preferredStartDate ?: it.startDate
                TimetableQuery(
                    semesterId = it.id,
                    week = ChronoUnit.DAYS.between(semesterStartDate, today).toInt() / 7 + 1,
                    dayOfWeek = today.dayOfWeek.value,
                    currentPeriod = currentPeriod
                )
            }
        }.flatMapLatest { query ->
            if (query == null || query.week < 1) {
                flowOf(TimetableHomeSummary())
            } else {
                combine(
                    courseDao.observeBySemesterWeekAndDay(
                        semesterId = query.semesterId,
                        week = query.week,
                        dayOfWeek = query.dayOfWeek
                    ),
                    semesterPeriodDao.observeBySemester(query.semesterId)
                ) { courses, configuredPeriods ->
                    val periodTimes = configuredPeriods
                        .associate { it.period to (it.startTime to it.endTime) }
                        .ifEmpty {
                            defaultSemesterPeriodTimes().associate { it.period to (it.startTime to it.endTime) }
                        }
                    val nextCourse = selectNextCourse(courses, query.currentPeriod)
                    TimetableHomeSummary(
                        todayCourseCount = courses.size,
                        nextCourseLabel = if (courses.isEmpty()) {
                            null
                        } else {
                            nextCourse?.toCourseLabel() ?: "今日课程已结束"
                        },
                        todayCourses = courses.map { course ->
                            HomeCourseRow(
                                startPeriod = course.startPeriod,
                                courseName = course.courseName,
                                timeLabel = course.timeLabel(periodTimes),
                                detail = course.location?.takeIf(String::isNotBlank).orEmpty()
                            )
                        },
                        isEmpty = courses.isEmpty()
                    )
                }
            }
        }

    private fun selectNextCourse(
        courses: List<CourseEntity>,
        currentPeriod: Int?
    ): CourseEntity? {
        return if (currentPeriod == null) {
            null
        } else {
            courses.firstOrNull { it.startPeriod >= currentPeriod }
        }
    }

    private fun CourseEntity.toCourseLabel(): String =
        buildString {
            append(courseName)
            append(" 第 $startPeriod-$endPeriod 节")
            location?.takeIf(String::isNotBlank)?.let { append(" · $it") }
        }

    private fun CourseEntity.timeLabel(periodTimes: Map<Int, Pair<LocalTime, LocalTime>>): String {
        val start = periodTimes[startPeriod]?.first
        val end = periodTimes[endPeriod]?.second
        return if (start != null && end != null) {
            "$start–$end"
        } else {
            "第 $startPeriod-$endPeriod 节"
        }
    }

    private data class TimetableQuery(
        val semesterId: Long,
        val week: Int,
        val dayOfWeek: Int,
        val currentPeriod: Int?
    )
}

private fun minuteTicker(): Flow<Unit> = flow {
    while (true) {
        delay(60_000L)
        emit(Unit)
    }
}

private data class PeriodSlot(
    val period: Int,
    val startInclusive: LocalTime,
    val endInclusive: LocalTime
)

private val DAILY_PERIOD_SLOTS = listOf(
    PeriodSlot(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
    PeriodSlot(2, LocalTime.of(8, 55), LocalTime.of(9, 40)),
    PeriodSlot(3, LocalTime.of(10, 10), LocalTime.of(10, 55)),
    PeriodSlot(4, LocalTime.of(11, 5), LocalTime.of(11, 50)),
    PeriodSlot(5, LocalTime.of(14, 0), LocalTime.of(14, 45)),
    PeriodSlot(6, LocalTime.of(14, 55), LocalTime.of(15, 40)),
    PeriodSlot(7, LocalTime.of(16, 10), LocalTime.of(16, 55)),
    PeriodSlot(8, LocalTime.of(17, 5), LocalTime.of(17, 50)),
    PeriodSlot(9, LocalTime.of(19, 0), LocalTime.of(19, 45)),
    PeriodSlot(10, LocalTime.of(19, 55), LocalTime.of(20, 40))
)

private fun defaultUpcomingPeriod(now: LocalTime): Int? =
    DAILY_PERIOD_SLOTS.firstOrNull { slot ->
        now <= slot.endInclusive
    }?.period

class DaoScheduleSummaryRepository(
    scheduleEventDao: ScheduleEventDao,
    clock: Clock = Clock.systemDefaultZone()
) : ScheduleSummaryRepository {
    override val summary: Flow<ScheduleHomeSummary>

    init {
        val zone = clock.zone
        val now = clock.instant().toEpochMilli()
        val end = LocalDate.now(clock)
            .plusDays(7)
            .plusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli() - 1L
        val today = LocalDate.now(clock)
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        summary = combine(
            scheduleEventDao.observeBetween(now, end),
            scheduleEventDao.observeBetween(todayStart, todayEnd)
        ) { events, todayEvents ->
            ScheduleHomeSummary(
                nextEventLabel = events.firstOrNull()?.let { event ->
                    "${event.title} ${EVENT_TIME_FORMATTER.format(Instant.ofEpochMilli(event.eventAt).atZone(zone))}"
                },
                upcomingCount = events.size,
                todaySchedules = todayEvents.map { event ->
                    HomeScheduleRow(
                        id = event.id,
                        title = event.title,
                        timeLabel = TODAY_EVENT_TIME_FORMATTER.format(
                            Instant.ofEpochMilli(event.eventAt).atZone(zone)
                        ),
                        location = event.location
                    )
                },
                isEmpty = events.isEmpty()
            )
        }
    }

    private companion object {
        val EVENT_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
        val TODAY_EVENT_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm", Locale.SIMPLIFIED_CHINESE)
    }
}

class DaoHealthSummaryRepository(
    healthDao: HealthDao,
    periodDao: PeriodDao,
    preferences: DailyPreferences
) : HealthSummaryRepository {
    override val summary: Flow<HealthHomeSummary> =
        combine(healthDao.observeWeights(), periodDao.observeAll(), preferences.menstrualCycleDays) {
                weights, periods, cycleDays ->
            val latestPeriod = periods.maxByOrNull { it.startDate }
            HealthHomeSummary(
                latestWeightJin = weights.firstOrNull()?.weightJin,
                latestPeriod = latestPeriod?.let { HomePeriodSummary(it.startDate, it.endDate) },
                nextPeriodStart = PeriodPredictionCalculator.nextStartDate(
                    periods.map { PeriodRecord(it.id, it.startDate, it.endDate) },
                    cycleDays
                ),
                isEmpty = weights.isEmpty() && periods.isEmpty()
            )
        }
}

class DaoBillSummaryRepository(
    transactionDao: TransactionDao,
    budgetDao: BudgetDao,
    preferences: DailyPreferences,
    private val clock: Clock = Clock.systemDefaultZone()
) : BillSummaryRepository {
    override val summary: Flow<BillHomeSummary> =
        combine(
            transactionDao.observeAll(),
            budgetDao.observeAll(),
            preferences.defaultBudgetCents
        ) { transactions, budgets, defaultBudget ->
            val zone: ZoneId = clock.zone
            val month = YearMonth.now(clock)
            val monthlyExpenses = transactions.filter { transaction ->
                transaction.direction == TransactionDirection.EXPENSE &&
                    YearMonth.from(
                        Instant.ofEpochMilli(transaction.occurredAt).atZone(zone)
                    ) == month
            }
            BillHomeSummary(
                monthlyExpenseCents = monthlyExpenses.sumOf { it.amountCents },
                monthlyBudgetCents = budgets.firstOrNull { it.month == month.toString() }?.budgetCents
                    ?: defaultBudget,
                isEmpty = monthlyExpenses.isEmpty()
            )
        }
}
