package com.daily.life.core.sync

import com.daily.life.core.database.ActivityRecordEntity
import com.daily.life.core.database.CourseEntity
import com.daily.life.core.database.CourseWeekEntity
import com.daily.life.core.database.ImportLogEntity
import com.daily.life.core.database.MonthlyReportEntity
import com.daily.life.core.database.ScheduleEventEntity
import com.daily.life.core.database.SemesterEntity
import com.daily.life.core.database.WeightRecordEntity
import java.io.File
import java.time.Instant
import java.time.LocalDate

data class SnapshotSettings(
    val semesterStartDate: LocalDate?,
    val currentSemesterId: Long?,
    val targetWeightJin: Double?,
    val webDavEndpoint: String?,
    val autoSyncEnabled: Boolean
)

data class DailySnapshot(
    val schemaVersion: Int,
    val deviceId: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val settings: SnapshotSettings,
    val semesters: List<SemesterEntity>,
    val courses: List<CourseEntity>,
    val courseWeeks: List<CourseWeekEntity>,
    val scheduleEvents: List<ScheduleEventEntity>,
    val weights: List<WeightRecordEntity>,
    val activities: List<ActivityRecordEntity>,
    val monthlyReports: List<MonthlyReportEntity>,
    val importLogs: List<ImportLogEntity>
) {
    companion object {
        fun empty(deviceId: String, createdAt: Instant, updatedAt: Instant): DailySnapshot = DailySnapshot(
            schemaVersion = SnapshotSerializer.CURRENT_SCHEMA_VERSION,
            deviceId = deviceId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            settings = SnapshotSettings(null, null, null, null, false),
            semesters = emptyList(),
            courses = emptyList(),
            courseWeeks = emptyList(),
            scheduleEvents = emptyList(),
            weights = emptyList(),
            activities = emptyList(),
            monthlyReports = emptyList(),
            importLogs = emptyList()
        )
    }
}

sealed class SnapshotValidationException(message: String) : IllegalArgumentException(message) {
    class UnsupportedSchemaVersion(val actual: Int) :
        SnapshotValidationException("不支持的备份版本：$actual")

    class DeviceMismatch(val expected: String, val actual: String) :
        SnapshotValidationException("备份设备不匹配：需要 $expected，实际为 $actual")

    class MalformedSnapshot(detail: String, cause: Throwable? = null) :
        SnapshotValidationException("备份文件无效：$detail") {
        init {
            if (cause != null) initCause(cause)
        }
    }
}

class RestoreConfirmationRequired : IllegalStateException("恢复备份前必须明确确认覆盖本地数据")

data class RestoreResult(val file: File, val safetyCopy: File?)

interface LocalSnapshotStore {
    suspend fun read(): DailySnapshot
    suspend fun replace(snapshot: DailySnapshot)
}

interface SnapshotRepository {
    suspend fun createSnapshot(): File
    suspend fun restoreSnapshot(file: File, replaceExisting: Boolean): RestoreResult
}

enum class SyncStatus {
    Disabled,
    NoRemote,
    RemoteNewer,
    LocalNewer,
    Conflict,
    ReadyToRestore,
    Synced,
    Failed
}

data class SyncResult(
    val status: SyncStatus,
    val remoteSnapshot: DailySnapshot? = null,
    val errorMessage: String? = null
)

data class WebDavResponse(val code: Int) {
    val isSuccessful: Boolean
        get() = code in 200..299
}

interface SyncStateStore {
    suspend fun isAutoSyncEnabled(): Boolean
    suspend fun lastSyncAt(): Instant?
    suspend fun setLastSyncAt(value: Instant)
    suspend fun setStatus(status: String)
}
