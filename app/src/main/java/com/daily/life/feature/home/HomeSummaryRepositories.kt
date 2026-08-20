package com.daily.life.feature.home

import com.daily.life.core.database.ActivityRecordEntity
import com.daily.life.core.database.ActivityType
import com.daily.life.core.database.BudgetDao
import com.daily.life.core.database.CourseDao
import com.daily.life.core.database.HealthDao
import com.daily.life.core.database.ScheduleEventDao
import com.daily.life.core.database.SemesterDao
import com.daily.life.core.database.TransactionDao
import com.daily.life.core.database.TransactionDirection
import com.daily.life.core.datastore.DailyPreferences
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class DaoTimetableSummaryRepository(
    semesterDao: SemesterDao,
    private val courseDao: CourseDao,
    preferences: DailyPreferences,
    private val clock: Clock = Clock.systemDefaultZone()
) : TimetableSummaryRepository {
    override val summary: Flow<TimetableHomeSummary> =
        combine(
            semesterDao.observeAll(),
            preferences.currentSemesterId,
            preferences.semesterStartDate
        ) { semesters, currentSemesterId, preferredStartDate ->
            val semester = semesters.firstOrNull { it.id == currentSemesterId }
                ?: semesters.firstOrNull { it.isCurrent }
            semester?.let {
                TimetableQuery(
                    semesterId = it.id,
                    semesterStartDate = preferredStartDate ?: it.startDate
                )
            }
        }.flatMapLatest { query ->
            if (query == null) {
                flowOf(TimetableHomeSummary())
            } else {
                val today = LocalDate.now(clock)
                val week = ChronoUnit.DAYS.between(query.semesterStartDate, today).toInt() / 7 + 1
                if (week < 1) {
                    flowOf(TimetableHomeSummary())
                } else {
                    courseDao.observeBySemesterWeekAndDay(
                        semesterId = query.semesterId,
                        week = week,
                        dayOfWeek = today.dayOfWeek.value
                    ).map { courses ->
                        TimetableHomeSummary(
                            todayCourseCount = courses.size,
                            nextCourseLabel = courses.firstOrNull()?.let { course ->
                                buildString {
                                    append(course.courseName)
                                    append(" 第 ${course.startPeriod}-${course.endPeriod} 节")
                                    course.location?.takeIf(String::isNotBlank)?.let { append(" · $it") }
                                }
                            },
                            isEmpty = courses.isEmpty()
                        )
                    }
                }
            }
        }

    private data class TimetableQuery(
        val semesterId: Long,
        val semesterStartDate: LocalDate
    )
}

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
        summary = scheduleEventDao.observeBetween(now, end).map { events ->
            ScheduleHomeSummary(
                nextEventLabel = events.firstOrNull()?.let { event ->
                    "${event.title} ${EVENT_TIME_FORMATTER.format(Instant.ofEpochMilli(event.eventAt).atZone(zone))}"
                },
                upcomingCount = events.size,
                isEmpty = events.isEmpty()
            )
        }
    }

    private companion object {
        val EVENT_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.SIMPLIFIED_CHINESE)
    }
}

class DaoHealthSummaryRepository(
    healthDao: HealthDao,
    clock: Clock = Clock.systemDefaultZone()
) : HealthSummaryRepository {
    override val summary: Flow<HealthHomeSummary>

    init {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        summary = combine(
            healthDao.observeWeights(),
            healthDao.observeActivitiesBetween(dayStart, dayEnd)
        ) { weights, activities ->
            HealthHomeSummary(
                latestWeightJin = weights.firstOrNull()?.weightJin,
                latestActivityLabel = activities.firstOrNull()?.toHomeLabel(),
                isEmpty = weights.isEmpty() && activities.isEmpty()
            )
        }
    }

    private fun ActivityRecordEntity.toHomeLabel(): String {
        val activityName = when (activityType) {
            ActivityType.WALK -> "步行"
            ActivityType.RUN -> "跑步"
        }
        val detail = when {
            steps != null -> String.format(Locale.US, "%,d 步", steps)
            distanceMeters != null -> String.format(Locale.US, "%.1f 公里", distanceMeters / 1_000.0)
            durationMinutes != null -> "$durationMinutes 分钟"
            else -> "已记录"
        }
        return "今日$activityName $detail"
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
