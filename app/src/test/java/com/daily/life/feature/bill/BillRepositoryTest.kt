package com.daily.life.feature.bill

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.datastore.DailyPreferences
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BillRepositoryTest {
    private lateinit var database: DailyDatabase
    private lateinit var preferences: DailyPreferences
    private lateinit var preferencesFile: File
    private lateinit var repository: BillRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = DailyDatabase.buildInMemory(context)
        preferencesFile = File.createTempFile("daily-bill-test", ".preferences_pb").also(File::delete)
        preferences = DailyPreferences.create(produceFile = { preferencesFile })
        repository = BillRepository(
            database = database,
            preferences = preferences,
            clock = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() {
        database.close()
        preferencesFile.delete()
    }

    @Test
    fun previewDoesNotWriteTransactionsUntilExplicitConfirmation() = runTest {
        val parsed = parseFixture()

        val preview = repository.preview(parsed)

        assertEquals(0, database.transactionDao().observeAll().first().size)
        assertTrue(preview.rows.any { it.isDuplicateCandidate })

        repository.confirmImport(preview)

        assertEquals(3, database.transactionDao().observeAll().first().size)
        assertEquals(1, database.importLogDao().observeAll().first().size)
    }

    @Test
    fun statisticsUseCentsAndFilterByDateDirectionAndSearch() = runTest {
        repository.confirmImport(repository.preview(parseFixture()))

        val statistics = repository.statistics(
            BillFilter(
                period = BillPeriod.MONTH,
                month = YearMonth.of(2026, 8),
                direction = Direction.EXPENSE,
                searchText = "星巴克"
            )
        )

        assertEquals(2, statistics.count)
        assertEquals(24_690L, statistics.expenseCents)
        assertEquals(0L, statistics.incomeCents)
        assertEquals(24_690L, statistics.categoryTotals[Category.FOOD])
    }

    @Test
    fun signedExpenseImportsContributePositiveSpendingAndBudgetProgress() = runTest {
        repository.setBudget(YearMonth.of(2026, 8), 10_000L)
        repository.confirmImport(
            repository.preview(
                parseCsv(
                    """
                    交易时间,交易对方,收/支,金额(元)
                    2026-08-05 08:00:00,早餐店,支出,-12.34
                    """.trimIndent()
                )
            )
        )

        val statistics = repository.statistics(
            BillFilter(
                period = BillPeriod.MONTH,
                month = YearMonth.of(2026, 8)
            )
        )

        assertEquals(1, statistics.count)
        assertEquals(1_234L, statistics.expenseCents)
        assertEquals(12, statistics.budgetProgressPercent)
        assertEquals(1_234L, statistics.transactions.single().amountCents)
    }

    private fun parseFixture(): BillParseResult =
        javaClass.classLoader!!.getResourceAsStream("fixtures/wechat-synthetic.csv")!!.use { input ->
            CsvBillParser().parse(input, BillSource.WECHAT)
        }

    private fun parseCsv(text: String): BillParseResult =
        ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)).use { input ->
            CsvBillParser().parse(input, BillSource.WECHAT)
        }
}
