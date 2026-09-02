# Daily 中国节假日、调休与课表校准 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ] syntax) for tracking.

**Goal:** 为 Daily 建立 ICS、手机系统日历、普通周末规则和用户手动修正共同驱动的实际日期规则，并让日历、课表和课程消息提醒使用同一套结果。

**Architecture:** 在现有系统日历特殊日期解析、学期调休校准表和课表日期映射基础上，增加规范化日期规则模型。ICS 源配置和成功缓存保存到 Room；系统日历按范围读取；合并器按“手动修正 > ICS > 系统日历 > 普通周末”生成确定结果。课表额外保存实际日期到课程来源星期的学期校准，以及单日/日期范围有课或无课覆盖，提醒同步复用同一纯逻辑映射器。

**Tech Stack:** Kotlin、Jetpack Compose、Room 2.7、DataStore Preferences、OkHttp 4.12、WorkManager 2.10、CalendarContract.Instances、java.time、JUnit、Robolectric、现有 Android Gradle 工程。

**Spec:** docs/superpowers/specs/2026-08-28-daily-chinese-holiday-calendar-design.md

## Global Constraints

- 手动日期状态 > ICS 订阅 > 系统日历 > 普通周末规则。
- 同一优先级内同类状态合并来源；休息和补班冲突时 MAKEUP_WORKDAY 优先。
- 每天后台同步 ICS，Daily 启动/回到前台时检查，并提供“立即同步”。
- 网络或单个来源失败时保留最近一次成功缓存，不清空其他有效来源。
- 没有系统日历读取权限时继续使用 ICS、本地缓存和手动修正。
- 调休来源没有“补哪一天”时不阻塞 PDF 导入、不自动复制课程，并显示待校准。
- 课表的 HAS_CLASS/NO_CLASS 覆盖自动结果；调休来源星期单独保存。
- 课表 UI 与课程消息提醒必须调用同一个实际日期映射函数。
- 不修改 PDF 解析出的学期周次、星期和节次原始规则。
- 不把普通系统日历事件、Daily 自己创建的课程事件或生日事件识别为节假日。
- 不把某一年节假日表硬编码为唯一数据来源；内置源必须是可替换的 ICS 配置。

---

## 当前代码边界

当前分支已经包含部分上一轮系统日历和课表调休实现，计划必须在其上演进：

- core/calendar/SystemCalendarScheduleModels.kt、SystemCalendarScheduleParser.kt、SystemCalendarScheduleReader.kt 已有系统日历输入、关键词解析和 CalendarContract.Instances 查询。
- feature/timetable/TimetableCalendarMapping.kt 已有普通日、节假日和确认调休日映射。
- core/database 已有 SemesterCalendarAdjustmentEntity、DAO 和版本 8 迁移。
- feature/timetable/TimetableViewModel.kt、TimetableImportScreen.kt 已有调休校准入口。
- feature/schedule/ScheduleMonthScreen.kt 已有系统日历月历标记。
- app/build.gradle.kts 已有 OkHttp 和 WorkManager，不新增网络或后台任务依赖。

此前运行的基线是 150 个单元测试中 9 个失败，涉及系统日历解析的跨天边界、日历标记、调休日提醒日期、课表 ViewModel 初始化和 Room 测试初始化。Task 1 先固定这些契约，再扩展 ICS 和手动修正。

## 文件与职责映射

- core/calendar/CalendarDayRuleModels.kt：规范化日期状态、来源和覆盖模型。
- core/calendar/IcsCalendarParser.kt：纯文本 ICS 解析。
- core/calendar/IcsCalendarClient.kt：OkHttp 请求、条件请求和结果封装。
- core/calendar/HolidayCalendarRepository.kt：来源缓存读取、规则合并、手动覆盖和范围查询。
- core/calendar/HolidayCalendarSyncWorker.kt：每日 ICS 同步。
- core/database/DailyEntities.kt、DailyDaos.kt、DailyDatabase.kt：源配置、事件缓存、日期覆盖和学期课程覆盖持久化。
- core/datastore/DailyPreferences.kt：同步显示状态和最后检查时间等轻量偏好。
- core/AppContainer.kt、DailyApplication.kt、core/navigation/DailyNavHost.kt：单例依赖和启动检查。
- feature/settings/SettingsState.kt、SettingsViewModel.kt、SettingsScreen.kt：ICS 源配置、同步状态和立即同步。
- feature/schedule/ScheduleModels.kt、ScheduleViewModel.kt、ScheduleMonthScreen.kt、ScheduleScreen.kt：日历最终状态、来源详情和日期状态修正。
- feature/timetable/TimetableModels.kt、TimetableViewModel.kt、TimetableImportScreen.kt、TimetableScreen.kt、TimetableCalendarMapping.kt：导入校准、课表展示和待校准状态。
- feature/timetable/TimetableRepository.kt、core/calendar/CalendarReminderSyncer.kt：学期校准持久化和课程消息实际日期同步。

---

### Task 1: 固定现有系统日历解析与课表映射契约

**Files:**
- Modify: app/src/main/java/com/daily/life/core/calendar/SystemCalendarScheduleParser.kt
- Modify: app/src/main/java/com/daily/life/core/calendar/SystemCalendarScheduleReader.kt
- Modify: app/src/main/java/com/daily/life/feature/schedule/ScheduleCalendarPresentation.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableCalendarMapping.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt
- Modify: app/src/test/java/com/daily/life/core/calendar/SystemCalendarScheduleParserTest.kt
- Modify: app/src/test/java/com/daily/life/core/calendar/SystemCalendarScheduleReaderTest.kt
- Modify: app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarPresentationTest.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableCalendarMappingTest.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableViewModelTest.kt

**Interfaces:**
- Preserve parseSystemCalendarSpecialDay(event, zone): List<SystemCalendarSpecialDay>.
- Preserve SystemCalendarScheduleReader.readBetween(startDate, endDate).
- Preserve mapWeekToScheduleSlots(semesterStartDate, selectedWeek, specialDays, confirmedAdjustments).
- Preserve courseOccurrenceDates(semesterStartDate, week, courseDayOfWeek, specialDays, confirmedAdjustments).

- [ ] Step 1: Reproduce the current focused failures.

Run:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.calendar.SystemCalendarScheduleParserTest --tests com.daily.life.core.calendar.SystemCalendarScheduleReaderTest --tests com.daily.life.feature.schedule.ScheduleCalendarPresentationTest --tests com.daily.life.feature.timetable.TimetableCalendarMappingTest --tests com.daily.life.feature.timetable.TimetableViewModelTest --console=plain
~~~

Record the exact failing assertions before editing. All-day end dates are exclusive; a holiday must not create a normal occurrence; current week must use the injected Clock.

- [ ] Step 2: Add the approved unknown-makeup tests.

~~~kotlin
@Test fun unknownMakeupIsVisibleButDoesNotBlockImport() {
    val state = importStateWithMakeup(actualDate = LocalDate.of(2026, 10, 10), sourceDayOfWeek = null)
    assertTrue(state.calendarAdjustmentChoices.single().isRequired)
    assertTrue(state.canConfirm)
}

@Test fun confirmedMakeupAddsTheActualDateForTheSourceWeekday() {
    val actual = LocalDate.of(2026, 9, 5)
    assertEquals(
        listOf(LocalDate.of(2026, 9, 4), actual),
        courseOccurrenceDates(
            LocalDate.of(2026, 8, 31), 1, 5,
            listOf(makeupDay(actual)), mapOf(actual to 5)
        )
    )
}
~~~

The first test means unresolved source information is visible and pending but does not block PDF import; the mapper still produces no copied course until a source weekday or explicit class override exists.

- [ ] Step 3: Implement the minimal corrections.

Use date >= startDate and date < endDate for all-day ranges. Keep MAKEUP_WORKDAY over HOLIDAY_REST. Calculate the current week from WeekCalculator.currentWeek(semester.startDate, clock.instant().atZone(clock.zone).toLocalDate()). Keep import canConfirm independent of unresolved calendar adjustments.

- [ ] Step 4: Run the focused and full unit tests.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.calendar.SystemCalendarScheduleParserTest --tests com.daily.life.core.calendar.SystemCalendarScheduleReaderTest --tests com.daily.life.feature.schedule.ScheduleCalendarPresentationTest --tests com.daily.life.feature.timetable.TimetableCalendarMappingTest --tests com.daily.life.feature.timetable.TimetableViewModelTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
~~~

Both commands must finish with zero failures before this task is committed.

- [ ] Step 5: Commit.

~~~powershell
git add app/src/main/java/com/daily/life/core/calendar app/src/main/java/com/daily/life/feature/schedule app/src/main/java/com/daily/life/feature/timetable app/src/test/java/com/daily/life/core/calendar app/src/test/java/com/daily/life/feature/schedule app/src/test/java/com/daily/life/feature/timetable
git commit -m "fix: stabilize holiday and timetable date mapping"
~~~

### Task 2: Add normalized date-rule models and Room persistence

**Files:**
- Create: app/src/main/java/com/daily/life/core/calendar/CalendarDayRuleModels.kt
- Modify: app/src/main/java/com/daily/life/core/database/DailyEntities.kt
- Modify: app/src/main/java/com/daily/life/core/database/DailyDaos.kt
- Modify: app/src/main/java/com/daily/life/core/database/DailyDatabase.kt
- Modify: app/src/main/java/com/daily/life/core/datastore/DailyPreferences.kt
- Create: app/src/test/java/com/daily/life/core/database/HolidayCalendarDaoTest.kt
- Create: app/src/test/java/com/daily/life/core/database/HolidayCalendarMigrationTest.kt

**Interfaces:**
- Define CalendarDayKind with REGULAR_WORKDAY, REGULAR_REST_DAY, HOLIDAY_REST and MAKEUP_WORKDAY.
- Define CalendarRuleSource with WEEKEND_DEFAULT, SYSTEM_CALENDAR, BUILTIN_ICS, CUSTOM_ICS and MANUAL.
- Define CalendarDayRule(date, kind, source, sourceId, label, sourceDayOfWeek, sourceDate, updatedAt).
- Define HolidayCalendarSourceInput(id, name, url, builtIn, enabled).
- Add HolidayCalendarSourceEntity with unique URL, built-in/enabled flags, last-success time, ETag, Last-Modified and last-error fields.
- Add HolidayCalendarEventEntity keyed by sourceId and eventKey, with normalized date range, summaries, parsed kind/source weekday/source date and fetched time.
- Add CalendarDayOverrideEntity keyed by date, with target kind, note, createdAt and updatedAt.
- Add DAO methods observeSources(), upsertSource(), deleteCustomSource(id), findEventsBetween(start, end), replaceEventsForSource(sourceId, events), observeDayOverridesBetween(start, end), upsertDayOverrides(rows) and deleteDayOverrides(dates).

- [ ] Step 1: Write DAO and migration tests.

Use an in-memory DailyDatabase to verify two sources retain separate events, replacing one source does not delete another, a date override upserts by date, and clearing one range leaves other dates. Add a version 8 to 9 migration test that checks the new tables exist and an existing SemesterCalendarAdjustmentEntity row survives.

- [ ] Step 2: Run the focused tests and verify the expected missing-schema failure.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.database.HolidayCalendarDaoTest --tests com.daily.life.core.database.HolidayCalendarMigrationTest --console=plain
~~~

Expected: failure because the new entities, DAO methods and version 9 migration do not exist.

- [ ] Step 3: Implement models, entities, DAOs and migration.

Use the existing LocalDate converter and ISO date strings. Add indexes for source ID and normalized date range. Register the entities and MIGRATION_8_9. Do not use destructive database recreation.

- [ ] Step 4: Run persistence tests.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.database.HolidayCalendarDaoTest --tests com.daily.life.core.database.HolidayCalendarMigrationTest --tests com.daily.life.core.database.DailyDatabaseMigrationTest --console=plain
~~~

Expected: all selected tests pass and existing semester/calendar-adjustment data survives.

- [ ] Step 5: Commit.

~~~powershell
git add app/src/main/java/com/daily/life/core/calendar/CalendarDayRuleModels.kt app/src/main/java/com/daily/life/core/database app/src/main/java/com/daily/life/core/datastore/DailyPreferences.kt app/src/test/java/com/daily/life/core/database/HolidayCalendarDaoTest.kt app/src/test/java/com/daily/life/core/database/HolidayCalendarMigrationTest.kt
git commit -m "feat: persist holiday calendar sources and overrides"
~~~

### Task 3: Implement ICS parsing, fetching and cache updates

**Files:**
- Create: app/src/main/java/com/daily/life/core/calendar/IcsCalendarParser.kt
- Create: app/src/main/java/com/daily/life/core/calendar/IcsCalendarClient.kt
- Create: app/src/test/java/com/daily/life/core/calendar/IcsCalendarParserTest.kt
- Create: app/src/test/java/com/daily/life/core/calendar/IcsCalendarClientTest.kt

**Interfaces:**
- Define IcsCalendarSource(id, name, url, builtIn).
- Define IcsCalendarEvent(eventKey, startDate, endExclusiveDate, title, description, kind, sourceDayOfWeek, sourceDate).
- Define parseIcsCalendar(text, source, zone): List<IcsCalendarEvent>.
- Define IcsCalendarClient.fetch(source, etag, lastModified): IcsFetchResult.
- Define IcsFetchResult as NotModified, Success(events, etag, lastModified) or Failure(message, retryable).

- [ ] Step 1: Write parser tests.

Cover all-day DTSTART/DTEND with an exclusive end date, timed events in Asia/Shanghai, escaped commas/newlines, 国庆补周五, 调休上班, 国庆节放假, and one invalid event alongside one valid event. Assert that the unknown makeup source weekday remains null.

- [ ] Step 2: Run the parser tests and verify the expected failure.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.calendar.IcsCalendarParserTest --console=plain
~~~

Expected: failure because parseIcsCalendar does not exist.

- [ ] Step 3: Implement the pure parser.

Unfold ICS content lines, isolate VEVENT blocks, honor all-day exclusive DTEND, normalize escaped fields, and reuse shared Chinese special-day classification. Skip only malformed events; do not fail the entire feed.

- [ ] Step 4: Write client tests with a fake OkHttp dispatcher.

Assert 200 success, 304 NotModified, timeout Failure without throwing, conditional If-None-Match and If-Modified-Since headers, and rejection of non-HTTPS URLs before network access.

- [ ] Step 5: Implement the OkHttp client.

Use the existing OkHttp dependency, bounded connect/read timeouts, and conditional requests. Return parsed events and response metadata to the repository; do not write Room from the HTTP client. The repository task will replace only that source's cached events and metadata in one transaction and will preserve old events on failure.

- [ ] Step 6: Run both focused suites.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.calendar.IcsCalendarParserTest --tests com.daily.life.core.calendar.IcsCalendarClientTest --console=plain
~~~

Expected: all selected tests pass.

- [ ] Step 7: Commit.

~~~powershell
git add app/src/main/java/com/daily/life/core/calendar/IcsCalendarParser.kt app/src/main/java/com/daily/life/core/calendar/IcsCalendarClient.kt app/src/test/java/com/daily/life/core/calendar/IcsCalendarParserTest.kt app/src/test/java/com/daily/life/core/calendar/IcsCalendarClientTest.kt
git commit -m "feat: parse and cache holiday ICS feeds"
~~~

### Task 4: Build the unified rule repository and daily synchronization

**Files:**
- Create: app/src/main/java/com/daily/life/core/calendar/HolidayCalendarRepository.kt
- Create: app/src/main/java/com/daily/life/core/calendar/HolidayCalendarSyncWorker.kt
- Modify: app/src/main/java/com/daily/life/core/AppContainer.kt
- Modify: app/src/main/java/com/daily/life/DailyApplication.kt
- Modify: app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt
- Create: app/src/test/java/com/daily/life/core/calendar/HolidayCalendarRepositoryTest.kt
- Create: app/src/test/java/com/daily/life/core/calendar/HolidayCalendarSyncWorkerTest.kt

**Interfaces:**
- Define HolidayCalendarRepository.resolveBetween(startDate, endDate): List<CalendarDayRule>.
- Define observeSources(): Flow<List<HolidayCalendarSourceEntity>>.
- Define syncSource(sourceId): SyncSourceResult, syncAllEnabledSources(): SyncSummary.
- Define saveDateOverride(startDate, endDate, kind, note) and clearDateOverrides(dates).
- Define CalendarDayRuleMerger.merge(startDate, endDate, cachedIcsDays, systemDays, overrides).
- Define sealed SyncSourceResult with Success(sourceId, eventCount), UsedCache(sourceId, message) and Failed(sourceId, message, retryable).
- Define SyncSummary(succeeded, failed, usedCache, message).

- [ ] Step 1: Write merger tests.

~~~kotlin
@Test fun manualOverrideBeatsIcsSystemAndWeekend() { /* same date -> MANUAL */ }
@Test fun icsBeatsSystemCalendar() { /* same date -> BUILTIN_ICS */ }
@Test fun makeupBeatsHolidayWithinOnePriority() { /* same date -> MAKEUP_WORKDAY */ }
@Test fun missingSourcesUseSaturdayRestDefault() { /* Saturday -> REGULAR_REST_DAY */ }
@Test fun sourceSummariesAreRetainedWithoutDuplicateRules() { /* one output date */ }
~~~

- [ ] Step 2: Implement the pure merger.

Generate one rule for every date in the inclusive request range. Start with weekday/weekend defaults, overlay system-calendar days, overlay all cached ICS days, then overlay manual overrides. Within a layer, merge labels and source IDs; choose MAKEUP_WORKDAY over HOLIDAY_REST.

- [ ] Step 3: Write repository fallback tests.

Use fake DAOs, fake IcsCalendarClient and fake SystemCalendarScheduleReader to assert failed sources preserve old events, successful sources replace only their own events, denied permission still returns ICS rules, and manual overrides survive all remote failures.

- [ ] Step 4: Implement repository wiring and seed the built-in source.

Construct one application-scoped repository from DailyDatabase, DailyPreferences, SystemCalendarScheduleReader.from(application) and IcsCalendarClient. On first initialization insert exactly one source with ID builtin-china-public-holidays, builtIn = true and enabled = true, using the single configured recommendation URL constant; subsequent launches must not duplicate or overwrite user changes. Keep read failures as state data, not exceptions from PDF import.

- [ ] Step 5: Schedule daily work and startup checks.

Use PeriodicWorkRequestBuilder<HolidayCalendarSyncWorker>(1, TimeUnit.DAYS), unique work name daily-holiday-calendar-sync and ExistingPeriodicWorkPolicy.UPDATE. Enqueue from DailyApplication.onCreate; expose syncAllEnabledSources for settings and call a freshness check when main navigation resumes.

- [ ] Step 6: Run repository and worker tests.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.calendar.HolidayCalendarRepositoryTest --tests com.daily.life.core.calendar.HolidayCalendarSyncWorkerTest --console=plain
~~~

Expected: cache-backed failures return WorkManager success; no network exception reaches UI code.

- [ ] Step 7: Commit.

~~~powershell
git add app/src/main/java/com/daily/life/core/calendar/HolidayCalendarRepository.kt app/src/main/java/com/daily/life/core/calendar/HolidayCalendarSyncWorker.kt app/src/main/java/com/daily/life/core/AppContainer.kt app/src/main/java/com/daily/life/DailyApplication.kt app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt app/src/test/java/com/daily/life/core/calendar/HolidayCalendarRepositoryTest.kt app/src/test/java/com/daily/life/core/calendar/HolidayCalendarSyncWorkerTest.kt
git commit -m "feat: merge holiday sources and schedule daily sync"
~~~

### Task 5: Add source management and manual date-state UI

**Files:**
- Modify: app/src/main/java/com/daily/life/feature/settings/SettingsState.kt
- Modify: app/src/main/java/com/daily/life/feature/settings/SettingsViewModel.kt
- Modify: app/src/main/java/com/daily/life/feature/settings/SettingsScreen.kt
- Modify: app/src/main/java/com/daily/life/feature/schedule/ScheduleModels.kt
- Modify: app/src/main/java/com/daily/life/feature/schedule/ScheduleViewModel.kt
- Modify: app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt
- Modify: app/src/main/java/com/daily/life/feature/schedule/ScheduleScreen.kt
- Create: app/src/test/java/com/daily/life/feature/settings/HolidayCalendarSettingsTest.kt
- Create: app/src/test/java/com/daily/life/feature/schedule/ScheduleHolidayOverrideTest.kt

**Interfaces:**
- Extend SettingsState with holidaySources, holidaySyncStatus, holidayLastSyncAt and holidayError.
- Add SettingsViewModel.addHolidaySource(name, url), toggleHolidaySource(id, enabled), deleteHolidaySource(id) and syncHolidaySources().
- Add ScheduleViewModel.saveCalendarDayOverride(startDate, endDate, kind, note) and clearCalendarDayOverrides(dates).
- Make month view consume List<CalendarDayRule> rather than only a system-calendar badge map.

- [ ] Step 1: Write state and ViewModel tests.

Assert the built-in source cannot be deleted, blank custom URLs are rejected, disabled sources are excluded from sync, immediate sync updates visible status, and one-day/range overrides expand into per-date rows and can be cleared.

- [ ] Step 2: Implement Settings source management.

Add a 中国节假日 card with the built-in source, custom source rows, enabled switches, last successful sync, error text, HTTPS URL input, 添加订阅 and 立即同步 buttons. Validate nonblank names and HTTPS URLs before persistence.

- [ ] Step 3: Implement month-view status and details.

Map regular rest, holiday rest and makeup workday to distinct existing visual tokens; show a manual marker without changing the month layout. Date details show final status, source labels, last sync time and 修改这一天.

- [ ] Step 4: Implement single-day and range overrides.

Use existing date-picker components for start/end dates. Provide 设为休息日, 设为工作日 and 恢复自动判断. Expand a range into one CalendarDayOverrideEntity per date.

- [ ] Step 5: Run selected tests.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.settings.HolidayCalendarSettingsTest --tests com.daily.life.feature.schedule.ScheduleHolidayOverrideTest --tests com.daily.life.feature.schedule.ScheduleCalendarPresentationTest --console=plain
~~~

Expected: selected tests pass and existing schedule editor/reminder behavior remains unchanged.

- [ ] Step 6: Commit.

~~~powershell
git add app/src/main/java/com/daily/life/feature/settings app/src/main/java/com/daily/life/feature/schedule app/src/test/java/com/daily/life/feature/settings/HolidayCalendarSettingsTest.kt app/src/test/java/com/daily/life/feature/schedule/ScheduleHolidayOverrideTest.kt
git commit -m "feat: manage holiday sources and manual day overrides"
~~~

### Task 6: Integrate unified rules with timetable import and display

**Files:**
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableImportScreen.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableCalendarMapping.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt
- Modify: app/src/main/java/com/daily/life/core/database/DailyEntities.kt
- Modify: app/src/main/java/com/daily/life/core/database/DailyDaos.kt
- Modify: app/src/main/java/com/daily/life/core/database/DailyDatabase.kt
- Modify: app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt
- Create: app/src/test/java/com/daily/life/feature/timetable/SemesterClassOverrideTest.kt
- Modify: app/src/test/java/com/daily/life/core/database/HolidayCalendarMigrationTest.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableImportAdjustmentTest.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableCalendarMappingTest.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableViewModelTest.kt

**Interfaces:**
- Keep SemesterCalendarAdjustmentInput(actualDate, sourceDayOfWeek, sourceDate, sourceLabel).
- Add ClassOverride with FOLLOW_CALENDAR, HAS_CLASS and NO_CLASS.
- Add SemesterClassOverrideInput(actualDate, override) and SemesterClassOverrideEntity.
- Extend TimetableRepository.confirmImport(..., calendarAdjustments, classOverrides).
- Add observeClassOverrides(semesterId): Flow<List<SemesterClassOverrideEntity>>.
- Keep TimetableImportState.canConfirm equal to existing PDF validation; unresolved calendar calibration does not block import.

- [ ] Step 1: Add behavior tests.

Cover holiday suppression, confirmed Saturday makeup using Friday's source, unknown makeup showing needsMakeupConfirmation with no course source, HAS_CLASS enabling actual-weekday course display and NO_CLASS suppressing a normal course. Confirm an unknown makeup row remains visible but importable.

- [ ] Step 2: Implement mapping precedence.

Apply NO_CLASS, then HAS_CLASS, then holiday/makeup/default calendar rules. Confirmed makeup uses the saved source weekday; unknown makeup leaves courseDayOfWeek null and retains the warning.

- [ ] Step 3: Load unified rules in import and current-week state.

Inject HolidayCalendarRepository into TimetableViewModel. Query the inclusive semester range, preserve selected source weekdays while automatic data refreshes, and never overwrite manual date/class overrides.

- [ ] Step 4: Implement import UI.

Keep the existing 节假日与调休校准 card. Preselect recognized weekdays, allow 周一 through 周日, show unknown rows as 待校准, and add per-date/range 有课/无课 controls. Confirm remains enabled when only calendar calibration is unresolved.

- [ ] Step 5: Persist transactionally.

Add SemesterClassOverrideEntity with primary key semesterId + actualDate, register it in DailyDatabase version 10, and add MIGRATION_9_10. Save semester, courses, period times, source-weekday adjustments and class overrides in one Room transaction. On replacement delete only rows belonging to that semester; preserve global date overrides.

Extend HolidayCalendarMigrationTest to open version 9 data, run MIGRATION_9_10, verify the class-override table exists, and verify all source events, date overrides and semester calendar adjustments remain unchanged.

- [ ] Step 6: Run timetable tests.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.SemesterClassOverrideTest --tests com.daily.life.feature.timetable.TimetableImportAdjustmentTest --tests com.daily.life.feature.timetable.TimetableCalendarMappingTest --tests com.daily.life.feature.timetable.TimetableRepositoryTest --tests com.daily.life.feature.timetable.TimetableViewModelTest --console=plain
~~~

Expected: all selected tests pass, including the formerly failing makeup-date and ViewModel initialization cases.

- [ ] Step 7: Commit.

~~~powershell
git add app/src/main/java/com/daily/life/feature/timetable app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt app/src/test/java/com/daily/life/feature/timetable
git commit -m "feat: apply unified holiday rules to timetable"
~~~

### Task 7: Reuse the mapping for course reminders and verify the APK

**Files:**
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt
- Modify: app/src/main/java/com/daily/life/core/calendar/CalendarReminderSyncer.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt
- Create: app/src/test/java/com/daily/life/core/calendar/CalendarReminderSyncerTest.kt
- Modify: app/src/main/AndroidManifest.xml only if the final worker/provider requires a declaration
- Create: docs/qa/daily-chinese-holiday-timetable.png

**Interfaces:**
- Keep courseOccurrenceDates(...) as the sole pure date-to-occurrence function for UI and reminders.
- Make syncFutureCourseCalendarMessages(semesterId, periodTimes) load the same CalendarDayRule snapshot, semester adjustments and class overrides.
- Use a sync owner key containing course.id, nominal week and actual date.

- [ ] Step 1: Write reminder regression tests.

Assert holiday dates create no requests, confirmed makeup creates a request on the actual Saturday, unknown makeup creates no copied request, HAS_CLASS creates the explicit actual-date request, and two actual dates create distinct sync-link keys.

- [ ] Step 2: Implement reminder synchronization through the shared mapping.

Load final rules and class overrides, call courseOccurrenceDates for every stored course/week, preserve reminder mode/minutes, and pass actualDate to CalendarReminderSyncer.syncCourseOccurrence. Keep old cleanup for course replacement and period-time changes.

- [ ] Step 3: Run reminder and full unit tests.

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableRepositoryTest --tests com.daily.life.core.calendar.CalendarReminderSyncerTest --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
~~~

Expected: full unit suite completes with zero failures.

- [ ] Step 4: Build the debug APK.

~~~powershell
.\gradlew.bat :app:assembleDebug --console=plain
~~~

Record app/build/outputs/apk/debug/app-debug.apk and confirm versionName remains 0.1.6 unless separately changed.

- [ ] Step 5: Run emulator or device verification.

Verify Settings source management, denied READ_CALENDAR fallback, month holiday/makeup markers, single-day/range override, import of an unknown makeup without blocking, confirmed Friday-to-Saturday course mapping, and removal of future reminder after NO_CLASS. Capture docs/qa/daily-chinese-holiday-timetable.png only after the flow is visibly correct.

- [ ] Step 6: Review and commit the verification artifact.

~~~powershell
git diff --check
git status --short
git add app/src/main/java app/src/main/AndroidManifest.xml app/src/test docs/qa/daily-chinese-holiday-timetable.png
git commit -m "test: verify Chinese holiday timetable integration"
git status --short
~~~

The final status must be empty for the implementation worktree. Root-level APK files outside .worktrees/daily-rebuild are not part of this plan.
