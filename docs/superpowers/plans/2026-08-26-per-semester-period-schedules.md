# Per-Semester Period Schedules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Let every semester own editable 1–12 period times that drive timetable labels and future course calendar-message timing.

**Architecture:** Keep CourseEntity startPeriod and endPeriod unchanged. Add a child Room table holding LocalTime pairs for each semester, expose resolved schedules through the timetable repository, and use them from import, rendering, and calendar synchronization.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Kotlin Flow, PDFBox Android, Robolectric, Android emulator/adb.

**Spec:** docs/superpowers/specs/2026-08-26-per-semester-period-schedules.md

## Global Constraints

- Preserve all existing course data, the portrait timetable grid, and the three-line period labels.
- Store exactly 12 period rows per semester; do not share a global timetable.
- Course reminders remain system-calendar message reminders. Do not add a Daily alarm path.
- Never describe fallback defaults as times read from a PDF.
- Migrate Room from database version 6 to 7 without changing existing courses.

---

### Task 1: Build and test the period-schedule domain

**Files:**

- Create: app/src/main/java/com/daily/life/feature/timetable/SemesterPeriodSchedule.kt
- Create: app/src/test/java/com/daily/life/feature/timetable/SemesterPeriodScheduleTest.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetablePeriodLabelsTest.kt

**Interfaces:**

- SemesterPeriodTime(period: Int, startTime: LocalTime, endTime: LocalTime)
- defaultSemesterPeriodTimes(): List<SemesterPeriodTime>
- validateSemesterPeriodTimes(times: List<SemesterPeriodTime>): String?
- periodLabels(times: List<SemesterPeriodTime>): List<TimetablePeriodLabel>

- [ ] Write a failing test verifying default labels for periods 1, 2, and 12 are 1 / 08:00 / 08:45, 2 / 08:50 / 09:35, and 12 / 20:10 / 20:55.
- [ ] Run ./gradlew testDebugUnitTest --tests com.daily.life.feature.timetable.SemesterPeriodScheduleTest. Expected: compilation failure because the new model does not exist.
- [ ] Implement the 12 current Daily time pairs, label formatting, and validation requiring periods 1 through 12, start before end, and non-overlapping chronological rows.
- [ ] Add failing tests for end-before-start, overlapping periods, and valid defaults. Implement only the checks required to pass them.
- [ ] Run ./gradlew testDebugUnitTest --tests com.daily.life.feature.timetable.SemesterPeriodScheduleTest --tests com.daily.life.feature.timetable.TimetablePeriodLabelsTest. Expected: PASS.
- [ ] Commit only Task 1 files: git commit -m feat: define semester period schedules.

### Task 2: Add safe Room persistence and migration

**Files:**

- Modify: app/src/main/java/com/daily/life/core/database/DailyEntities.kt
- Modify: app/src/main/java/com/daily/life/core/database/DailyConverters.kt
- Modify: app/src/main/java/com/daily/life/core/database/DailyDaos.kt
- Modify: app/src/main/java/com/daily/life/core/database/DailyDatabase.kt
- Modify: app/src/test/java/com/daily/life/core/database/DailyDatabaseMigrationTest.kt
- Create: app/src/test/java/com/daily/life/feature/timetable/SemesterPeriodRepositoryTest.kt

**Interfaces:**

- SemesterPeriodEntity(semesterId: Long, period: Int, startTime: LocalTime, endTime: LocalTime)
- SemesterPeriodDao observeBySemester, rowsForSemester, and replaceForSemester methods.
- DailyDatabase MIGRATION_6_7 and database version 7.

- [ ] Write a failing migration test that inserts a version-6 semester/course, applies migration 6 to 7, and verifies the course is retained and semester_periods exists.
- [ ] Run ./gradlew testDebugUnitTest --tests com.daily.life.core.database.DailyDatabaseMigrationTest.migration6To7PreservesCoursesAndCreatesThePeriodTable. Expected: compilation failure because migration 6 to 7 is absent.
- [ ] Implement entity, LocalTime converters, DAO, database registration, and a migration that creates only semester_periods with composite semesterId/period key and cascade foreign key.
- [ ] Write a failing repository test: loading an old semester with no rows returns and stores all 12 defaults.
- [ ] Implement lazy default insertion in a Room transaction and validated replace for only one semester.
- [ ] Run ./gradlew testDebugUnitTest --tests com.daily.life.core.database.DailyDatabaseMigrationTest --tests com.daily.life.feature.timetable.SemesterPeriodRepositoryTest. Expected: PASS.
- [ ] Commit only Task 2 files: git commit -m feat: persist per-semester class times.

### Task 3: Parse and review PDF period times

**Files:**

- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/PdfTimetableParser.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableImportScreen.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/PdfTimetableParserTest.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableViewModelTest.kt

**Interfaces:**

- TimetableParseResult gains parsedPeriodTimes: Map<Int, SemesterPeriodTime>.
- TimetableImportState gains editable period-times rows and a periodTimesDetectedFromPdf flag.
- TimetableViewModel gains updateImportPeriodTime(period, start, end).

- [ ] Write failing parser tests for the forms 第1节 08:00-08:45, 2节 08:50～09:35, and 第3节 09:50 至 10:35; malformed 25:00-08:45 is ignored.
- [ ] Run ./gradlew testDebugUnitTest --tests com.daily.life.feature.timetable.PdfTimetableParserTest. Expected: compilation failure because parsed period data is absent.
- [ ] Implement bounded extraction requiring a nearby period number from 1 to 12 and an accepted separator. Keep only valid start-before-end pairs for layout and text parsing.
- [ ] Write a failing ViewModel test: parsed period 1 overrides its default, unparsed period 2 keeps its default, and the detection flag is true.
- [ ] Implement a 节次时间核对 card before course preview cards. It shows all 12 editable HH:mm rows, uses the truthful no-time warning, and disables confirmation when invalid.
- [ ] Run ./gradlew testDebugUnitTest --tests com.daily.life.feature.timetable.PdfTimetableParserTest --tests com.daily.life.feature.timetable.TimetableViewModelTest. Expected: PASS.
- [ ] Commit only Task 3 files: git commit -m feat: review class times during PDF import.

### Task 4: Use saved times in timetable and calendar synchronization

**Files:**

- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableViewModelTest.kt

**Interfaces:**

- TimetableRepository gains observePeriodTimes(semesterId) and updatePeriodTimes(semesterId, times).
- confirmImport receives periodTimes.
- TimetableState timeLabels comes from saved semester times.

- [ ] Write a failing repository test importing a period-1 course with a changed 09:10 start, then assert the system-calendar request uses 09:10 rather than the old 08:00.
- [ ] Run that focused test. Expected: FAIL because current code uses hard-coded timestamps.
- [ ] Persist courses and selected period rows in one import transaction. Replace hard-coded course time lists with the resolved semester schedule in future calendar sync.
- [ ] Write a failing ViewModel test proving a saved 08:10 to 08:55 first period renders a 1 / 08:10 / 08:55 label.
- [ ] Combine observed period rows with courses while creating TimetableState. Legacy semesters obtain lazy defaults first.
- [ ] Run ./gradlew testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableRepositoryTest --tests com.daily.life.feature.timetable.TimetableViewModelTest. Expected: PASS.
- [ ] Commit only Task 4 files: git commit -m feat: apply semester class times to courses.

### Task 5: Add the post-import editor and verify Android behaviour

**Files:**

- Create: app/src/main/java/com/daily/life/feature/timetable/TimetablePeriodEditorScreen.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableViewModelTest.kt

**Interfaces:**

- TimetableState gains TimetablePeriodEditorState.
- TimetableViewModel gains openPeriodEditor, updatePeriodEditorRow, restoreDefaultPeriodTimes, savePeriodTimes, and closePeriodEditor.

- [ ] Write a failing ViewModel test that opens the editor with saved values and verifies reset restores period 1 to 08:00–08:45.
- [ ] Run the test. Expected: compilation failure because editor state/actions are absent.
- [ ] Implement a scrollable existing-Daily-style editor: 12 rows, validation text, 恢复默认时间, 保存, and 取消. Add a compact 节次时间 top-bar action next to import; do not alter the bottom navigation.
- [ ] Write a failing repository test that saving changed times deletes future course calendar events then creates new ones at changed times.
- [ ] Implement guarded re-sync: any cleanup/create failure reports an error and leaves saved rows unchanged. Do not add alarm permissions, receivers, services, or settings deep links.
- [ ] Run ./gradlew testDebugUnitTest --tests com.daily.life.feature.timetable.* and then ./gradlew testDebugUnitTest. Expected: PASS with zero failures.
- [ ] Run ./gradlew assembleDebug, install on emulator, import the synthetic PDF, edit period 1 during import, verify the timetable label, reopen 节次时间, and capture docs/qa/timetable-period-schedule-editor.png.
- [ ] Commit only Task 5 files and QA screenshot: git commit -m feat: edit timetable period times.

## Plan Self-Review

- All approved requirements are covered: migration/defaults, PDF parsing with truthful fallback, import review, persistent UI editing, timetable labels, future calendar messages, and emulator QA.
- Type names remain consistent: SemesterPeriodTime is domain/UI, SemesterPeriodEntity is Room, and CourseEntity retains integer period bounds.
- The plan includes no alarm path and no undefined deferred work.
