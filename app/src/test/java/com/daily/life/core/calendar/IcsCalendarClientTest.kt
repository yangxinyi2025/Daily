package com.daily.life.core.calendar

import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.ZoneId
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsCalendarClientTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun fetchReturnsParsedEventsAndResponseMetadata() {
        val recorded = mutableListOf<String>()
        val client = IcsCalendarClient(
            okHttpClient = okHttpClient { chain ->
                recorded += chain.request().url.toString()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("ETag", "\"v2\"")
                    .header("Last-Modified", "Fri, 28 Aug 2026 10:00:00 GMT")
                    .body(
                        """
                        BEGIN:VCALENDAR
                        BEGIN:VEVENT
                        UID:holiday
                        DTSTART;VALUE=DATE:20261001
                        DTEND;VALUE=DATE:20261008
                        SUMMARY:国庆节放假
                        END:VEVENT
                        END:VCALENDAR
                        """.trimIndent().toResponseBody()
                    )
                    .build()
            },
            zone = zone
        )

        val result = client.fetch(
            source = IcsCalendarSource(
                id = "builtin",
                name = "Built-in",
                url = "https://example.com/china.ics",
                builtIn = true
            ),
            etag = "\"v1\"",
            lastModified = "Thu, 27 Aug 2026 10:00:00 GMT"
        )

        assertEquals(listOf("https://example.com/china.ics"), recorded)
        val success = result as IcsFetchResult.Success
        assertEquals("\"v2\"", success.etag)
        assertEquals("Fri, 28 Aug 2026 10:00:00 GMT", success.lastModified)
        assertEquals(LocalDate.of(2026, 10, 1), success.events.single().startDate)
    }

    @Test
    fun fetchSendsConditionalHeadersAndHandlesNotModified() {
        var ifNoneMatch: String? = null
        var ifModifiedSince: String? = null
        val client = IcsCalendarClient(
            okHttpClient = okHttpClient { chain ->
                ifNoneMatch = chain.request().header("If-None-Match")
                ifModifiedSince = chain.request().header("If-Modified-Since")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(304)
                    .message("Not Modified")
                    .body(ByteArray(0).toResponseBody())
                    .build()
            },
            zone = zone
        )

        val result = client.fetch(
            source = IcsCalendarSource(
                id = "builtin",
                name = "Built-in",
                url = "https://example.com/china.ics",
                builtIn = true
            ),
            etag = "\"cached\"",
            lastModified = "Fri, 28 Aug 2026 10:00:00 GMT"
        )

        assertEquals("\"cached\"", ifNoneMatch)
        assertEquals("Fri, 28 Aug 2026 10:00:00 GMT", ifModifiedSince)
        assertTrue(result is IcsFetchResult.NotModified)
    }

    @Test
    fun fetchTurnsTimeoutIntoRetryableFailure() {
        val client = IcsCalendarClient(
            okHttpClient = okHttpClient {
                throw SocketTimeoutException("timeout")
            },
            zone = zone
        )

        val result = client.fetch(
            source = IcsCalendarSource(
                id = "builtin",
                name = "Built-in",
                url = "https://example.com/china.ics",
                builtIn = true
            )
        )

        val failure = result as IcsFetchResult.Failure
        assertTrue(failure.retryable)
        assertTrue(failure.message.contains("timeout"))
    }

    @Test
    fun fetchRejectsNonHttpsUrlsBeforeNetworkAccess() {
        var called = false
        val client = IcsCalendarClient(
            okHttpClient = okHttpClient {
                called = true
                error("should not execute")
            },
            zone = zone
        )

        val result = client.fetch(
            source = IcsCalendarSource(
                id = "custom",
                name = "Custom",
                url = "http://example.com/china.ics",
                builtIn = false
            )
        )

        assertFalse(called)
        val failure = result as IcsFetchResult.Failure
        assertFalse(failure.retryable)
        assertTrue(failure.message.contains("HTTPS"))
    }

    private fun okHttpClient(handler: (Interceptor.Chain) -> Response): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(handler)
            .build()
}
