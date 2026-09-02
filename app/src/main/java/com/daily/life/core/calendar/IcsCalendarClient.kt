package com.daily.life.core.calendar

import java.io.IOException
import java.net.SocketTimeoutException
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface IcsFetchResult {
    data object NotModified : IcsFetchResult

    data class Success(
        val events: List<IcsCalendarEvent>,
        val etag: String?,
        val lastModified: String?
    ) : IcsFetchResult

    data class Failure(
        val message: String,
        val retryable: Boolean
    ) : IcsFetchResult
}

interface IcsCalendarFetcher {
    fun fetch(
        source: IcsCalendarSource,
        etag: String? = null,
        lastModified: String? = null
    ): IcsFetchResult
}

class IcsCalendarClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val zone: ZoneId = ZoneId.systemDefault()
 ) : IcsCalendarFetcher {
    override fun fetch(
        source: IcsCalendarSource,
        etag: String?,
        lastModified: String?
    ): IcsFetchResult {
        val url = source.url.toHttpUrlOrNull()
            ?: return IcsFetchResult.Failure(message = "Invalid ICS URL", retryable = false)
        if (url.scheme != "https") {
            return IcsFetchResult.Failure(message = "ICS URL must use HTTPS", retryable = false)
        }

        val requestBuilder = Request.Builder().url(url)
        if (!etag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", etag)
        }
        if (!lastModified.isNullOrBlank()) {
            requestBuilder.header("If-Modified-Since", lastModified)
        }
        val request = requestBuilder.build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> IcsFetchResult.NotModified
                    !response.isSuccessful -> IcsFetchResult.Failure(
                        message = "HTTP ${response.code}",
                        retryable = response.code >= 500 || response.code == 408 || response.code == 429
                    )
                    else -> {
                        val body = response.body?.string().orEmpty()
                        val events = parseIcsCalendar(body, source, zone)
                        IcsFetchResult.Success(
                            events = events,
                            etag = response.header("ETag"),
                            lastModified = response.header("Last-Modified")
                        )
                    }
                }
            }
        } catch (error: IcsCalendarParseException) {
            IcsFetchResult.Failure(error.message ?: "ICS parse failure", retryable = false)
        } catch (error: SocketTimeoutException) {
            IcsFetchResult.Failure(error.message ?: "timeout", retryable = true)
        } catch (error: IOException) {
            IcsFetchResult.Failure(error.message ?: "network error", retryable = true)
        }
    }
}
