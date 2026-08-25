# System Calendar Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synchronize Daily message reminders for birthdays, courses, and user-selected message events to the phone calendar, while preserving Daily-only alarms for ordinary events the user marked as alarms.

**Architecture:** Add a calendar gateway that is the only component allowed to access `CalendarContract`; it selects a writable primary calendar and writes `METHOD_ALERT` events. Room stores an ownership mapping between Daily objects and calendar event IDs so edits and deletes affect only Daily-created events. The schedule and timetable repositories decide whether to call the gateway or the existing `ReminderScheduler` based on the reminder-routing policy.

**Tech Stack:** Kotlin, Android Calendar Provider (`CalendarContract`), Room 2.x, Compose, Activity Result runtime permissions, Robolectric/JUnit.

**Spec:** `docs/superpowers/specs/2026-08-25-system-calendar-reminders.md`

## Global Constraints

- Course reminders and birthdays are always `METHOD_ALERT` system-calendar messages; their UI must not offer a Daily alarm route.
- Ordinary schedule events retain the existing `ReminderMode.NOTIFICATION` and `ReminderMode.ALARM` choice.
- Only `ReminderMode.NOTIFICATION` ordinary events are written to the system calendar; `ReminderMode.ALARM` stays on the existing Daily alarm path.
- The app must request `READ_CALENDAR` and `WRITE_CALENDAR` only when calendar synchronization is needed.
- Never update or delete a calendar event unless it has a matching Daily-owned mapping row.
- A failed provider write must preserve the Daily record and return a visible, specific failure state rather than a false success message.
- Do not commit application source in this currently dirty worktree; use targeted diffs and tests as review checkpoints.

---

## File Structure

- `core/database/DailyEntities.kt` — defines `CalendarSyncKind` and the persistent mapping entity.
- `core/database/DailyDaos.kt` — defines mapping queries by Daily owner and external event ID.
- `core/database/DailyDatabase.kt` — registers the mapping entity, migrates schema 5 to 6, and exposes its DAO.
- `core/calendar/SystemCalendarGateway.kt` — pure boundary around Android Calendar Provider reads/writes and provider error mapping.
- `core/calendar/CalendarReminderModels.kt` — value objects and result types shared by repositories and UI.
- `core/calendar/CalendarReminderSyncer.kt` — coordinates calendar ownership mappings with the gateway and database.
- `feature/schedule/ScheduleRepository.kt` — routes birthday/message events to calendar sync and alarms to `ReminderScheduler`.
- `feature/timetable/TimetableRepository.kt` — emits one future course occurrence per real week to the calendar syncer and removes stale mappings during import/semester changes.
- `MainActivity.kt` and `core/navigation/DailyNavHost.kt` — request calendar permissions only after a save or course-reminder action asks for them; report callback outcome to the initiating ViewModel.
- `feature/schedule/ScheduleViewModel.kt`, `feature/timetable/TimetableViewModel.kt`, and their screens — display precise calendar-sync result text and offer the existing alarm choice only for ordinary non-birthday events.

## Task 1: Add Daily-owned calendar mapping persistence

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/database/DailyEntities.kt`
- Modify: `app/src/main/java/com/daily/life/core/database/DailyDaos.kt`
- Modify: `app/src/main/java/com/daily/life/core/database/DailyDatabase.kt`
- Modify: `app/src/test/java/com/daily/life/core/database/DailyDatabaseMigrationTest.kt`

**Interfaces:**
- Produces `enum class CalendarSyncKind { SCHEDULE_EVENT, COURSE_OCCURRENCE }`.
- Produces `CalendarSyncLinkEntity(ownerKind: CalendarSyncKind, ownerKey: String, calendarId: Long, eventId: Long, eventStartAt: Long, lastError: String? = null)` with composite primary key `(ownerKind, ownerKey)`.
- Produces `CalendarSyncLinkDao.find(ownerKind, ownerKey)`, `upsert(link)`, `delete(ownerKind, ownerKey)`, and `findByKind(ownerKind)`.
- Produces `DailyDatabase.MIGRATION_5_6` and database version `6`.

- [ ] **Step 1: Write the failing migration test**

Add this test to `DailyDatabaseMigrationTest`:

```kotlin
@Test
fun migrationFrom5To6AddsCalendarSyncLinksWithoutRemovingExistingRows() {
    val database = createVersion5Database().writableDatabase
    database.execSQL("CREATE TABLE existing_user_data (value TEXT NOT NULL)")
    database.execSQL("INSERT INTO existing_user_data (value) VALUES ('keep')")

    DailyDatabase.MIGRATION_5_6.migrate(database)

    assertTrue(database.hasTable("calendar_sync_links"))
    assertTrue(database.hasRow("SELECT value FROM existing_user_data WHERE value = 'keep'"))
    database.close()
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.daily.life.core.database.DailyDatabaseMigrationTest.migrationFrom5To6AddsCalendarSyncLinksWithoutRemovingExistingRows" --console=plain
```

Expected: compilation failure because `MIGRATION_5_6` and the table do not exist.

- [ ] **Step 3: Implement the minimal schema and DAO**

Add the entity and DAO using the exact keys below; create only a composite primary-key mapping with no foreign keys, because a course occurrence key combines multiple tables:

```kotlin
@Entity(tableName = "calendar_sync_links", primaryKeys = ["ownerKind", "ownerKey"])
data class CalendarSyncLinkEntity(
    val ownerKind: CalendarSyncKind,
    val ownerKey: String,
    val calendarId: Long,
    val eventId: Long,
    val eventStartAt: Long,
    val lastError: String? = null
)
```

Create `calendar_sync_links` in `MIGRATION_5_6`, add it to `@Database.entities`, bump the version to `6`, register `abstract fun calendarSyncLinkDao(): CalendarSyncLinkDao`, and add the migration to `build(context)`.

- [ ] **Step 4: Run the migration test to verify it passes**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Review only the task diff**

Run:

```powershell
git diff --check -- app/src/main/java/com/daily/life/core/database app/src/test/java/com/daily/life/core/database/DailyDatabaseMigrationTest.kt
```

Expected: no whitespace errors. Do not commit application files in this dirty worktree.

## Task 2: Build and test the Calendar Provider gateway

**Files:**
- Create: `app/src/main/java/com/daily/life/core/calendar/CalendarReminderModels.kt`
- Create: `app/src/main/java/com/daily/life/core/calendar/SystemCalendarGateway.kt`
- Create: `app/src/test/java/com/daily/life/core/calendar/SystemCalendarGatewayTest.kt`

**Interfaces:**
- Produces `CalendarReminderRequest(ownerLabel: String, title: String, description: String?, startAt: Instant, endAt: Instant, reminderMinutes: Int, repeatYearly: Boolean)`.
- Produces `CalendarGatewayResult` with `Synced(calendarId: Long, eventId: Long)`, `PermissionDenied`, `NoWritableCalendar`, and `ProviderFailure(message: String)`.
- Produces `SystemCalendarGateway.upsert(existingEventId: Long?, request: CalendarReminderRequest): CalendarGatewayResult` and `delete(calendarId: Long, eventId: Long): CalendarGatewayResult`.

- [ ] **Step 1: Write failing gateway tests**

Create tests with a fake `CalendarProviderClient` interface so no test calls the physical device provider:

```kotlin
@Test
fun upsertUsesPrimaryWritableCalendarAndCreatesAlertAtRequestedOffset() = runTest {
    val client = FakeCalendarProviderClient(
        calendars = listOf(CalendarTarget(id = 7, isPrimary = true, canWrite = true))
    )
    val result = SystemCalendarGateway(client).upsert(null, request(reminderMinutes = 15))

    assertEquals(CalendarGatewayResult.Synced(calendarId = 7, eventId = 42), result)
    assertEquals(15, client.insertedReminderMinutes)
    assertEquals(CalendarContract.Reminders.METHOD_ALERT, client.insertedReminderMethod)
}

@Test
fun upsertReturnsNoWritableCalendarWhenAllCalendarsAreReadOnly() = runTest {
    val result = SystemCalendarGateway(FakeCalendarProviderClient(readOnlyCalendars)).upsert(null, request())
    assertEquals(CalendarGatewayResult.NoWritableCalendar, result)
}
```

- [ ] **Step 2: Run the gateway tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.daily.life.core.calendar.SystemCalendarGatewayTest" --console=plain
```

Expected: compilation failure because gateway types do not exist.

- [ ] **Step 3: Implement the narrow provider boundary**

Define a `CalendarProviderClient` abstraction with `hasReadWritePermission()`, `writableCalendars()`, `insertEvent`, `updateEvent`, `replaceAlertReminder`, and `deleteEvent`. Its Android implementation must:

```kotlin
values.put(CalendarContract.Events.CALENDAR_ID, calendarId)
values.put(CalendarContract.Events.TITLE, "Daily｜${request.title}")
values.put(CalendarContract.Events.DTSTART, request.startAt.toEpochMilli())
values.put(CalendarContract.Events.DTEND, request.endAt.toEpochMilli())
values.put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
values.put(CalendarContract.Events.DESCRIPTION, request.description)
```

Use `CalendarContract.Reminders.METHOD_ALERT`; select the first writable primary calendar, otherwise the first writable visible calendar. Catch `SecurityException`, `IllegalArgumentException`, and provider null-URI responses as a `ProviderFailure` with a short Chinese diagnostic message.

- [ ] **Step 4: Run the gateway tests to verify they pass**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Add the permission declaration**

Add `android.permission.READ_CALENDAR` and `android.permission.WRITE_CALENDAR` to `AndroidManifest.xml`; do not request them at application launch.

## Task 3: Coordinate mappings and route ordinary schedule events

**Files:**
- Create: `app/src/main/java/com/daily/life/core/calendar/CalendarReminderSyncer.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleRepository.kt`
- Modify: `app/src/main/java/com/daily/life/core/AppContainer.kt`
- Create: `app/src/test/java/com/daily/life/core/calendar/CalendarReminderSyncerTest.kt`
- Modify: `app/src/test/java/com/daily/life/feature/schedule/ScheduleViewModelTest.kt`

**Interfaces:**
- Produces `CalendarReminderSyncer.syncSchedule(event: ScheduleEvent): CalendarGatewayResult` and `deleteSchedule(eventId: Long): CalendarGatewayResult`.
- Produces `ReminderRouting.isBirthday(event: ScheduleEvent): Boolean` and `ReminderRouting.usesCalendarMessage(event): Boolean`.
- `RoomScheduleRepository` receives an optional `CalendarReminderSyncer` and exposes `lastReminderStatus` as `SCHEDULED` only when the chosen executor accepted the reminder.

- [ ] **Step 1: Write failing routing tests**

Add tests covering the user’s fixed rule:

```kotlin
@Test
fun birthdayAlwaysUsesCalendarMessageEvenWhenEditorPreviouslySelectedAlarm() {
    assertTrue(ReminderRouting.usesCalendarMessage(birthdayEvent(reminderMode = ReminderMode.ALARM)))
}

@Test
fun ordinaryAlarmUsesDailyAlarmAndDoesNotCallCalendarSyncer() = runTest {
    repository.create(ordinaryEvent(reminderMode = ReminderMode.ALARM))
    assertEquals(0, calendarSyncer.syncCalls)
    assertEquals(1, reminderScheduler.scheduleCalls)
}

@Test
fun ordinaryMessageUsesCalendarAndCancelsOldDailyLocalReminder() = runTest {
    repository.create(ordinaryEvent(reminderMode = ReminderMode.NOTIFICATION))
    assertEquals(1, calendarSyncer.syncCalls)
    assertEquals(1, reminderScheduler.cancelCalls)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.daily.life.core.calendar.CalendarReminderSyncerTest" --tests "com.daily.life.feature.schedule.ScheduleViewModelTest" --console=plain
```

Expected: compilation failure because `ReminderRouting` and `CalendarReminderSyncer` do not exist.

- [ ] **Step 3: Implement mapping-aware schedule synchronization**

Implement `ReminderRouting` so `repeatYearly == true` is treated as a birthday message route regardless of stored `ReminderMode`; ordinary notifications use the calendar; ordinary alarms use only `ReminderScheduler`. In `CalendarReminderSyncer`, look up the `SCHEDULE_EVENT` mapping by `event.id.toString()`, update that calendar event if present, otherwise insert and persist the returned event ID. On route changes, cancel the old executor before invoking the new one. On delete, remove only a mapping-owned event, then remove its mapping row.

- [ ] **Step 4: Run tests to verify they pass**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Add a repository-level regression test for provider failure**

Add and run:

```kotlin
@Test
fun providerFailureKeepsDailyEventAndReportsPermissionRestrictedStatus() = runTest {
    calendarSyncer.nextResult = CalendarGatewayResult.NoWritableCalendar
    repository.create(ordinaryEvent(reminderMode = ReminderMode.NOTIFICATION))

    assertNotNull(repository.findById(1L))
    assertEquals(ReminderScheduleStatus.PERMISSION_RESTRICTED, repository.lastReminderStatus)
}
```

Expected: Daily data remains queryable and the UI has a non-success status.

## Task 4: Synchronize future course occurrences to calendar alerts

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/notification/ReminderScheduler.kt`
- Modify: the concrete timetable repository under `app/src/main/java/com/daily/life/feature/timetable/`
- Modify: `app/src/main/java/com/daily/life/core/calendar/CalendarReminderSyncer.kt`
- Create: `app/src/test/java/com/daily/life/core/calendar/CourseCalendarReminderSyncerTest.kt`

**Interfaces:**
- Produces `CourseOccurrenceKey.of(courseId: Long, week: Int): String` with value `"<courseId>:<week>"`.
- Produces `CalendarReminderSyncer.syncFutureCourseOccurrences(semesterId: Long, now: Instant, reminderMinutes: Int): List<CalendarGatewayResult>`.
- Produces `CalendarReminderSyncer.removeCourseOccurrences(semesterId: Long): List<CalendarGatewayResult>`.

- [ ] **Step 1: Write failing course synchronization tests**

```kotlin
@Test
fun syncCreatesOneAlertForEachFutureRealCourseWeek() = runTest {
    syncer.syncFutureCourseOccurrences(semesterId = 3, now = instant("2026-09-15T00:00:00Z"), reminderMinutes = 20)

    assertEquals(setOf("11:2", "11:4"), gateway.insertedOwnerKeys)
    assertEquals(20, gateway.insertedReminderMinutesByOwner["11:2"])
}

@Test
fun disabledCourseReminderCreatesNoCalendarEvent() = runTest {
    syncer.syncFutureCourseOccurrences(semesterId = 3, now = now, reminderMinutes = 20)
    assertTrue(gateway.insertedOwnerKeys.isEmpty())
}
```

- [ ] **Step 2: Run the course tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.daily.life.core.calendar.CourseCalendarReminderSyncerTest" --console=plain
```

Expected: compilation failure because the course sync API does not exist.

- [ ] **Step 3: Implement deterministic course occurrence generation**

Reuse the existing semester start-date plus course week/day/period calculation from `AndroidReminderScheduler`. For every future course occurrence whose `CourseReminderMode` resolves to a minute count, create/update a `COURSE_OCCURRENCE` mapping with key `"$courseId:$week"`; use title `课程：<courseName>` and description containing teacher/location. Delete stale course mapping events for the affected semester before creating the current future set. Do not call `AlarmManager` for course messages after successful calendar sync.

- [ ] **Step 4: Run the course tests to verify they pass**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Run existing course reminder tests for regressions**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.daily.life.feature.timetable.CourseReminderPolicyTest" --tests "com.daily.life.core.notification.ReminderSchedulerTest" --console=plain
```

Expected: PASS; the policy tests still enforce global/custom/disabled minute selection.

## Task 5: Request permissions on demand and expose truthful status

**Files:**
- Modify: `app/src/main/java/com/daily/life/MainActivity.kt`
- Modify: `app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleViewModel.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleEditorScreen.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt`
- Modify: `app/src/main/java/com/daily/life/feature/settings/SettingsScreen.kt`
- Create: `app/src/test/java/com/daily/life/feature/schedule/CalendarPermissionPromptPolicyTest.kt`

**Interfaces:**
- Produces `CalendarPermissionPromptPolicy.requiresPrompt(hasPermission: Boolean, event: ScheduleEvent): Boolean`.
- Adds `onRequestCalendarPermission: () -> Unit` and `onCalendarPermissionResult(granted: Boolean)` to navigation/ViewModel callbacks.
- Adds settings action `重新同步未来提醒` which calls the syncer only when calendar permission is granted.

- [ ] **Step 1: Write failing permission-prompt tests**

```kotlin
@Test
fun birthdayRequiresCalendarPermissionPromptWhenNotGranted() {
    assertTrue(CalendarPermissionPromptPolicy.requiresPrompt(false, birthdayEvent()))
}

@Test
fun ordinaryAlarmDoesNotRequestCalendarPermission() {
    assertFalse(CalendarPermissionPromptPolicy.requiresPrompt(false, ordinaryEvent(ReminderMode.ALARM)))
}
```

- [ ] **Step 2: Run the permission policy tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.daily.life.feature.schedule.CalendarPermissionPromptPolicyTest" --console=plain
```

Expected: compilation failure because the policy does not exist.

- [ ] **Step 3: Implement the on-demand permission and status flow**

Register an `ActivityResultContracts.RequestMultiplePermissions` launcher for `READ_CALENDAR` and `WRITE_CALENDAR` in `MainActivity`. Invoke it only after the user saves a calendar-routed event or enables a course reminder; return the grant result to the ViewModel, which runs the pending sync once after permission is granted. Show exactly one of these user-facing states:

```text
日程已保存，已同步至系统日历
日程已保存；未授权系统日历，锁屏消息不会由日历执行
日程已保存；没有可写的系统日历
日程已保存；系统日历同步失败
日程已保存，使用 Daily 闹钟
```

Hide or disable the alarm selector for yearly birthday events; leave the selector unchanged for ordinary events. The course screen must identify its reminder as `消息提醒`.

- [ ] **Step 4: Run the permission policy and ViewModel tests to verify they pass**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.daily.life.feature.schedule.CalendarPermissionPromptPolicyTest" --tests "com.daily.life.feature.schedule.ScheduleViewModelTest" --tests "com.daily.life.feature.timetable.TimetableViewModelTest" --console=plain
```

Expected: PASS.

- [ ] **Step 5: Build the debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

Expected: `BUILD SUCCESSFUL` and a fresh `app-debug.apk` in the configured Gradle output directory.

## Task 6: End-to-end regression verification and device acceptance

**Files:**
- Modify only if needed by failed tests: the exact files named in Tasks 1–5.
- Update: `docs/qa/system-calendar-reminders.md` with the actual device-test results and screenshots supplied by the user.

- [ ] **Step 1: Run all relevant reminder and database tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.daily.life.core.database.DailyDatabaseMigrationTest" --tests "com.daily.life.core.calendar.*" --tests "com.daily.life.core.notification.ReminderSchedulerTest" --tests "com.daily.life.feature.schedule.*" --tests "com.daily.life.feature.timetable.*" --console=plain
```

Expected: PASS. Investigate any failure before changing unrelated code.

- [ ] **Step 2: Package and hand off the APK**

Provide the absolute APK file path and explain that upgrading does not automatically synchronize pre-existing events until the user invokes `重新同步未来提醒` or edits/saves the event again.

- [ ] **Step 3: Perform the user’s iQOO 13 acceptance test**

Ask the user to grant the two calendar permissions, create a message reminder ten minutes ahead with a one-minute offset, verify the `Daily｜` event in the system calendar, return to Home and lock the screen, and report whether the system calendar notification appears. Then test one course reminder, one birthday, and one ordinary Daily alarm separately.

- [ ] **Step 4: Record only observed results**

Write the device date, result, and screenshots to `docs/qa/system-calendar-reminders.md`. If system calendar messaging passes but Daily alarms remain blocked, report that as a distinct unresolved limitation rather than calling the whole reminder feature fixed.
