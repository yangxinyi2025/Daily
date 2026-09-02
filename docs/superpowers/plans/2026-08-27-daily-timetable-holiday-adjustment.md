# Daily 课表节假日与调休校准 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 PDF 导入确认时让用户确认系统日历中的调休补课来源，并让课表展示与课程消息提醒按确认后的实际日期运行。

**Architecture:** 新增共享的系统日历特殊日期读取/解析层；新增学期级调休确认表保存“实际日期 → 来源星期”。课表和课程提醒均调用同一个纯逻辑日期映射器，普通日期、休假日、已确认调休分别生成对应的课程槽位。原有 PDF 课程周次数据保持不变。

**Tech Stack:** Kotlin、Jetpack Compose、Room、CalendarContract.Instances、java.time、JUnit/Robolectric、现有 Gradle Android 工程。

**Spec:** `docs/superpowers/specs/2026-08-27-daily-timetable-holiday-adjustment-design.md`

## Global Constraints

- 不修改 PDF 解析出的原始课程规则。
- 不内置节假日表，不根据周末或事件标题自行猜测补课来源。
- 课表展示和课程消息提醒必须使用完全相同的实际日期映射。
- 用户拒绝读取权限时不阻塞普通课表导入，但必须明确提示课表未进行节假日/调休校准。
- 检测到调休但来源日期未确认时禁止导入，不提供跳过校准入口。
- 原有 1-12 节次、周次计算和其他页面业务逻辑保持不变。

---

### Task 1: 建立系统日历特殊日期的共享模型与纯逻辑解析器

**Files:**
- Create: `app/src/main/java/com/daily/life/core/calendar/SystemCalendarScheduleModels.kt`
- Create: `app/src/main/java/com/daily/life/core/calendar/SystemCalendarScheduleParser.kt`
- Create: `app/src/test/java/com/daily/life/core/calendar/SystemCalendarScheduleParserTest.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleCalendarPresentation.kt`

**Interfaces:**
- Produce `SystemCalendarScheduleEvent(startAt: Instant, endAt: Instant, title: String?, description: String?)`.
- Produce `SystemCalendarSpecialDay(date: LocalDate, kind: Holiday|MakeupWorkday, sourceDayOfWeek: Int?, label: String)`.
- Produce `parseSystemCalendarSpecialDay(event, zone): List<SystemCalendarSpecialDay>` and `mergeSystemCalendarSpecialDays(...)`.
- Preserve the existing month-page badge behavior by adapting the schedule page to the shared model.

- [ ] **Step 1: Write failing parser tests**

```kotlin
@Test fun parsesHolidayAndEachDateOfAnAllDayRange() { /* 10/1-10/7 -> seven Holiday entries */ }
@Test fun parsesExplicitMakeupWeekdayFromChineseTitle() { /* “国庆补周五” -> MakeupWorkday(sourceDayOfWeek = 5) */ }
@Test fun leavesSourceDayUnknownWhenTitleOnlySaysMakeupWorkday() { /* “调休上班” -> sourceDayOfWeek = null */ }
@Test fun makeupWorkdayWinsWhenHolidayAndMakeupOverlap() { /* same date -> MakeupWorkday */ }
@Test fun ignoresDailyCourseCalendarEvents() { /* “课程：高等数学” -> no special day */ }
```

- [ ] **Step 2: Run the focused tests and verify the expected failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.calendar.SystemCalendarScheduleParserTest`

Expected: FAIL because the shared parser types/functions do not exist yet.

- [ ] **Step 3: Implement the minimal parser and schedule adapter**

Use the existing `CalendarContract.Instances` text classification rules, add Chinese forms `补周一` through `补周日`, `补星期一` through `补星期日`, and parse all-day/multi-day event ranges into one date per day. Keep `MakeupWorkday` precedence over `Holiday`. Convert the month-page badge map to the shared special-day result without changing its visible labels.

- [ ] **Step 4: Run the focused tests and existing schedule tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.calendar.SystemCalendarScheduleParserTest --tests com.daily.life.feature.schedule.ScheduleCalendarPresentationTest`

Expected: PASS.

- [ ] **Step 5: Commit the parser unit**

```bash
git add app/src/main/java/com/daily/life/core/calendar app/src/main/java/com/daily/life/feature/schedule/ScheduleCalendarPresentation.kt app/src/test/java/com/daily/life/core/calendar/SystemCalendarScheduleParserTest.kt
git commit -m "feat: parse system calendar holiday and makeup days"
```

### Task 2: Add persistent per-semester makeup confirmations

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/database/DailyEntities.kt`
- Modify: `app/src/main/java/com/daily/life/core/database/DailyDaos.kt`
- Modify: `app/src/main/java/com/daily/life/core/database/DailyDatabase.kt`
- Create: `app/src/test/java/com/daily/life/core/database/SemesterCalendarAdjustmentDaoTest.kt`

**Interfaces:**
- Add `SemesterCalendarAdjustmentEntity(semesterId: Long, actualDate: LocalDate, sourceDayOfWeek: Int, sourceDate: LocalDate?, sourceLabel: String?, updatedAt: Long)` with primary key `(semesterId, actualDate)`.
- Add DAO methods `findBySemester(semesterId)`, `upsertAll(rows)`, `deleteBySemester(semesterId)`, and `deleteByActualDates(semesterId, dates)`.
- Add Room migration `7 -> 8`, register the entity and migration, and preserve all existing rows.

- [ ] **Step 1: Write the failing DAO/migration test**

Create a versioned database test that inserts an adjustment, reopens through the current schema, verifies the date and source weekday survive, replaces the same `(semesterId, actualDate)`, and verifies deleting a semester removes its adjustments.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.database.SemesterCalendarAdjustmentDaoTest`

Expected: FAIL because the entity, DAO methods, and schema version 8 do not exist.

- [ ] **Step 3: Implement the entity, DAO, migration, and database registration**

Use the existing `LocalDate` converter. Add an index on `semesterId` if Room requires it for the semester query. Make the migration create the table with the composite primary key and nullable source date/label columns.

- [ ] **Step 4: Run the DAO test and existing migration tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.database.SemesterCalendarAdjustmentDaoTest --tests com.daily.life.core.database.DailyDatabaseMigrationTest`

Expected: PASS.

- [ ] **Step 5: Commit the persistence unit**

```bash
git add app/src/main/java/com/daily/life/core/database app/src/test/java/com/daily/life/core/database/SemesterCalendarAdjustmentDaoTest.kt
git commit -m "feat: persist semester makeup-day confirmations"
```

### Task 3: Implement one pure date-to-course-slot mapping service

**Files:**
- Create: `app/src/main/java/com/daily/life/feature/timetable/TimetableCalendarMapping.kt`
- Create: `app/src/test/java/com/daily/life/feature/timetable/TimetableCalendarMappingTest.kt`

**Interfaces:**
- Add `TimetableScheduleSlot(actualDate: LocalDate, week: Int, courseDayOfWeek: Int?, isHoliday: Boolean, needsMakeupConfirmation: Boolean)`.
- Add `mapWeekToScheduleSlots(semesterStartDate, selectedWeek, specialDays, confirmedAdjustments): List<TimetableScheduleSlot>`.
- Add `courseOccurrenceDate(semesterStartDate, week, courseDayOfWeek, specialDays, confirmedAdjustments): LocalDate?` for reminder synchronization.

- [ ] **Step 1: Write failing behavior tests**

```kotlin
@Test fun normalWeekUsesActualWeekday() { /* Monday slot -> source Monday */ }
@Test fun holidaySlotHasNoCourseSource() { /* Holiday on Thursday -> source null */ }
@Test fun confirmedMakeupUsesSelectedSourceWeekdayOnActualWeekendDate() { /* Saturday mapped to Friday */ }
@Test fun unconfirmedMakeupDoesNotCopyAnyCourse() { /* source null + needs confirmation */ }
@Test fun sameDateMakeupOverridesHoliday() { /* source weekday remains available */ }
@Test fun reminderOccurrenceReturnsActualMakeupDate() { /* course from Friday -> Saturday date */ }
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableCalendarMappingTest`

Expected: FAIL because the mapping service does not exist.

- [ ] **Step 3: Implement the minimal pure mapping service**

Generate the seven dates for the selected nominal week. Apply `MakeupWorkday` after `Holiday`; use a confirmed adjustment’s source weekday only on its actual date; leave unconfirmed makeup dates with `courseDayOfWeek = null`. Keep the existing nominal week number unchanged.

- [ ] **Step 4: Run focused timetable tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableCalendarMappingTest --tests com.daily.life.feature.timetable.TimetableWeekDatesTest`

Expected: PASS.

- [ ] **Step 5: Commit the mapping unit**

```bash
git add app/src/main/java/com/daily/life/feature/timetable/TimetableCalendarMapping.kt app/src/test/java/com/daily/life/feature/timetable/TimetableCalendarMappingTest.kt
git commit -m "feat: map timetable courses across holidays and makeup days"
```

### Task 4: Read system calendar entries for the import range

**Files:**
- Create: `app/src/main/java/com/daily/life/core/calendar/SystemCalendarScheduleReader.kt`
- Create: `app/src/test/java/com/daily/life/core/calendar/SystemCalendarScheduleReaderTest.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/SystemCalendarDayBadgeReader.kt`

**Interfaces:**
- Add `SystemCalendarScheduleReader.readBetween(startDate: LocalDate, endDate: LocalDate): List<SystemCalendarSpecialDay>`.
- Add `SystemCalendarScheduleReader.from(context): SystemCalendarScheduleReader` with a permission callback.
- Make the existing schedule-month reader use the shared query/parse path.

- [ ] **Step 1: Write the failing reader test**

Use a fake `ContentResolver`/cursor seam to verify the reader queries the requested time range, maps an all-day holiday range and a makeup event, and returns an empty result when `READ_CALENDAR` is not granted.

- [ ] **Step 2: Run the focused reader test and verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.calendar.SystemCalendarScheduleReaderTest`

Expected: FAIL because the reader and resolver seam do not exist.

- [ ] **Step 3: Implement range querying with safe failure handling**

Use `CalendarContract.Instances.CONTENT_URI` with the supplied range, query `BEGIN`, `END`, `TITLE`, and `DESCRIPTION`, parse with the shared parser, and return empty data on denied permission or query failure. Do not throw into PDF import.

- [ ] **Step 4: Run reader and schedule tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.calendar.SystemCalendarScheduleReaderTest --tests com.daily.life.feature.schedule.ScheduleCalendarPresentationTest`

Expected: PASS.

- [ ] **Step 5: Commit the reader unit**

```bash
git add app/src/main/java/com/daily/life/core/calendar/SystemCalendarScheduleReader.kt app/src/main/java/com/daily/life/feature/schedule/SystemCalendarDayBadgeReader.kt app/src/test/java/com/daily/life/core/calendar/SystemCalendarScheduleReaderTest.kt
git commit -m "feat: read system calendar schedule adjustments"
```

### Task 5: Add adjustment choices to PDF import preview

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableImportScreen.kt`
- Create: `app/src/test/java/com/daily/life/feature/timetable/TimetableImportAdjustmentTest.kt`

**Interfaces:**
- Add `TimetableAdjustmentChoiceState(actualDate, label, sourceDayOfWeek, options, isRequired)` to `TimetableImportState`.
- Add ViewModel methods `loadCalendarAdjustments()`, `updateMakeupSource(actualDate, sourceDayOfWeek)` and `refreshSystemCalendarDays()`.
- `TimetableImportState.canConfirm` must require all detected makeup rows to have a source; there is no skip action.

- [ ] **Step 1: Write failing ViewModel/import-state tests**

Test that a detected `补周五` row defaults to Friday but remains editable, an unknown `调休上班` row blocks confirmation until selected, and changing the selection updates state.

- [ ] **Step 2: Run the focused import tests and verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableImportAdjustmentTest`

Expected: FAIL because import state has no adjustment choices or methods.

- [ ] **Step 3: Implement import state and ViewModel loading**

Inject an optional `SystemCalendarScheduleReader` into `TimetableViewModel`. Once the semester start date is valid and the PDF preview exists, read through the default semester range, convert special days to import rows, preselect parser-recognized source weekdays, and preserve user edits while the name/date fields change. Keep the read result in the ViewModel as `calendarSpecialDays` so later timetable state creation can use the same dates.

- [ ] **Step 4: Implement the Compose confirmation UI**

Add a compact `节假日与调休校准` card after semester information and before course rows. Show holiday dates as read-only. Show each makeup actual date with a Chinese weekday selector (`周一`…`周日`), a required marker when source is unknown, and a clear “跳过本次校准” action that exposes the warning. Keep the existing PDF/course editing flow unchanged.

- [ ] **Step 5: Run focused tests and Compose compilation**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableImportAdjustmentTest :app:compileDebugKotlin`

Expected: PASS.

- [ ] **Step 6: Commit the import flow**

```bash
git add app/src/main/java/com/daily/life/feature/timetable app/src/test/java/com/daily/life/feature/timetable/TimetableImportAdjustmentTest.kt
git commit -m "feat: confirm makeup days during timetable import"
```

### Task 6: Persist choices and apply them to timetable display

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt`
- Modify: `app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt`
- Modify: `app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt`
- Modify: `app/src/test/java/com/daily/life/feature/timetable/TimetableViewModelTest.kt`

**Interfaces:**
- Extend `TimetableRepository.confirmImport(..., calendarAdjustments: List<SemesterCalendarAdjustmentInput>)`.
- Add `TimetableRepository.observeCalendarAdjustments(semesterId): Flow<List<SemesterCalendarAdjustmentEntity>>`.
- Add `RoomTimetableRepository.saveCalendarAdjustments(semesterId, rows)`.
- Extend `TimetableState` with `calendarSpecialDays` and `calendarAdjustmentWarning` so the screen can show a clear uncalibrated state.

- [ ] **Step 1: Write failing persistence/display tests**

Test that confirmed adjustments are written with the imported semester, replacement import removes the old semester’s rows, the ViewModel hides a holiday course, and the ViewModel places a Friday course in a confirmed Saturday makeup column.

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableRepositoryTest --tests com.daily.life.feature.timetable.TimetableViewModelTest`

Expected: FAIL because repository import has no adjustment parameter and state creation always groups by actual weekday.

- [ ] **Step 3: Implement repository persistence and ViewModel mapping**

Save adjustments inside the import transaction, remove old rows when replacing an existing semester, load the current semester’s saved adjustments into the state flow, and make `createState` map each of the seven day columns through `TimetableCalendarMapping` using the ViewModel’s latest `calendarSpecialDays`. Add a small warning to state when the system calendar could not be read; if a detected makeup date lacks a saved confirmation, show the required calibration warning and do not copy its courses.

- [ ] **Step 4: Wire the reader and adjustment data through `DailyNavHost`**

Construct one application-scoped `SystemCalendarScheduleReader` for timetable import and pass it to the ViewModel; keep calendar write synchronization independent from read permission failures.

- [ ] **Step 5: Run focused timetable tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableRepositoryTest --tests com.daily.life.feature.timetable.TimetableViewModelTest --tests com.daily.life.feature.timetable.TimetableCalendarMappingTest`

Expected: PASS.

- [ ] **Step 6: Commit the display unit**

```bash
git add app/src/main/java/com/daily/life/feature/timetable app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt app/src/test/java/com/daily/life/feature/timetable
git commit -m "feat: apply confirmed holiday adjustments to timetable"
```

### Task 7: Make course message reminders use actual adjusted dates

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/calendar/CalendarReminderSyncer.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt`
- Modify: `app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt`
- Modify: `app/src/test/java/com/daily/life/core/calendar/CalendarReminderSyncerTest.kt`

**Interfaces:**
- Extend `syncCourseOccurrence` with `actualDate: LocalDate` and use a link key containing `course.id`, nominal week, and actual date.
- Make `syncFutureCourseCalendarMessages` load the same adjustments and call `courseOccurrenceDate` for every stored course week.

- [ ] **Step 1: Write failing reminder tests**

Test that a holiday course produces no calendar request, a confirmed Saturday makeup produces a request whose start date is Saturday, and two different actual dates do not overwrite each other’s sync links.

- [ ] **Step 2: Run the focused reminder tests and verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableRepositoryTest --tests com.daily.life.core.calendar.CalendarReminderSyncerTest`

Expected: FAIL because reminders currently calculate dates only from course weekday and key links only by course/week.

- [ ] **Step 3: Implement shared mapping in reminder synchronization**

Load system special days for the semester range and saved adjustments, skip holiday occurrences, route confirmed makeup occurrences to their actual date, and skip unconfirmed makeup dates. Preserve existing reminder mode and minute settings. This must call the same `courseOccurrenceDate` mapping function used by the timetable UI.

- [ ] **Step 4: Update cleanup/resync behavior**

Keep deleting all links by course ID before period-time changes or replacement imports so old date-keyed events cannot remain in the system calendar.

- [ ] **Step 5: Run focused and full unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest`

Expected: PASS with no new warnings.

- [ ] **Step 6: Commit the reminder unit**

```bash
git add app/src/main/java/com/daily/life/core/calendar/CalendarReminderSyncer.kt app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt app/src/test/java/com/daily/life/core/calendar/CalendarReminderSyncerTest.kt app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt
git commit -m "fix: sync course messages to adjusted calendar dates"
```

### Task 8: Verify permissions, import flow, and APK behavior

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml` only if the existing calendar permissions are insufficient.
- Create: `docs/qa/daily-timetable-holiday-adjustment.png`

- [ ] **Step 1: Add a one-time read-calendar permission request for timetable calibration**

Use the existing Activity Result API. Request `READ_CALENDAR` when the user begins import/calibration, allow ordinary import to continue after denial, and show the explicit “未进行系统节假日/调休校准” state. A detected but unconfirmed makeup row still blocks confirmation.

- [ ] **Step 2: Run unit and instrumentation checks**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`; if an emulator is available, install `app-debug.apk`, open timetable import, verify the calibration card, choose a makeup weekday, confirm import, and capture the timetable.

Expected: build succeeds; UI shows holidays without courses, confirmed makeup courses on actual weekend dates, and import remains usable when calendar read permission is denied.

- [ ] **Step 3: Compare the screenshot against the existing timetable UI**

Check that the calendar-adjustment card does not alter the 1-12 timetable grid, existing Chinese typography, or bottom navigation. Fix only layout regressions introduced by the new import rows.

- [ ] **Step 4: Run final verification**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug` and record the APK path and any emulator limitation in the final response.

- [ ] **Step 5: Commit final verification changes**

```bash
git add app/src/main/java/com/daily/life/feature/timetable app/src/main/AndroidManifest.xml docs/qa/daily-timetable-holiday-adjustment.png
git commit -m "test: verify timetable holiday adjustment flow"
```
