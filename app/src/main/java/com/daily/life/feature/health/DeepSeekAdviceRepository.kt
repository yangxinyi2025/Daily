package com.daily.life.feature.health

import com.daily.life.core.security.SecretId
import com.daily.life.core.security.SecretStore

enum class AdviceSource { LOCAL, DEEPSEEK }

data class AdviceReport(val local: LocalReport, val advice: String? = null, val source: AdviceSource = AdviceSource.LOCAL)

class DeepSeekAdviceRepository(
    private val secretStore: SecretStore,
    private val client: DeepSeekAdviceClient
) {
    suspend fun generateAdviceOrLocalSummary(summary: LocalReport): AdviceReport {
        val key = secretStore.read(SecretId.DeepSeekApiKey)?.takeIf { it.isNotBlank() }
            ?: return AdviceReport(summary)
        val result = client.generateAdvice(summary, key)
        return result.fold(
            onSuccess = { AdviceReport(summary, it, AdviceSource.DEEPSEEK) },
            onFailure = { AdviceReport(summary) }
        )
    }
}
