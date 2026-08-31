package com.daily.life.feature.bill

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.TransactionDirection
import com.daily.life.core.database.TransactionEntity
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BillViewModelTest {
    private lateinit var database: DailyDatabase
    private lateinit var preferences: DailyPreferences
    private lateinit var preferencesFile: File
    private lateinit var repository: BillRepository
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock: Clock = Clock.fixed(
        Instant.parse("2026-08-20T08:00:00Z"),
        zoneId
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = DailyDatabase.buildInMemory(context)
        preferencesFile = File.createTempFile("daily-bill-view-model", ".preferences_pb").also(File::delete)
        preferences = DailyPreferences.create(produceFile = { preferencesFile })
        repository = BillRepository(
            database = database,
            preferences = preferences,
            clock = clock,
            queryDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        database.close()
        preferencesFile.delete()
    }

    @Test
    fun weekPeriodNavigatesAcrossConsecutiveWeeks() = runTest {
        seedTransactions(
            transaction("当前周午餐", LocalDate.of(2026, 8, 18), 1_200L),
            transaction("上一周午餐", LocalDate.of(2026, 8, 11), 3_400L),
            transaction("下一周午餐", LocalDate.of(2026, 8, 25), 5_600L)
        )
        val viewModel = BillViewModel(
            repository = repository,
            clock = clock,
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )

        viewModel.setPeriod(BillPeriod.WEEK)
        advanceUntilIdle()
        awaitRange(viewModel, LocalDate.of(2026, 8, 17), testScheduler)

        assertEquals("2026年8月17日 - 2026年8月23日", viewModel.state.value.periodLabel)
        assertEquals("上周", viewModel.state.value.previousPeriodLabel)
        assertEquals("本周", viewModel.state.value.currentPeriodLabel)
        assertEquals("下周", viewModel.state.value.nextPeriodLabel)
        assertEquals(LocalDate.of(2026, 8, 17), viewModel.state.value.statistics.rangeStart)
        assertEquals(LocalDate.of(2026, 8, 23), viewModel.state.value.statistics.rangeEnd)
        assertEquals(1, viewModel.state.value.statistics.count)
        assertEquals(1_200L, viewModel.state.value.statistics.expenseCents)

        viewModel.selectPreviousPeriod()
        advanceUntilIdle()
        awaitRange(viewModel, LocalDate.of(2026, 8, 10), testScheduler)

        assertEquals("2026年8月10日 - 2026年8月16日", viewModel.state.value.periodLabel)
        assertEquals(YearMonth.of(2026, 8), viewModel.state.value.selectedMonth)
        assertEquals(LocalDate.of(2026, 8, 10), viewModel.state.value.statistics.rangeStart)
        assertEquals(LocalDate.of(2026, 8, 16), viewModel.state.value.statistics.rangeEnd)
        assertEquals(1, viewModel.state.value.statistics.count)
        assertEquals(3_400L, viewModel.state.value.statistics.expenseCents)

        viewModel.selectNextPeriod()
        advanceUntilIdle()
        awaitRange(viewModel, LocalDate.of(2026, 8, 17), testScheduler)
        viewModel.selectNextPeriod()
        advanceUntilIdle()
        awaitRange(viewModel, LocalDate.of(2026, 8, 24), testScheduler)

        assertEquals("2026年8月24日 - 2026年8月30日", viewModel.state.value.periodLabel)
        assertEquals(LocalDate.of(2026, 8, 24), viewModel.state.value.statistics.rangeStart)
        assertEquals(LocalDate.of(2026, 8, 30), viewModel.state.value.statistics.rangeEnd)
        assertEquals(1, viewModel.state.value.statistics.count)
        assertEquals(5_600L, viewModel.state.value.statistics.expenseCents)
    }

    @Test
    fun monthAndYearPeriodsKeepCalendarNavigation() = runTest {
        val viewModel = BillViewModel(
            repository = repository,
            clock = clock,
            coroutineScope = backgroundScope
        )

        advanceUntilIdle()
        assertEquals(YearMonth.of(2026, 8), viewModel.state.value.selectedMonth)
        assertEquals("2026年8月", viewModel.state.value.periodLabel)
        assertEquals("上个月", viewModel.state.value.previousPeriodLabel)
        assertEquals("本月", viewModel.state.value.currentPeriodLabel)
        assertEquals("下个月", viewModel.state.value.nextPeriodLabel)

        viewModel.selectPreviousPeriod()
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 7), viewModel.state.value.selectedMonth)
        assertEquals("2026年7月", viewModel.state.value.periodLabel)

        viewModel.setPeriod(BillPeriod.YEAR)
        advanceUntilIdle()
        assertEquals("2026年", viewModel.state.value.periodLabel)
        assertEquals("上一年", viewModel.state.value.previousPeriodLabel)
        assertEquals("今年", viewModel.state.value.currentPeriodLabel)
        assertEquals("下一年", viewModel.state.value.nextPeriodLabel)

        viewModel.selectPreviousPeriod()
        advanceUntilIdle()

        assertEquals(YearMonth.of(2025, 7), viewModel.state.value.selectedMonth)
        assertEquals("2025年", viewModel.state.value.periodLabel)
    }

    @Test
    fun selectingAMonthRefreshesTheExistingMonthlyStatistics() = runTest {
        seedTransactions(
            transaction("七月午餐", LocalDate.of(2026, 7, 12), 1_200L),
            transaction("八月午餐", LocalDate.of(2026, 8, 12), 3_400L)
        )
        val viewModel = BillViewModel(
            repository = repository,
            clock = clock,
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )

        viewModel.selectMonth(YearMonth.of(2026, 7))
        advanceUntilIdle()
        awaitRange(viewModel, LocalDate.of(2026, 7, 1), testScheduler)

        assertEquals(YearMonth.of(2026, 7), viewModel.state.value.selectedMonth)
        assertEquals("2026年7月", viewModel.state.value.periodLabel)
        assertEquals(1, viewModel.state.value.statistics.count)
        assertEquals(1_200L, viewModel.state.value.statistics.expenseCents)
    }

    private suspend fun seedTransactions(vararg entities: TransactionEntity) {
        database.transactionDao().insertAll(entities.toList())
    }

    private suspend fun awaitRange(
        viewModel: BillViewModel,
        start: LocalDate,
        scheduler: TestCoroutineScheduler
    ) {
        repeat(100) {
            scheduler.advanceUntilIdle()
            if (viewModel.state.value.statistics.rangeStart == start) return
            Thread.sleep(20L)
        }
        error("账单统计未在测试等待窗口内完成: ${viewModel.state.value}")
    }

    private fun transaction(
        counterparty: String,
        date: LocalDate,
        amountCents: Long,
        direction: TransactionDirection = TransactionDirection.EXPENSE
    ): TransactionEntity {
        val occurredAt = date.atTime(LocalTime.NOON).atZone(zoneId).toInstant().toEpochMilli()
        return TransactionEntity(
            occurredAt = occurredAt,
            amountCents = amountCents,
            direction = direction,
            category = Category.FOOD.label,
            counterparty = counterparty,
            source = BillSource.WECHAT.name
        )
    }
}
