package com.daily.life.feature.home

import com.daily.life.core.navigation.DailyDestination
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun emptyHomeUsesActionableStates() = runTest {
        val viewModel = HomeViewModel(
            timetableRepository = FakeTimetableSummaryRepository(),
            scheduleRepository = FakeScheduleSummaryRepository(),
            healthRepository = FakeHealthSummaryRepository(),
            billRepository = FakeBillSummaryRepository(),
            clock = fixedClock(),
            coroutineScope = backgroundScope
        )

        val state = viewModel.state.first { it.cards.size == 5 }

        assertEquals("你好", state.greeting)
        assertEquals("8月20日 星期四", state.dateLabel)
        assertEquals(0, state.todayCourseCount)
        assertEquals(0, state.upcomingEventCount)
        assertEquals(0L, state.monthlySpendingCents)
        assertEquals(null, state.nextCourseLabel)
        assertEquals(null, state.nextEventLabel)
        assertEquals(null, state.latestWeightJin)
        assertEquals(null, state.latestActivityLabel)
        assertEquals(null, state.monthlyBudgetCents)
        assertEquals(5, state.cards.size)
        assertTrue(state.cards.any { it.actionLabel == "导入第一份课表" && it.destination == DailyDestination.Timetable })
        assertTrue(state.cards.any { it.actionLabel == "记录今天体重" && it.destination == DailyDestination.Health })
    }

    @Test
    fun populatedHomeCombinesSummaryValues() = runTest {
        val viewModel = HomeViewModel(
            timetableRepository = FakeTimetableSummaryRepository(
                MutableStateFlow(
                    TimetableHomeSummary(
                        todayCourseCount = 2,
                        nextCourseLabel = "数据库 第 3-4 节",
                        isEmpty = false
                    )
                )
            ),
            scheduleRepository = FakeScheduleSummaryRepository(
                MutableStateFlow(
                    ScheduleHomeSummary(
                        nextEventLabel = "晚上体测 19:00",
                        upcomingCount = 1,
                        isEmpty = false
                    )
                )
            ),
            healthRepository = FakeHealthSummaryRepository(
                MutableStateFlow(
                    HealthHomeSummary(
                        latestWeightJin = 120.5,
                        latestActivityLabel = "今日步行 6,000 步",
                        isEmpty = false
                    )
                )
            ),
            billRepository = FakeBillSummaryRepository(
                MutableStateFlow(
                    BillHomeSummary(
                        monthlyExpenseCents = 23_450L,
                        monthlyBudgetCents = 100_000L,
                        isEmpty = false
                    )
                )
            ),
            clock = fixedClock(),
            coroutineScope = backgroundScope
        )

        val state = viewModel.state.first { homeState ->
            homeState.cards.any { it.title == "本月账单" && it.value.contains("¥234.50") }
        }
        val cards = state.cards

        assertEquals(2, state.todayCourseCount)
        assertEquals("数据库 第 3-4 节", state.nextCourseLabel)
        assertEquals(1, state.upcomingEventCount)
        assertEquals("晚上体测 19:00", state.nextEventLabel)
        assertEquals(120.5, state.latestWeightJin ?: Double.NaN, 0.0)
        assertEquals("今日步行 6,000 步", state.latestActivityLabel)
        assertEquals(23_450L, state.monthlySpendingCents)
        assertEquals(100_000L, state.monthlyBudgetCents)
        assertTrue(cards.any { it.title == "今日课表" && it.value.contains("2 节课") })
        assertTrue(cards.any { it.title == "最近日程" && it.value.contains("晚上体测") })
        assertTrue(cards.any { it.title == "健康记录" && it.value.contains("120.5 斤") })
        assertTrue(cards.any { it.title == "本月账单" && it.value.contains("¥234.50 / ¥1,000.00") })
    }

    @Test
    fun summaryFlowChangesRefreshHomeCards() = runTest {
        val timetable = MutableStateFlow(TimetableHomeSummary())
        val viewModel = HomeViewModel(
            timetableRepository = FakeTimetableSummaryRepository(timetable),
            scheduleRepository = FakeScheduleSummaryRepository(),
            healthRepository = FakeHealthSummaryRepository(),
            billRepository = FakeBillSummaryRepository(),
            clock = fixedClock(),
            coroutineScope = backgroundScope
        )

        timetable.value = TimetableHomeSummary(
            todayCourseCount = 1,
            nextCourseLabel = "高等数学 第 1-2 节",
            isEmpty = false
        )
        runCurrent()

        assertEquals(1, viewModel.state.value.todayCourseCount)
        assertEquals("高等数学 第 1-2 节", viewModel.state.value.nextCourseLabel)
        assertTrue(
            viewModel.state.value.cards.any {
                it.title == "今日课表" && it.value.contains("高等数学")
            }
        )
    }

    private fun fixedClock(): Clock = Clock.fixed(
        Instant.parse("2026-08-20T08:00:00Z"),
        ZoneId.of("Asia/Shanghai")
    )
}
