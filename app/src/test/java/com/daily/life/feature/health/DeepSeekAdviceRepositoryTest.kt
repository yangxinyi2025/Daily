package com.daily.life.feature.health

import com.daily.life.core.security.InMemorySecretStore
import com.daily.life.core.security.SecretId
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekAdviceRepositoryTest {
    @Test
    fun missingKeyKeepsLocalReport() = runTest {
        val local = sampleReport()
        val result = DeepSeekAdviceRepository(InMemorySecretStore(), FakeClient(Result.success("unused"))).generateAdviceOrLocalSummary(local)
        assertEquals(AdviceSource.LOCAL, result.source)
        assertEquals(null, result.advice)
    }

    @Test
    fun failedNetworkKeepsLocalReport() = runTest {
        val secrets = InMemorySecretStore()
        secrets.put(SecretId.DeepSeekApiKey, "test-key")
        val result = DeepSeekAdviceRepository(secrets, FakeClient(Result.failure(IllegalStateException("offline")))).generateAdviceOrLocalSummary(sampleReport())
        assertEquals(AdviceSource.LOCAL, result.source)
    }

    @Test
    fun successfulAdviceIsOptionalOverlay() = runTest {
        val secrets = InMemorySecretStore()
        secrets.put(SecretId.DeepSeekApiKey, "test-key")
        val result = DeepSeekAdviceRepository(secrets, FakeClient(Result.success("早点休息"))).generateAdviceOrLocalSummary(sampleReport())
        assertEquals(AdviceSource.DEEPSEEK, result.source)
        assertEquals("早点休息", result.advice)
    }

    private fun sampleReport() = LocalReport(
        month = YearMonth.of(2026, 8), monthAverageJin = 130.0, monthOverMonthJin = null,
        weeklyAveragesJin = emptyMap(), recentWeights = emptyList(), totalSteps = 1000,
        walkingSteps = 1000, runningSteps = 0, totalDistanceMeters = 500.0,
        totalDurationMinutes = 10, targetDifferenceJin = null, dataState = ReportDataState.READY,
        weightTrendSummary = "稳定", activitySummary = "本月 1000 步"
    )

    private class FakeClient(private val result: Result<String>) : DeepSeekAdviceClient {
        override suspend fun generateAdvice(summary: LocalReport, apiKey: String): Result<String> = result
    }
}
