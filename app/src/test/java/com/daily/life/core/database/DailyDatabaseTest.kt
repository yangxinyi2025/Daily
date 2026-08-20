package com.daily.life.core.database

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DailyDatabaseTest {
    private lateinit var database: DailyDatabase
    private lateinit var transactionDao: TransactionDao

    @Before
    fun setUp() {
        database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        transactionDao = database.transactionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transactionAmountIsStoredAsCents() = runTest {
        val record = TransactionEntity(
            id = 1L,
            occurredAt = 1_700_000_000_000L,
            amountCents = 12_345L,
            direction = TransactionDirection.EXPENSE,
            category = "餐饮",
            counterparty = "测试商户",
            source = "TEST"
        )

        transactionDao.insert(record)

        assertEquals(12_345L, transactionDao.findById(1L)?.amountCents)
    }

    @Test
    fun importLogAndBudgetTablesAreAvailable() = runTest {
        database.budgetDao().upsert(
            BudgetEntity(
                month = "2026-08",
                budgetCents = 300_000L,
                triggeredPercentages = setOf(50, 70),
                updatedAt = 1_700_000_100_000L
            )
        )
        database.importLogDao().insert(
            ImportLogEntity(
                batchId = "batch-1",
                fileName = "synthetic.csv",
                sourceType = "WECHAT",
                importedAt = 1_700_000_200_000L,
                totalRows = 10,
                successRows = 8,
                skippedRows = 2,
                errorSummary = "2 duplicate rows"
            )
        )

        assertNotNull(database.budgetDao().findByMonth("2026-08"))
        assertEquals(1, database.importLogDao().observeAll().first().size)
    }
}
