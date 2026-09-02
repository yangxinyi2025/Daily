package com.daily.life.core.sync

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface WebDavClient {
    suspend fun putTemp(url: String, bytes: ByteArray): WebDavResponse
    suspend fun replace(tempUrl: String, canonicalUrl: String): WebDavResponse
    suspend fun download(url: String): ByteArray?
    suspend fun exists(url: String): Boolean
}

class OkHttpWebDavClient(
    private val username: String? = null,
    private val password: String? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
) : WebDavClient {
    private val mediaType = "application/octet-stream".toMediaType()

    override suspend fun putTemp(url: String, bytes: ByteArray): WebDavResponse = request(
        Request.Builder().url(url).put(bytes.toRequestBody(mediaType)).build()
    )

    override suspend fun replace(tempUrl: String, canonicalUrl: String): WebDavResponse = request(
        Request.Builder().url(tempUrl).method("MOVE", null)
            .header("Destination", canonicalUrl)
            .header("Overwrite", "T")
            .build()
    )

    override suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url).get().build()) { response ->
            if (response.code == 404) null else if (response.isSuccessful) response.body?.bytes()
            else throw IllegalStateException("WebDAV 下载失败：${response.code}")
        }
    }

    override suspend fun exists(url: String): Boolean = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url).head().build()) { response ->
            response.isSuccessful
        }
    }

    private suspend fun request(request: Request): WebDavResponse = withContext(Dispatchers.IO) {
        execute(request) { response -> WebDavResponse(response.code) }
    }

    private fun <T> execute(request: Request, block: (okhttp3.Response) -> T): T {
        val authenticated = if (username.isNullOrBlank()) request else request.newBuilder()
            .header("Authorization", Credentials.basic(username, password.orEmpty()))
            .build()
        return client.newCall(authenticated).execute().use(block)
    }
}
