package com.daily.life.core.calendar

import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.database.CalendarDayOverrideEntity
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.HolidayCalendarEventEntity
import com.daily.life.core.database.HolidayCalendarSourceEntity
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HolidayCalendarRepositoryTest {
    private lateinit var database: DailyDatabase
    private lateinit var preferences: DailyPreferences
    private lateinit var preferenceFile: File

    @Before
    fun setUp() {
        database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        preferenceFile = File(System.getProperty("java.io.tmpdir"), "daily-holiday-test-${System.nanoTime()}.preferences_pb")
        preferences = DailyPreferences.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            produceFile = { preferenceFile }
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::preferenceFile.isInitialized) preferenceFile.delete()
    }

    @Test
    fun manualOverrideBeatsIcsSystemAndWeekend() {
        val date = LocalDate.of(2026, 10, 1)
        val result = CalendarDayRuleMerger.merge(
            date,
            date,
            cachedIcsDays = listOf(event("ics", date, CalendarDayKind.HOLIDAY_REST, "国庆")),
            systemDays = listOf(SystemCalendarSpecialDay(date, SystemCalendarSpecialDayKind.Holiday, null, null, "系统")),
            overrides = listOf(CalendarDayOverrideEntity(date, CalendarDayKind.REGULAR_WORKDAY, "临时上班", 1, 2))
        )
        assertEquals(CalendarDayKind.REGULAR_WORKDAY, result.single().kind)
        assertEquals(CalendarRuleSource.MANUAL, result.single().source)
    }

    @Test
    fun icsBeatsSystemCalendarAndMakeupWinsSameLayer() {
        val date = LocalDate.of(2026, 5, 2)
        val result = CalendarDayRuleMerger.merge(
            date,
            date,
            cachedIcsDays = listOf(event(HolidayCalendarRepository.BUILTIN_SOURCE_ID, date, CalendarDayKind.MAKEUP_WORKDAY, "补周六", sourceDayOfWeek = 6)),
            systemDays = listOf(SystemCalendarSpecialDay(date, SystemCalendarSpecialDayKind.Holiday, null, null, "放假")),
            overrides = emptyList()
        )
        assertEquals(CalendarDayKind.MAKEUP_WORKDAY, result.single().kind)
        assertEquals(CalendarRuleSource.BUILTIN_ICS, result.single().source)
    }

    @Test
    fun missingSourcesUseSaturdayRestDefault() {
        val date = LocalDate.of(2026, 5, 2)
        val result = CalendarDayRuleMerger.merge(date, date, emptyList(), emptyList(), emptyList())
        assertEquals(CalendarDayKind.REGULAR_REST_DAY, result.single().kind)
    }

    @Test
    fun repositoryKeepsCacheOnFailureAndManualOverride() = runBlocking {
        val source = HolidayCalendarSourceEntity("custom", "自定义", "https://example.com/a.ics", false, true)
        val eventDate = LocalDate.of(2026, 10, 1)
        database.holidayCalendarDao().insertSourceIfMissing(source)
        database.holidayCalendarDao().insertEvents(listOf(
            HolidayCalendarEventEntity("custom", "event", eventDate, eventDate, "国庆节放假", kind = CalendarDayKind.HOLIDAY_REST, fetchedAt = 1)
        ))
        val reader = SystemCalendarScheduleReader(
            zone = ZoneId.of("Asia/Shanghai"),
            canReadCalendar = { false },
            queryEvents = { _, _ -> emptyList() }
        )
        val repository = HolidayCalendarRepository(
            database.holidayCalendarDao(), preferences, reader,
            object : IcsCalendarFetcher {
                override fun fetch(source: IcsCalendarSource, etag: String?, lastModified: String?) = IcsFetchResult.Failure("offline", true)
            }
        )
        database.holidayCalendarDao().upsertDayOverrides(listOf(CalendarDayOverrideEntity(eventDate, CalendarDayKind.REGULAR_WORKDAY, "临时调课", 1, 1)))
        val result = repository.syncSource("custom")
        assertTrue(result is SyncSourceResult.UsedCache)
        val resolved = repository.resolveBetween(eventDate, eventDate).single()
        assertEquals(CalendarDayKind.REGULAR_WORKDAY, resolved.kind)
        assertEquals(1, database.holidayCalendarDao().findEventsForSource("custom").size)
    }

    @Test
    fun resolveExpandsMultiDayIcsEventAndWorksWithoutSystemPermission() = runBlocking {
        val source = HolidayCalendarSourceEntity("custom", "自定义", "https://example.com/a.ics", false, true)
        val start = LocalDate.of(2026, 10, 1)
        val end = LocalDate.of(2026, 10, 3)
        val dao = database.holidayCalendarDao()
        dao.insertSourceIfMissing(source)
        dao.insertEvents(listOf(HolidayCalendarEventEntity("custom", "multi", start, end, "国庆节放假", kind = CalendarDayKind.HOLIDAY_REST, fetchedAt = 1)))
        val repository = HolidayCalendarRepository(
            dao, preferences,
            SystemCalendarScheduleReader(ZoneId.of("Asia/Shanghai"), { false }) { _, _ -> error("must not query") },
            object : IcsCalendarFetcher { override fun fetch(source: IcsCalendarSource, etag: String?, lastModified: String?) = IcsFetchResult.NotModified }
        )
        assertEquals(listOf(start, start.plusDays(1), end), repository.resolveBetween(start, end).map { it.date })
        assertTrue(repository.resolveBetween(start.plusDays(1), start.plusDays(1)).single().kind == CalendarDayKind.HOLIDAY_REST)
    }

    @Test
    fun saveDateOverrideExpandsInclusiveRange() = runBlocking {
        val repository = HolidayCalendarRepository(
            database.holidayCalendarDao(), preferences,
            SystemCalendarScheduleReader(ZoneId.systemDefault(), { false }) { _, _ -> emptyList() },
            object : IcsCalendarFetcher { override fun fetch(source: IcsCalendarSource, etag: String?, lastModified: String?) = IcsFetchResult.NotModified }
        )
        val start = LocalDate.of(2026, 8, 1)
        repository.saveDateOverride(start, start.plusDays(2), CalendarDayKind.REGULAR_WORKDAY, "补课")
        assertEquals(3, database.holidayCalendarDao().observeDayOverridesBetween(start, start.plusDays(2)).first().size)
    }

    @Test
    fun successfulSyncOnlyReplacesCurrentSource() = runBlocking {
        val dao = database.holidayCalendarDao()
        dao.insertSourceIfMissing(HolidayCalendarSourceEntity("one", "一", "https://example.com/one.ics", false, true))
        dao.insertSourceIfMissing(HolidayCalendarSourceEntity("two", "二", "https://example.com/two.ics", false, true))
        dao.insertEvents(listOf(HolidayCalendarEventEntity("two", "old", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), "旧", kind = CalendarDayKind.HOLIDAY_REST, fetchedAt = 1)))
        val repository = HolidayCalendarRepository(
            dao, preferences,
            SystemCalendarScheduleReader(ZoneId.systemDefault(), { false }) { _, _ -> emptyList() },
            object : IcsCalendarFetcher {
                override fun fetch(source: IcsCalendarSource, etag: String?, lastModified: String?) = IcsFetchResult.Success(
                    listOf(IcsCalendarEvent("new", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2), "新", null, CalendarDayKind.HOLIDAY_REST, null, null)), null, null
                )
            }
        )
        repository.syncSource("one")
        assertEquals(1, dao.findEventsForSource("one").size)
        assertEquals(1, dao.findEventsForSource("two").size)
    }

    @Test
    fun initializeSeedsOnceAndDoesNotOverwriteExistingSource() = runBlocking {
        val dao = database.holidayCalendarDao()
        dao.insertSourceIfMissing(HolidayCalendarSourceEntity(HolidayCalendarRepository.BUILTIN_SOURCE_ID, "用户名称", "https://example.com/user.ics", true, false))
        val repository = HolidayCalendarRepository(
            dao, preferences,
            SystemCalendarScheduleReader(ZoneId.systemDefault(), { false }) { _, _ -> emptyList() },
            object : IcsCalendarFetcher { override fun fetch(source: IcsCalendarSource, etag: String?, lastModified: String?) = IcsFetchResult.NotModified }
        )
        repository.initialize()
        val stored = dao.observeSources().first().single()
        assertEquals("用户名称", stored.name)
        assertFalse(stored.enabled)
        assertNotNull(stored)
    }

    private fun event(sourceId: String, date: LocalDate, kind: CalendarDayKind, summary: String, sourceDayOfWeek: Int? = null) =
        HolidayCalendarEventEntity(sourceId, "$sourceId:$date:$summary", date, date, summary, kind = kind, sourceDayOfWeek = sourceDayOfWeek, fetchedAt = 1)
}
