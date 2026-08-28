package com.daily.life.core.calendar

import com.daily.life.core.database.CalendarDayOverrideEntity
import com.daily.life.core.database.HolidayCalendarEventEntity
import com.daily.life.core.database.HolidayCalendarSourceEntity
import com.daily.life.core.database.HolidayCalendarDao
import com.daily.life.core.datastore.DailyPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object CalendarDayRuleMerger {
    fun merge(
        startDate: LocalDate,
        endDate: LocalDate,
        cachedIcsDays: Iterable<HolidayCalendarEventEntity>,
        systemDays: Iterable<SystemCalendarSpecialDay>,
        overrides: Iterable<CalendarDayOverrideEntity>,
        sourceKinds: Map<String, CalendarRuleSource> = emptyMap(),
        updatedAt: Long = System.currentTimeMillis()
    ): List<CalendarDayRule> {
        if (endDate.isBefore(startDate)) return emptyList()
        val systemByDate = systemDays.groupBy { it.date }
        val icsByDate = cachedIcsDays.groupBy { it.startDate }
        val manualByDate = overrides.associateBy { it.date }
        return generateSequence(startDate) { it.plusDays(1).takeIf { next -> !next.isAfter(endDate) } }
            .map { date ->
                val default = CalendarDayRule(
                    date = date,
                    kind = if (date.dayOfWeek.value in 6..7) {
                        CalendarDayKind.REGULAR_REST_DAY
                    } else {
                        CalendarDayKind.REGULAR_WORKDAY
                    },
                    source = CalendarRuleSource.WEEKEND_DEFAULT,
                    updatedAt = updatedAt
                )
                val system = systemByDate[date].orEmpty().map { day ->
                    CalendarDayRule(
                        date = date,
                        kind = when (day.kind) {
                            SystemCalendarSpecialDayKind.Holiday -> CalendarDayKind.HOLIDAY_REST
                            SystemCalendarSpecialDayKind.MakeupWorkday -> CalendarDayKind.MAKEUP_WORKDAY
                        },
                        source = CalendarRuleSource.SYSTEM_CALENDAR,
                        label = day.label,
                        sourceDayOfWeek = day.sourceDayOfWeek,
                        sourceDate = day.sourceDate,
                        updatedAt = updatedAt
                    )
                }
                val ics = icsByDate[date].orEmpty().map { day ->
                    CalendarDayRule(
                        date = date,
                        kind = day.kind,
                        source = sourceKinds[day.sourceId]
                            ?: if (day.sourceId == HolidayCalendarRepository.BUILTIN_SOURCE_ID) {
                                CalendarRuleSource.BUILTIN_ICS
                            } else {
                                CalendarRuleSource.CUSTOM_ICS
                            },
                        sourceId = day.sourceId,
                        label = day.summary,
                        sourceDayOfWeek = day.sourceDayOfWeek,
                        sourceDate = day.sourceDate,
                        updatedAt = updatedAt
                    )
                }
                val manual = manualByDate[date]?.let { override ->
                    CalendarDayRule(
                        date = date,
                        kind = override.targetKind,
                        source = CalendarRuleSource.MANUAL,
                        label = override.note,
                        updatedAt = override.updatedAt
                    )
                }
                manual ?: chooseLayer(ics, system) ?: default
            }
            .toList()
    }

    private fun chooseLayer(
        primary: List<CalendarDayRule>,
        secondary: List<CalendarDayRule>
    ): CalendarDayRule? {
        val candidates = primary.ifEmpty { secondary }
        if (candidates.isEmpty()) return null
        val selectedKind = if (candidates.any { it.kind == CalendarDayKind.MAKEUP_WORKDAY }) {
            CalendarDayKind.MAKEUP_WORKDAY
        } else {
            candidates.first().kind
        }
        val selected = candidates.filter { it.kind == selectedKind }
        return selected.first().copy(
            label = candidates.mapNotNull { it.label?.takeIf(String::isNotBlank) }
                .distinct()
                .joinToString(" / ")
                .ifBlank { null },
            sourceId = candidates.mapNotNull { it.sourceId }.distinct().joinToString(",").ifBlank { null }
        )
    }
}

sealed interface SyncSourceResult {
    val sourceId: String
    data class Success(override val sourceId: String, val eventCount: Int) : SyncSourceResult
    data class UsedCache(override val sourceId: String, val message: String) : SyncSourceResult
    data class Failed(override val sourceId: String, val message: String, val retryable: Boolean) : SyncSourceResult
}

data class SyncSummary(
    val succeeded: Int,
    val failed: Int,
    val usedCache: Int,
    val message: String,
    val retryableFailure: Boolean = false
)

class HolidayCalendarRepository(
    private val dao: HolidayCalendarDao,
    private val preferences: DailyPreferences,
    private val systemCalendarReader: SystemCalendarScheduleReader,
    private val icsClient: IcsCalendarFetcher,
    private val now: () -> Instant = { Instant.now() }
) {
    private val syncMutex = Mutex()

    fun observeSources(): Flow<List<HolidayCalendarSourceEntity>> = dao.observeSources()

    suspend fun observeSourcesSnapshot(): List<HolidayCalendarSourceEntity> = dao.observeSources().first()

    suspend fun initialize() {
        if (dao.observeSources().first().none { it.id == BUILTIN_SOURCE_ID }) {
            dao.insertSourceIfMissing(
                HolidayCalendarSourceEntity(
                    id = BUILTIN_SOURCE_ID,
                    name = "中国节假日（推荐）",
                    url = BUILTIN_ICS_URL,
                    builtIn = true,
                    enabled = true
                )
            )
        }
    }

    suspend fun resolveBetween(startDate: LocalDate, endDate: LocalDate): List<CalendarDayRule> {
        if (endDate.isBefore(startDate)) return emptyList()
        val sources = dao.observeSources().first()
        val sourceById = sources.associateBy { it.id }
        val cachedDays = dao.findEventsBetween(startDate, endDate).flatMap { event ->
            val source = sourceById[event.sourceId] ?: return@flatMap emptyList()
            if (!source.enabled) return@flatMap emptyList()
            val firstDate = maxOf(startDate, event.startDate)
            val lastDate = minOf(endDate, event.endDateInclusive)
            if (lastDate.isBefore(firstDate)) return@flatMap emptyList()
            generateSequence(firstDate) { it.plusDays(1).takeIf { next -> !next.isAfter(lastDate) } }
                .map { date -> event.copy(startDate = date, endDateInclusive = date) }
                .toList()
        }
        val systemDays = runCatching { systemCalendarReader.readBetween(startDate, endDate) }.getOrDefault(emptyList())
        val overrides = dao.observeDayOverridesBetween(startDate, endDate).first()
        val sourceKinds = sourceById.mapValues { (_, source) ->
            if (source.builtIn) CalendarRuleSource.BUILTIN_ICS else CalendarRuleSource.CUSTOM_ICS
        }
        return CalendarDayRuleMerger.merge(startDate, endDate, cachedDays, systemDays, overrides, sourceKinds, now().toEpochMilli())
    }

    suspend fun syncSource(sourceId: String): SyncSourceResult {
        return syncMutex.withLock {
            initialize()
            val result = syncSourceInternal(sourceId)
            persistSyncSummary(listOf(result))
            result
        }
    }

    private suspend fun syncSourceInternal(sourceId: String): SyncSourceResult {
        val source = dao.observeSources().first().firstOrNull { it.id == sourceId }
            ?: return SyncSourceResult.Failed(sourceId, "日历来源不存在", retryable = false)
        val result = withContext(Dispatchers.IO) {
            icsClient.fetch(
                IcsCalendarSource(source.id, source.name, source.url, source.builtIn),
                source.etag,
                source.lastModified
            )
        }
        val syncAt = now()
        return when (result) {
            is IcsFetchResult.Success -> {
                val rows = result.events.map { event ->
                    HolidayCalendarEventEntity(
                        sourceId = source.id,
                        eventKey = event.eventKey,
                        startDate = event.startDate,
                        endDateInclusive = event.endExclusiveDate.minusDays(1),
                        summary = event.title,
                        description = event.description,
                        kind = event.kind,
                        sourceDayOfWeek = event.sourceDayOfWeek,
                        sourceDate = event.sourceDate,
                        fetchedAt = syncAt.toEpochMilli()
                    )
                }
                dao.replaceEventsForSource(source.id, rows)
                dao.upsertSource(source.copy(lastSuccessfulSyncAt = syncAt.toEpochMilli(), etag = result.etag, lastModified = result.lastModified, lastError = null))
                SyncSourceResult.Success(source.id, rows.size)
            }
            IcsFetchResult.NotModified -> {
                dao.upsertSource(source.copy(lastSuccessfulSyncAt = syncAt.toEpochMilli(), lastError = null))
                SyncSourceResult.Success(source.id, dao.findEventsForSource(source.id).size)
            }
            is IcsFetchResult.Failure -> {
                dao.upsertSource(source.copy(lastError = result.message))
                if (dao.findEventsForSource(source.id).isNotEmpty()) {
                    SyncSourceResult.UsedCache(source.id, result.message)
                } else {
                    SyncSourceResult.Failed(source.id, result.message, result.retryable)
                }
            }
        }
    }

    suspend fun syncAllEnabledSources(): SyncSummary {
        return syncMutex.withLock {
            initialize()
            syncSources(dao.observeSources().first().filter { it.enabled }.map { it.id })
        }
    }

    suspend fun addCustomSource(name: String, url: String): HolidayCalendarSourceEntity {
        initialize()
        val normalizedName = name.trim()
        val normalizedUrl = url.trim()
        require(normalizedName.isNotEmpty()) { "请输入订阅名称" }
        require(normalizedUrl.isNotEmpty()) { "请输入 HTTPS 订阅地址" }
        require(normalizedUrl.startsWith("https://", ignoreCase = true)) { "仅支持 HTTPS 订阅地址" }
        val existing = dao.observeSources().first()
        require(existing.none { it.url.equals(normalizedUrl, ignoreCase = true) }) { "该订阅地址已存在" }
        val entity = HolidayCalendarSourceEntity(
            id = "custom-${UUID.randomUUID()}",
            name = normalizedName,
            url = normalizedUrl,
            builtIn = false,
            enabled = true
        )
        dao.upsertSource(entity)
        return entity
    }

    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        initialize()
        val source = dao.observeSources().first().firstOrNull { it.id == id } ?: return
        dao.upsertSource(source.copy(enabled = enabled))
    }

    suspend fun deleteSource(id: String) {
        initialize()
        val source = dao.observeSources().first().firstOrNull { it.id == id } ?: return
        if (source.builtIn) return
        dao.deleteCustomSource(id)
    }

    suspend fun saveDateOverride(startDate: LocalDate, endDate: LocalDate, kind: CalendarDayKind, note: String?) {
        require(!endDate.isBefore(startDate)) { "结束日期不能早于开始日期" }
        val timestamp = now().toEpochMilli()
        dao.upsertDayOverrides(generateSequence(startDate) { it.plusDays(1).takeIf { next -> !next.isAfter(endDate) } }
            .map { CalendarDayOverrideEntity(it, kind, note, timestamp, timestamp) }
            .toList())
    }

    suspend fun clearDateOverrides(dates: List<LocalDate>) {
        if (dates.isNotEmpty()) dao.deleteDayOverrides(dates)
    }

    suspend fun syncIfStale() {
        syncMutex.withLock {
            initialize()
            val sources = dao.observeSources().first()
            val reference = now()
            val staleSourceIds = sources.filter { source ->
                source.enabled && (
                    source.lastSuccessfulSyncAt == null ||
                        Duration.between(Instant.ofEpochMilli(source.lastSuccessfulSyncAt), reference).toHours() >= 24
                    )
            }.map { it.id }
            if (staleSourceIds.isNotEmpty()) {
                syncSources(staleSourceIds)
            }
        }
    }

    private suspend fun syncSources(sourceIds: List<String>): SyncSummary {
        val results = sourceIds.map { sourceId -> syncSourceInternal(sourceId) }
        val summary = summarize(results)
        persistSyncSummary(results)
        return summary
    }

    private suspend fun persistSyncSummary(results: List<SyncSourceResult>) {
        if (results.isEmpty()) return
        val summary = summarize(results)
        when {
            summary.failed > 0 -> preferences.setHolidaySyncStatus("同步失败")
            summary.usedCache > 0 -> preferences.setHolidaySyncStatus("同步失败，继续使用缓存")
            else -> {
                preferences.setHolidaySyncStatus("同步成功")
                preferences.setHolidayLastSyncAt(now())
            }
        }
        val errors = results.mapNotNull { result ->
            when (result) {
                is SyncSourceResult.UsedCache -> "${result.sourceId}: ${result.message}"
                is SyncSourceResult.Failed -> "${result.sourceId}: ${result.message}"
                is SyncSourceResult.Success -> null
            }
        }
        preferences.setHolidaySyncError(errors.joinToString("；").ifBlank { null })
    }

    private fun summarize(results: List<SyncSourceResult>): SyncSummary = SyncSummary(
        succeeded = results.count { it is SyncSourceResult.Success },
        failed = results.count { it is SyncSourceResult.Failed },
        usedCache = results.count { it is SyncSourceResult.UsedCache },
        message = results.joinToString("；") { result ->
            when (result) {
                is SyncSourceResult.Success -> "${result.sourceId}: ${result.eventCount} 条"
                is SyncSourceResult.UsedCache -> "${result.sourceId}: 使用缓存（${result.message}）"
                is SyncSourceResult.Failed -> "${result.sourceId}: ${result.message}"
            }
        },
        retryableFailure = results.any { it is SyncSourceResult.Failed && it.retryable }
    )

    companion object {
        const val BUILTIN_SOURCE_ID = "builtin-china-public-holidays"
        // Provider URL must be vetted before release; keeping it in one place makes replacement safe.
        const val BUILTIN_ICS_URL = "https://www.officeholidays.com/ics/ics_china.php"
    }
}
