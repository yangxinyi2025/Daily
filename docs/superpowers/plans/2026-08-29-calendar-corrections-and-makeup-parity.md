# Calendar Corrections and Makeup Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct holiday/makeup classification, expose manual date correction, show a one-time battery guide, and require weekday plus parity for makeup-course mapping.

**Architecture:** Calendar parsing is conservative and holiday-first, with manual overrides above all sources. A DataStore flag drives a root Compose dialog. Makeup adjustments grow from weekday-only to weekday-and-parity, and timetable display/reminder logic resolves the selected parity to the nearest valid source week.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, DataStore Preferences, coroutines, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-08-29-calendar-corrections-and-makeup-parity-design.md`

## Global Constraints

- A holiday wins over a non-manual makeup marker for the same date.
- Bare `调休` never marks a date as makeup work.
- The first-launch guide is acknowledged once for either action.
- Makeup mapping requires both weekday and `WeekParity`; it is never inferred.
- Existing null-parity adjustments schedule neither makeup courses nor reminders.

---

### Task 1: Fix calendar classification and source conflicts

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/calendar/SystemCalendarScheduleParser.kt`
- Modify: `app/src/main/java/com/daily/life/core/calendar/HolidayCalendarRepository.kt`
- Test: `app/src/test/java/com/daily/life/core/calendar/IcsCalendarParserTest.kt`
- Test: `app/src/test/java/com/daily/life/core/calendar/HolidayCalendarRepositoryTest.kt`

**Produces:** holiday-first `systemCalendarSpecialDayKindFor` and a merger that chooses `HOLIDAY_REST` before `MAKEUP_WORKDAY`.

- [ ] Write failing tests:
  ```kotlin
  assertEquals(Holiday, systemCalendarSpecialDayKindFor("国庆放假（调休安排）", null))
  assertNull(systemCalendarSpecialDayKindFor("国庆调休安排", null))
  assertEquals(CalendarDayKind.HOLIDAY_REST, merged.single().kind)
  ```
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.core.calendar.IcsCalendarParserTest --tests com.daily.life.core.calendar.HolidayCalendarRepositoryTest --console=plain`; verify failure.
- [ ] Implement explicit `isHoliday` and `isMakeup` predicates; check holiday first and restrict makeup to `补班`, `调休上班`, or the explicit source-day pattern.
- [ ] Update `CalendarDayRuleMerger.chooseLayer` to prefer `HOLIDAY_REST` among non-manual candidates.
- [ ] Re-run the command; verify PASS.
- [ ] Commit: `git commit -m "fix: prefer holidays over ambiguous makeup markers"`.

### Task 2: Surface manual date correction

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt`
- Test: `app/src/test/java/com/daily/life/feature/schedule/ScheduleMonthScreenTest.kt`

**Consumes:** existing `onSaveCalendarDayOverride` and `onClearCalendarDayOverrides` callbacks.

- [ ] Write failing Compose/Robolectric assertions:
  ```kotlin
  onNodeWithText("修正日期状态").assertIsDisplayed()
  onNodeWithText("修正日期状态").performClick()
  onNodeWithText("设为调休上班").assertIsDisplayed()
  ```
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleMonthScreenTest --console=plain`; verify the visible-entry assertion fails.
- [ ] Replace the subtle clickable `修改这一天` text with an `OutlinedButton` labelled `修正日期状态`; label its collapsed form `收起日期修正`.
- [ ] Change the work action to save `CalendarDayKind.MAKEUP_WORKDAY`, label it `设为调休上班`, and retain `设为休息日` and `恢复系统日历`.
- [ ] Re-run the screen test; verify PASS.
- [ ] Commit: `git commit -m "fix: expose manual calendar status correction"`.

### Task 3: Show a one-time battery guide at launch

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/datastore/DailyPreferences.kt`
- Create: `app/src/main/java/com/daily/life/core/system/BackgroundRuntimeGuide.kt`
- Modify: `app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt`
- Test: `app/src/test/java/com/daily/life/core/datastore/DailyPreferencesTest.kt`
- Test: `app/src/test/java/com/daily/life/core/system/BackgroundRuntimeGuideTest.kt`

**Produces:** `backgroundRuntimeGuideAcknowledged: Flow<Boolean>`, `setBackgroundRuntimeGuideAcknowledged(Boolean)`, and `appDetailsIntent(packageName)`.

- [ ] Write failing tests:
  ```kotlin
  assertFalse(preferences.backgroundRuntimeGuideAcknowledged.first())
  preferences.setBackgroundRuntimeGuideAcknowledged(true)
  assertTrue(preferences.backgroundRuntimeGuideAcknowledged.first())
  assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appDetailsIntent("pkg").action)
  ```
- [ ] Run the two test classes; verify missing API failure.
- [ ] Add the DataStore key and setter. Add an `AlertDialog` with the exact path `电池 → 后台耗电管理 → 允许后台耗电`, `去设置`, and `稍后`.
- [ ] Collect the flag in `DailyNavHost`; acknowledge before either dismissing or opening the App Info intent.
- [ ] Re-run the tests; verify PASS.
- [ ] Commit: `git commit -m "feat: show one-time background runtime guide"`.

### Task 4: Persist weekday and parity for makeup selections

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/database/DailyDatabase.kt`
- Modify: `app/src/main/java/com/daily/life/core/database/DailyEntities.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableCalendarAdjustmentState.kt`
- Test: `app/src/test/java/com/daily/life/core/database/DailyDatabaseMigrationTest.kt`
- Test: `app/src/test/java/com/daily/life/feature/timetable/TimetableImportAdjustmentTest.kt`

**Produces:** nullable entity field `sourceWeekParity: WeekParity?`, UI selection field `selectedSourceWeekParity`, and non-null importer input parity.

- [ ] Write failing tests:
  ```kotlin
  assertFalse(adjustmentChoicesAreComplete(listOf(choice.copy(selectedSourceDayOfWeek = 5))))
  assertTrue(adjustmentChoicesAreComplete(listOf(choice.copy(selectedSourceDayOfWeek = 5, selectedSourceWeekParity = WeekParity.ODD))))
  ```
  Also migrate a version-10 fixture and assert `sourceWeekParity == null`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableImportAdjustmentTest --tests com.daily.life.core.database.DailyDatabaseMigrationTest --console=plain`; verify failure.
- [ ] Bump Room to version 11 and add `ALTER TABLE semester_calendar_adjustments ADD COLUMN sourceWeekParity TEXT` in `MIGRATION_10_11`.
- [ ] Thread parity through models and persistence without using feed metadata.
- [ ] Re-run migration/import tests; verify PASS.
- [ ] Commit: `git commit -m "feat: store makeup course week parity"`.

### Task 5: Apply weekday-plus-parity to timetable and reminders

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableImportScreen.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableCalendarMapping.kt`
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt`
- Test: `app/src/test/java/com/daily/life/feature/timetable/TimetableCalendarMappingTest.kt`
- Test: `app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt`

**Produces:** `MakeupCourseSource(dayOfWeek: Int, parity: WeekParity)` and `resolveWeekForParity(actualWeek, parity, semesterWeekCount)`.

- [ ] Write failing tests:
  ```kotlin
  assertTrue(slot.needsMakeupConfirmation)
  assertEquals(9, resolveWeekForParity(actualWeek = 10, parity = WeekParity.ODD, semesterWeekCount = 20))
  ```
  Verify an empty adjustment map yields no makeup occurrence, while a confirmed odd/even source selects the matching course week.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableCalendarMappingTest --tests com.daily.life.feature.timetable.TimetableRepositoryTest --console=plain`; verify failure.
- [ ] Add weekday and parity pickers for every makeup date; `canConfirm` requires both.
- [ ] Resolve a finite nearest valid week of requested parity; use it for display and the same course occurrences in reminder synchronization.
- [ ] Keep legacy null-parity rows unconfirmed and unscheduled.
- [ ] Re-run the focused timetable tests; verify PASS.
- [ ] Commit: `git commit -m "feat: apply user-selected makeup weekday and parity"`.

### Task 6: Integrate and build

**Files:**
- Test: calendar, schedule, DataStore, database, and timetable tests listed above.

- [ ] Run:
  ```powershell
  .\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.core.calendar.IcsCalendarParserTest --tests com.daily.life.core.calendar.HolidayCalendarRepositoryTest --tests com.daily.life.core.system.BackgroundRuntimeGuideTest --tests com.daily.life.feature.schedule.ScheduleMonthScreenTest --tests com.daily.life.feature.timetable.TimetableCalendarMappingTest --tests com.daily.life.feature.timetable.TimetableImportAdjustmentTest --tests com.daily.life.feature.timetable.TimetableRepositoryTest --console=plain
  ```
  Expected: `BUILD SUCCESSFUL`.
- [ ] Run `.\gradlew.bat --no-daemon :app:assembleDebug --console=plain`; verify `BUILD SUCCESSFUL` and the debug APK.
- [ ] Commit design and plan documents: `git commit -m "docs: plan calendar corrections and makeup parity"`.
