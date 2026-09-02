# Daily 经期记录与课程提醒 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 替换健康活动读取为 30 天周期经期记录，并实现可靠的课程和日程提醒。

**Architecture:** Room 新增经期记录与课程提醒覆盖字段，DataStore 保存 30 天周期和全局提醒。统一的 `AndroidReminderScheduler` 为日程与每次课程实际发生时间注册稳定的精确闹钟，并在开机/时间变更后重建。

**Tech Stack:** Kotlin, Compose, Room, DataStore, AlarmManager, JUnit/Robolectric, adb.

**Spec:** `docs/superpowers/specs/2026-08-22-daily-menstrual-course-reminders-design.md`

## Global Constraints

- 经期记录只含开始日、结束日；默认周期固定为 30 天。
- 数据库只做向前迁移，不能删除现有用户数据。
- 课程提醒采用全局默认、单课跟随/关闭/自定义三级规则。
- 缺少通知或精确闹钟权限时必须明确提示，禁止静默降级。

---

### Task 1: 经期数据和预测

**Files:**
- Modify: `core/database/DailyEntities.kt`, `DailyDaos.kt`, `DailyDatabase.kt`, `core/datastore/DailyPreferences.kt`
- Create: `feature/health/PeriodModels.kt`, `feature/health/PeriodRepository.kt`
- Test: `test/feature/health/PeriodPredictionTest.kt`, `test/core/database/DailyDatabaseMigrationTest.kt`

**Interfaces:**

```kotlin
data class PeriodRecord(val id: Long = 0, val startDate: LocalDate, val endDate: LocalDate)
object PeriodPredictionCalculator {
    fun nextStartDate(records: List<PeriodRecord>, cycleDays: Int): LocalDate?
}
```

- [ ] Write the failing prediction test.

```kotlin
assertEquals(
    LocalDate.of(2026, 8, 31),
    PeriodPredictionCalculator.nextStartDate(
        listOf(PeriodRecord(startDate = LocalDate.of(2026, 8, 1), endDate = LocalDate.of(2026, 8, 5))),
        cycleDays = 30
    )
)
```

- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.health.PeriodPredictionTest --console=plain`; expect missing production types.
- [ ] Add `period_records`, `PeriodDao`, database version 4 and `MIGRATION_3_4`; validate `endDate >= startDate` and clamp custom cycle days to 15..90.
- [ ] Add nullable `menstrual_cycle_days` preference that defaults to 30.
- [ ] Re-run the prediction and migration tests; expect PASS.
- [ ] Commit only Task 1 files with `feat: store menstrual periods`.

### Task 2: 健康页替换

**Files:**
- Modify: `feature/health/HealthRepository.kt`, `HealthViewModel.kt`, `HealthScreen.kt`, `core/AppContainer.kt`, `AndroidManifest.xml`
- Create: `feature/health/PeriodScreen.kt`
- Test: `test/feature/health/HealthViewModelTest.kt`

**Interfaces:**

```kotlin
data class HealthState(
    val periodRecords: List<PeriodRecord> = emptyList(),
    val nextPeriodStart: LocalDate? = null
)
fun recordPeriod(startDate: LocalDate, endDate: LocalDate)
```

- [ ] Write a failing ViewModel test that saves 2026-08-01..05 and observes `nextPeriodStart == 2026-08-31`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.health.HealthViewModelTest --console=plain`; expect failure for missing period state/API.
- [ ] Replace `Activity` tab with `经期`; build a Chinese date-picker editor, list entries, edit/delete action, latest-period card and empty state.
- [ ] Remove Health Connect and sensor factory creation, phone-health read controls and unused health/activity-recognition permissions. Keep legacy activity database tables intact. Convert the monthly report copy to weight-only.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.health --console=plain`; expect PASS.
- [ ] Commit only Task 2 files with `feat: replace activity with period tracking`.

### Task 3: 课表三行节次

**Files:**
- Modify: `feature/timetable/TimetableModels.kt`, `TimetableScreen.kt`
- Test: `test/feature/timetable/TimetablePeriodLabelsTest.kt`

- [ ] Change the failing expectation to `"1\\n08:00\\n08:45"` and `"12\\n20:10\\n20:55"`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetablePeriodLabelsTest --console=plain`; expect failure.
- [ ] Store each label in three lines, reduce only `TIME_COLUMN_WIDTH`, and use a legible small line height without clipping.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable --console=plain`; expect PASS.
- [ ] Commit only Task 3 files with `fix: compact timetable period labels`.

### Task 4: 全局与单课课前提醒

**Files:**
- Modify: `core/datastore/DailyPreferences.kt`, `core/database/DailyEntities.kt`, `DailyDaos.kt`, `DailyDatabase.kt`
- Modify: `feature/settings/SettingsState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`
- Modify: `feature/timetable/TimetableModels.kt`, `TimetableRepository.kt`, `TimetableScreen.kt`, `TimetableViewModel.kt`
- Create: `feature/timetable/CourseReminderPolicy.kt`
- Test: `test/feature/timetable/CourseReminderPolicyTest.kt`

**Interfaces:**

```kotlin
sealed interface CourseReminderOverride {
    data object FollowGlobal : CourseReminderOverride
    data object Disabled : CourseReminderOverride
    data class Custom(val minutesBefore: Int) : CourseReminderOverride
}
object CourseReminderPolicy {
    fun resolve(globalMinutes: Int?, override: CourseReminderOverride): Int?
}
```

- [ ] Write a failing policy test: global 10 + custom 25 returns 25; disabled returns null; follow returns 10.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.CourseReminderPolicyTest --console=plain`; expect missing types.
- [ ] Add nullable global minutes preference and database version 5 course override fields. `null` global means closed; custom minutes must be 1..180.
- [ ] Add settings choices closed/5/10/15/20/30/custom; add course detail choices follow/closed/custom. Each save must request scheduler re-registration.
- [ ] Run settings and policy tests; expect PASS.
- [ ] Commit only Task 4 files with `feat: configure course reminders`.

### Task 5: 可靠后台调度

**Files:**
- Modify: `core/notification/ReminderScheduler.kt`, `ReminderReceiver.kt`, `BootReceiver.kt`
- Modify: `feature/timetable/TimetableRepository.kt`, `feature/schedule/ScheduleViewModel.kt`, `ScheduleScreen.kt`
- Create: `feature/timetable/CourseReminderPlanner.kt`
- Test: `test/core/notification/ReminderSchedulerTest.kt`, `test/feature/timetable/CourseReminderScheduleTest.kt`

**Interfaces:**

```kotlin
data class CourseReminderRequest(val requestId: Int, val title: String, val triggerAt: Instant)
object CourseReminderPlanner {
    fun futureRequests(
        semesterStartDate: LocalDate,
        courses: List<CourseEntity>,
        periodStarts: Map<Int, LocalTime>,
        globalMinutes: Int?,
        now: Instant,
        zone: ZoneId
    ): List<CourseReminderRequest>
}
```

- [ ] Write failing tests: week-1 Monday period 1, ten minutes early, yields 07:50 local; a notification reminder without exact-alarm access is `PERMISSION_RESTRICTED`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.core.notification.ReminderSchedulerTest --tests com.daily.life.feature.timetable.CourseReminderScheduleTest --console=plain`; expect failure because course scheduling is a no-op.
- [ ] Implement stable request IDs per course/week/date, cancel obsolete requests, and register only future requests through `setExactAndAllowWhileIdle`.
- [ ] Make notification-mode reminders require notification plus exact-alarm permission. Return the actual registered trigger time/count to the UI; show an authorization action if anything is blocked.
- [ ] Extend boot/time-change rebuilding to include courses as well as normal schedule events.
- [ ] Re-run notification and course schedule tests; expect PASS.
- [ ] Commit only Task 5 files with `feat: schedule reliable course reminders`.

### Task 6: Emulator QA and delivery

**Files:** none unless a reproduced defect requires a minimal follow-up.

- [ ] Build `./gradlew :app:assembleDebug --console=plain` and install with `adb -s emulator-5554 install -r <debug-apk>`.
- [ ] Confirm Health has no Health Connect read action; add a period and inspect the 30-day prediction.
- [ ] Confirm timetable labels have no clipping at first and twelfth periods.
- [ ] Enable both system permissions, create future message and course reminders, inspect `adb shell dumpsys alarm`, press Home without force-stop, then inspect `adb shell dumpsys notification --noredact` after delivery.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.health --tests com.daily.life.feature.timetable --tests com.daily.life.core.notification --console=plain`; expect PASS.
- [ ] Commit the plan with `docs: plan menstrual tracking and course reminders` only if not already committed.
