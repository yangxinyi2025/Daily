package com.daily.life.feature.settings

import androidx.test.core.app.ApplicationProvider
import com.daily.life.core.calendar.CalendarDayKind
import com.daily.life.core.calendar.HolidayCalendarRepository
import com.daily.life.core.calendar.IcsCalendarEvent
import com.daily.life.core.calendar.IcsCalendarFetcher
import com.daily.life.core.calendar.IcsCalendarSource
import com.daily.life.core.calendar.IcsFetchResult
import com.daily.life.core.calendar.SystemCalendarScheduleReader
import com.daily.life.core.database.DailyDatabase
import com.daily.life.core.database.HolidayCalendarSourceEntity
import com.daily.life.core.datastore.DailyPreferences
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HolidayCalendarSettingsTest {
    private lateinit var database: DailyDatabase
    private lateinit var preferences: DailyPreferences
    private lateinit var preferenceFile: File
    private lateinit var fetcher: RecordingFetcher
    private lateinit var repository: HolidayCalendarRepository

    @Before
    fun setUp() {
        database = DailyDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        preferenceFile = File(
            System.getProperty("java.io.tmpdir"),
            "daily-settings-holiday-${System.nanoTime()}.preferences_pb"
        )
        preferences = DailyPreferences.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            produceFile = { preferenceFile }
        )
        fetcher = RecordingFetcher()
        repository = HolidayCalendarRepository(
            dao = database.holidayCalendarDao(),
            preferences = preferences,
            systemCalendarReader = SystemCalendarScheduleReader(ZoneId.of("Asia/Shanghai"), { false }) { _, _ -> emptyList() },
            icsClient = fetcher,
            now = { Instant.parse("2026-08-28T08:00:00Z") }
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        if (::preferenceFile.isInitialized) preferenceFile.delete()
    }

    @Test
    fun builtInSourceCannotBeDeleted() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.first { it.holidaySources.any { source -> source.builtIn } }

        viewModel.deleteHolidaySource(HolidayCalendarRepository.BUILTIN_SOURCE_ID)
        viewModel.state.first { it.holidaySources.any { source -> source.builtIn } }

        val stored = database.holidayCalendarDao().observeSources().first()
        assertTrue(stored.any { it.id == HolidayCalendarRepository.BUILTIN_SOURCE_ID && it.builtIn })
    }

    @Test
    fun blankOrNonHttpsCustomSourcesAreRejected() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.first { it.holidaySources.any { source -> source.builtIn } }

        viewModel.addHolidaySource("", "https://example.com/china.ics")
        viewModel.state.first { it.holidayError == "请输入订阅名称" }
        assertEquals("请输入订阅名称", viewModel.state.value.holidayError)

        viewModel.addHolidaySource("自定义", "")
        viewModel.state.first { it.holidayError == "请输入 HTTPS 订阅地址" }
        assertEquals("请输入 HTTPS 订阅地址", viewModel.state.value.holidayError)

        viewModel.addHolidaySource("自定义", "http://example.com/china.ics")
        viewModel.state.first { it.holidayError == "仅支持 HTTPS 订阅地址" }
        assertEquals("仅支持 HTTPS 订阅地址", viewModel.state.value.holidayError)
        assertEquals(
            listOf(HolidayCalendarRepository.BUILTIN_SOURCE_ID),
            database.holidayCalendarDao().observeSources().first().map { it.id }
        )
    }

    @Test
    fun disabledSourcesDoNotParticipateInImmediateSync() = runTest {
        database.holidayCalendarDao().insertSourceIfMissing(
            HolidayCalendarSourceEntity(
                id = "enabled-custom",
                name = "启用",
                url = "https://example.com/enabled.ics",
                builtIn = false,
                enabled = true
            )
        )
        database.holidayCalendarDao().insertSourceIfMissing(
            HolidayCalendarSourceEntity(
                id = "disabled-custom",
                name = "禁用",
                url = "https://example.com/disabled.ics",
                builtIn = false,
                enabled = false
            )
        )
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.first { it.holidaySources.any { source -> source.id == "enabled-custom" } }

        viewModel.syncHolidaySources()
        viewModel.state.first { it.holidaySyncStatus != null && it.holidayLastSyncAt != null }

        assertTrue(fetcher.requests.contains(HolidayCalendarRepository.BUILTIN_SOURCE_ID))
        assertTrue(fetcher.requests.contains("enabled-custom"))
        assertTrue(fetcher.requests.none { it == "disabled-custom" })
    }

    @Test
    fun immediateSyncUpdatesVisibleStatusAndTimestamp() = runTest {
        val viewModel = createViewModel(backgroundScope)
        viewModel.state.first { it.holidaySources.any { source -> source.builtIn } }

        viewModel.syncHolidaySources()
        viewModel.state.first { it.holidaySyncStatus != null && it.holidayLastSyncAt != null }

        assertEquals("同步成功", viewModel.state.value.holidaySyncStatus)
        assertNotNull(viewModel.state.value.holidayLastSyncAt)
        assertEquals(1, viewModel.state.value.holidaySources.size)
    }

    private fun createViewModel(scope: CoroutineScope): SettingsViewModel = SettingsViewModel(
        semesterRepository = EmptySemesterSettingsRepository,
        preferences = preferences,
        holidayCalendarRepository = repository,
        coroutineScope = scope
    )

    private class RecordingFetcher : IcsCalendarFetcher {
        val requests = mutableListOf<String>()

        override fun fetch(
            source: IcsCalendarSource,
            etag: String?,
            lastModified: String?
        ): IcsFetchResult {
            requests += source.id
            return IcsFetchResult.Success(
                events = listOf(
                    IcsCalendarEvent(
                        eventKey = "${source.id}-event",
                        startDate = LocalDate.of(2026, 10, 1),
                        endExclusiveDate = LocalDate.of(2026, 10, 2),
                        title = "国庆节放假",
                        description = null,
                        kind = CalendarDayKind.HOLIDAY_REST,
                        sourceDayOfWeek = null,
                        sourceDate = null
                    )
                ),
                etag = null,
                lastModified = null
            )
        }
    }
}
