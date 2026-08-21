package com.daily.life.core.sync

import java.time.Clock
import java.time.Instant

class WebDavSyncRepository(
    private val localStore: LocalSnapshotStore,
    private val serializer: SnapshotSerializer,
    private val client: WebDavClient,
    private val canonicalUrl: String,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun inspectRemote(local: DailySnapshot = localStore.read()): SyncResult {
        val bytes = client.download(canonicalUrl) ?: return SyncResult(SyncStatus.NoRemote)
        val remote = serializer.decode(bytes)
        return when {
            remote.updatedAt.isAfter(local.updatedAt) -> SyncResult(SyncStatus.RemoteNewer, remote)
            local.updatedAt.isAfter(remote.updatedAt) -> SyncResult(SyncStatus.LocalNewer, remote)
            serializer.encode(remote).contentEquals(serializer.encode(local)) -> SyncResult(SyncStatus.Synced, remote)
            else -> SyncResult(SyncStatus.Conflict, remote, "本地与远端更新时间相同但内容不同")
        }
    }

    suspend fun upload(local: DailySnapshot = localStore.read(), force: Boolean = false): SyncResult {
        val remote = inspectRemote(local)
        if (!force && remote.status == SyncStatus.RemoteNewer) return remote
        if (!force && remote.status == SyncStatus.Conflict) return remote
        val stamp = clock.instant().toEpochMilli()
        val tempUrl = "$canonicalUrl.tmp-$stamp"
        val put = client.putTemp(tempUrl, serializer.encode(local))
        if (!put.isSuccessful) return SyncResult(SyncStatus.Failed, errorMessage = "WebDAV 临时上传失败：${put.code}")
        val replaced = client.replace(tempUrl, canonicalUrl)
        if (!replaced.isSuccessful) return SyncResult(SyncStatus.Failed, errorMessage = "WebDAV 原子替换失败：${replaced.code}")
        return SyncResult(SyncStatus.Synced, local)
    }

    suspend fun restoreRemote(replaceExisting: Boolean): SyncResult {
        if (!replaceExisting) return SyncResult(SyncStatus.ReadyToRestore, errorMessage = "恢复远端备份需要明确确认")
        val bytes = client.download(canonicalUrl) ?: return SyncResult(SyncStatus.NoRemote)
        val remote = serializer.decode(bytes)
        val local = localStore.read()
        val safety = local.copy(createdAt = Instant.now(clock), updatedAt = Instant.now(clock))
        localStore.replace(safety)
        localStore.replace(remote)
        return SyncResult(SyncStatus.ReadyToRestore, remote)
    }
}
