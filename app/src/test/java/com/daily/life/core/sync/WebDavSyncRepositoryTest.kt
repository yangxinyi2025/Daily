package com.daily.life.core.sync

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavSyncRepositoryTest {
    @Test
    fun newerRemoteIsNeverOverwritten() = runTest {
        val local = DailySnapshot.empty("device", Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-20T00:00:00Z"))
        val remote = local.copy(updatedAt = Instant.parse("2026-08-21T00:00:00Z"))
        val client = FakeWebDavClient(SnapshotSerializer().encode(remote))
        val repository = WebDavSyncRepository(FakeStore(local), SnapshotSerializer(), client, "https://dav.example/daily")

        val result = repository.upload(local)

        assertEquals(SyncStatus.RemoteNewer, result.status)
        assertFalse(client.putCalled)
    }

    @Test
    fun uploadUsesTemporaryPathBeforeAtomicReplace() = runTest {
        val local = DailySnapshot.empty("device", Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-20T00:00:00Z"))
        val client = FakeWebDavClient(null)
        val repository = WebDavSyncRepository(FakeStore(local), SnapshotSerializer(), client, "https://dav.example/daily")

        assertEquals(SyncStatus.Synced, repository.upload(local).status)
        assertTrue(client.putCalled)
        assertTrue(client.replaceCalled)
        assertTrue(client.events == listOf("put", "replace"))
    }

    private class FakeWebDavClient(private val remote: ByteArray?) : WebDavClient {
        val events = mutableListOf<String>()
        var putCalled = false
        var replaceCalled = false
        override suspend fun putTemp(url: String, bytes: ByteArray): WebDavResponse { events += "put"; putCalled = true; return WebDavResponse(201) }
        override suspend fun replace(tempUrl: String, canonicalUrl: String): WebDavResponse { events += "replace"; replaceCalled = true; return WebDavResponse(201) }
        override suspend fun download(url: String): ByteArray? = remote
        override suspend fun exists(url: String): Boolean = remote != null
    }

    private class FakeStore(private var value: DailySnapshot) : LocalSnapshotStore {
        override suspend fun read(): DailySnapshot = value
        override suspend fun replace(snapshot: DailySnapshot) { value = snapshot }
    }
}
