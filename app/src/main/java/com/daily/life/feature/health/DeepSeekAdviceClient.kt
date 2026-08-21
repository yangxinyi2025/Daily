package com.daily.life.feature.health

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

interface DeepSeekAdviceClient {
    suspend fun generateAdvice(summary: LocalReport, apiKey: String): Result<String>
}

class HttpDeepSeekAdviceClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build(),
    private val endpoint: String = "https://api.deepseek.com/chat/completions"
) : DeepSeekAdviceClient {
    override suspend fun generateAdvice(summary: LocalReport, apiKey: String): Result<String> {
        return try {
            require(apiKey.isNotBlank()) { "缺少 DeepSeek Key" }
            val prompt = "请根据以下已计算的健康摘要给出简短、非诊断性的建议：月份=${summary.month}；体重趋势=${summary.weightTrendSummary}；活动=${summary.activitySummary}；步数=${summary.totalSteps}。"
            val body = JSONObject().put("model", "deepseek-chat")
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt))).toString()
            val advice = withContext(Dispatchers.IO) {
                client.newCall(
                    Request.Builder().url(endpoint)
                        .header("Authorization", "Bearer $apiKey")
                        .header("Content-Type", "application/json")
                        .post(body.toRequestBody("application/json".toMediaType())).build()
                ).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("DeepSeek 请求失败：${response.code}")
                    JSONObject(response.body?.string().orEmpty()).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content")
                }
            }
            Result.success(advice)
        } catch (error: Throwable) {
            Result.failure(IllegalStateException(error.message?.replace(apiKey, "[REDACTED]"), error))
        }
    }
}
