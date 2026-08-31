# Timetable Course Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Let users add, edit, delete, validate, and visually distinguish current-semester courses directly in the timetable, without changing import or calendar-day business logic.

**Architecture:** A small course-editor domain model validates input and detects conflicts. The repository remains the sole database writer and refreshes the existing Android system-calendar course reminders. The ViewModel owns temporary dialog state; Compose collects input and renders the timetable.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, Flow/coroutines, JUnit/Robolectric, Android Calendar provider.

**Spec:** docs/superpowers/specs/2026-08-31-timetable-course-management-design.md

## Global Constraints

- Reuse CourseEntity and course_weeks; do not create a table or a new manual-course type.
- Limit manual operations to the current semester.
- Leave PDF import, period settings, semesters, holidays, adjustments, and class overrides unchanged.
- Parse weeks with WeekRuleParser, including 1-16周, 1-16周 单周, and 2,4,6周.
- Reject a save only when weekday, parsed weeks, and inclusive lesson intervals all overlap; an edit excludes its own ID.
- Cards show only course name and location. Teachers remain editable, not permanently rendered.
- Use the exact palette: Mon purple, Tue green, Wed pink, Thu blue, Fri purple, Sat green, Sun pink.
- Reuse the project’s Android system-calendar course reminders. Do not introduce exact-alarm course reminders.
- Calendar synchronization happens after the Room transaction. On failure, keep the saved course, show “课程已保存，提醒同步失败”, and allow retry.
- Use TDD. Run focused tests before each commit and all timetable tests before delivery.

---

## File Structure

- Create app/src/main/java/com/daily/life/feature/timetable/TimetableCourseEditor.kt: draft model, field validation, conflict rule, color roles.
- Modify app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt: editor, delete-confirmation, and sync-notice state.
- Modify app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt: atomic save/delete and reminder retry.
- Modify app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt: state transitions and repository events.
- Modify app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt: cell click layer, central dialogs, two-line card content and palette.
- Modify app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt: ViewModel callback binding.
- Create app/src/test/java/com/daily/life/feature/timetable/TimetableCourseEditorTest.kt: validation and conflict unit tests.
- Modify app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt: persistence, conflict and reminder tests.
- Modify app/src/test/java/com/daily/life/feature/timetable/TimetableViewModelTest.kt: opening, editing, deleting, notices.
- Modify app/src/test/java/com/daily/life/feature/timetable/TimetableGridAppearanceTest.kt: weekday colors and two-line card mapping.

## Task 1: Define the editor domain and state

**Files:**
- Create: app/src/main/java/com/daily/life/feature/timetable/TimetableCourseEditor.kt
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt
- Create: app/src/test/java/com/daily/life/feature/timetable/TimetableCourseEditorTest.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableGridAppearanceTest.kt

**Interfaces:**
- Produces TimetableCourseDraft(id: Long?, semesterId: Long, courseName: String, dayOfWeek: Int, startPeriod: Int, endPeriod: Int, weekRuleText: String, location: String, teacher: String, notes: String).
- Produces CourseDraftValidation.Valid(parsedWeeks: Set<Int>) and CourseDraftValidation.Invalid(message: String) from validateTimetableCourseDraft(draft).
- Produces coursesConflict(candidate, candidateWeeks, existing, existingWeeks): Boolean.
- Produces TimetableCourseEditorState in TimetableState, containing draft, isOpen, isSaving, fieldError, syncNotice, and deleteConfirmationCourseName.
- Produces timetableCourseColorSlot(dayOfWeek): Int with Monday-Sunday values 0, 1, 2, 3, 0, 1, 2.

- [ ] **Step 1: Write the failing tests**

~~~kotlin
@Test fun validationParsesOddWeeksAndRejectsBlankName() {
    val valid = TimetableCourseDraft(null, 1, "高等数学", 1, 1, 2, "1-16周 单周", "教一 101", "", "")
    val result = validateTimetableCourseDraft(valid)
    assertEquals(setOf(1, 3, 5, 7, 9, 11, 13, 15), (result as CourseDraftValidation.Valid).parsedWeeks)
    assertTrue(validateTimetableCourseDraft(valid.copy(courseName = "")) is CourseDraftValidation.Invalid)
}

@Test fun conflictNeedsSameDayWeeksAndPeriods() {
    val existing = CourseEntity(9, 1, "英语", 2, 3, 4, "1-8周", setOf(1,2,3,4,5,6,7,8))
    val draft = TimetableCourseDraft(null, 1, "线代", 2, 4, 5, "2-10周", "", "", "")
    assertTrue(coursesConflict(draft, setOf(2,3,4,5,6,7,8,9,10), existing, existing.parsedWeeks))
    assertFalse(coursesConflict(draft.copy(startPeriod = 5), setOf(9,10), existing, existing.parsedWeeks))
}
~~~

- [ ] **Step 2: Run the test and confirm it fails**

Run: ./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableCourseEditorTest --rerun-tasks

Expected: FAIL because the draft, validation result, validator, and conflict function are absent.

- [ ] **Step 3: Add the minimal domain implementation**

~~~kotlin
sealed interface CourseDraftValidation {
    data class Valid(val parsedWeeks: Set<Int>) : CourseDraftValidation
    data class Invalid(val message: String) : CourseDraftValidation
}

fun coursesConflict(
    candidate: TimetableCourseDraft,
    candidateWeeks: Set<Int>,
    existing: CourseEntity,
    existingWeeks: Set<Int>
): Boolean = candidate.id != existing.id &&
    candidate.semesterId == existing.semesterId &&
    candidate.dayOfWeek == existing.dayOfWeek &&
    candidateWeeks.intersect(existingWeeks).isNotEmpty() &&
    candidate.startPeriod <= existing.endPeriod &&
    existing.startPeriod <= candidate.endPeriod
~~~

Validation must require nonblank name and week text, weekday 1..7, periods 1..12, start not after end, no parser warnings, and at least one parsed week. Map colors with ((dayOfWeek - 1) % 4 + 4) % 4. Add the independent editor-state data class rather than altering import state.

- [ ] **Step 4: Run focused tests and commit**

Run: ./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableCourseEditorTest --tests com.daily.life.feature.timetable.TimetableGridAppearanceTest --rerun-tasks

Expected: PASS and palette sequence is exactly 0, 1, 2, 3, 0, 1, 2.

~~~bash
git add app/src/main/java/com/daily/life/feature/timetable/TimetableCourseEditor.kt app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt app/src/test/java/com/daily/life/feature/timetable/TimetableCourseEditorTest.kt app/src/test/java/com/daily/life/feature/timetable/TimetableGridAppearanceTest.kt
git commit -m "feat: add timetable course editor domain"
~~~

## Task 2: Save and delete courses through the existing repository

**Files:**
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt

**Interfaces:**
- Consumes TimetableCourseDraft and CourseDraftValidation from Task 1.
- Produces CourseMutationResult.Saved(courseId: Long, reminderSyncFailed: Boolean) and CourseMutationResult.Rejected(message: String).
- Adds to TimetableRepository:

~~~kotlin
suspend fun saveCourse(draft: TimetableCourseDraft): CourseMutationResult
suspend fun deleteCourse(semesterId: Long, courseId: Long): CourseMutationResult
suspend fun retryCourseReminderSync(semesterId: Long): CourseMutationResult
suspend fun findCourse(courseId: Long): CourseWithWeeks?
~~~

- [ ] **Step 1: Write failing repository tests**

~~~kotlin
@Test fun saveWritesWeeksAndRejectsOverlap() = runTest {
    val first = repository.saveCourse(draft("数学", day = 1, start = 1, end = 2, weeks = "1-4周"))
    val conflict = repository.saveCourse(draft("物理", day = 1, start = 2, end = 3, weeks = "2-6周"))
    assertTrue(first is CourseMutationResult.Saved)
    assertEquals("与“数学”在周一第 2 节冲突", (conflict as CourseMutationResult.Rejected).message)
    val id = (first as CourseMutationResult.Saved).courseId
    assertEquals(listOf(1,2,3,4), database.courseDao().findWithWeeksById(id)!!.weeks.map { it.week }.sorted())
}

@Test fun editKeepsIdAndDeleteClearsWeeks() = runTest {
    val saved = repository.saveCourse(draft("英语", day = 3, start = 3, end = 4, weeks = "1-2周")) as CourseMutationResult.Saved
    repository.saveCourse(draft("英语", id = saved.courseId, day = 3, start = 5, end = 6, weeks = "3-4周"))
    repository.deleteCourse(1, saved.courseId)
    assertEquals(null, database.courseDao().findById(saved.courseId))
    assertTrue(database.courseDao().findAllWeeks().none { it.courseId == saved.courseId })
}
~~~

Use the existing permission-denied calendar client in one test and assert Saved with reminderSyncFailed true, with the Room row still present.

- [ ] **Step 2: Run the repository tests and confirm they fail**

Run: ./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableRepositoryTest --rerun-tasks

Expected: FAIL because mutation API and result type do not exist.

- [ ] **Step 3: Implement Room transaction and conflict detection**

validateTimetableCourseDraft before database access. Fetch courses with courseDao.findBySemester(draft.semesterId), use findWithWeeksById for each normalized set, and reject the first sorted conflict. In database.withTransaction, call insert for a new draft or update for its retained ID; deleteWeeksByCourseId and insertWeeks for a save. Preserve campus, courseCode, credits, reminder mode, and reminder minutes from an edited CourseEntity.

Return this exact conflict string after selecting the overlapping first period: 与“课程名”在周X第 N 节冲突.

For delete, confirm the course belongs to semesterId and call courseDao.deleteById inside the Room transaction; Room foreign keys remove week rows. Record the deleted ID before the transaction so its external calendar occurrences can be removed afterward.

- [ ] **Step 4: Implement reminder refresh and retry**

After transaction success, use CalendarReminderSyncer.deleteCourseOccurrences for an edit/delete and syncFutureCourseCalendarMessages(semesterId) to create the future occurrences from current database state. Without CalendarReminderSyncer, invoke reminderScheduler.scheduleCourseReminders(semesterId). If any calendar result is not Synced, return Saved with reminderSyncFailed true; never undo the database row. retryCourseReminderSync removes stale occurrences for all course IDs in the semester and rebuilds future occurrences via the same helper.

- [ ] **Step 5: Run the repository tests and commit**

Run: ./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableRepositoryTest --rerun-tasks

Expected: PASS for normalized weeks, overlap rejection, edit ID retention, delete cascade, successful sync, and retryable sync failure.

~~~bash
git add app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt
git commit -m "feat: persist editable timetable courses"
~~~

## Task 3: Add ViewModel editor events and feedback

**Files:**
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableViewModelTest.kt

**Interfaces:**
- Consumes TimetableCourseEditorState and the three repository functions from Tasks 1–2.
- Produces public callbacks:

~~~kotlin
fun openNewCourse(dayOfWeek: Int, startPeriod: Int)
fun openCourseEditor(courseId: Long)
fun updateCourseDraft(draft: TimetableCourseDraft)
fun saveCourse()
fun requestCourseDelete()
fun confirmCourseDelete()
fun dismissCourseEditor()
fun retryCourseReminderSync()
~~~

- [ ] **Step 1: Write failing ViewModel tests**

~~~kotlin
@Test fun emptyCellPrefillsOnePeriod() = runTest {
    viewModel.openNewCourse(4, 7)
    assertEquals(4, viewModel.state.value.courseEditor.draft.dayOfWeek)
    assertEquals(7, viewModel.state.value.courseEditor.draft.startPeriod)
    assertEquals(7, viewModel.state.value.courseEditor.draft.endPeriod)
}

@Test fun savedEditRetainsIdAndShowsSyncNotice() = runTest {
    viewModel.openCourseEditor(11L)
    viewModel.saveCourse()
    runCurrent()
    assertEquals(11L, fakeRepository.lastSavedDraft!!.id)
    assertEquals("课程已保存，提醒同步失败", viewModel.state.value.courseEditor.syncNotice)
}
~~~

- [ ] **Step 2: Run the ViewModel tests and confirm they fail**

Run: ./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableViewModelTest --rerun-tasks

Expected: FAIL because the editor events and courseEditor state do not exist.

- [ ] **Step 3: Implement state transitions**

openNewCourse reads the current semester and returns without opening when none exists. It starts blank fields, default end equals start. openCourseEditor fetches the target full CourseEntity plus week rows using a repository findCourse(courseId) helper, preserving every editable field and ID. saveCourse runs in scope.launch, retains the dialog on Rejected, closes it on Saved without sync failure, and keeps the success notice with a retry action when reminderSyncFailed is true. requestCourseDelete only flips confirmation state; confirmCourseDelete does the repository call after user confirmation.

- [ ] **Step 4: Update the test fake and verify**

Extend FakeTimetableRepository with current-course lookup, mutable lastSavedDraft, configurable CourseMutationResult, and the three mutation methods. Preserve all current import tests and their confirmation assertions.

Run: ./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableViewModelTest --rerun-tasks

Expected: PASS for defaults, ID preservation, validation retention, delete confirmation, and sync notice.

- [ ] **Step 5: Commit**

~~~bash
git add app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt app/src/test/java/com/daily/life/feature/timetable/TimetableViewModelTest.kt
git commit -m "feat: add timetable course editor state"
~~~

## Task 4: Render central dialogs and grid interactions

**Files:**
- Modify: app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt
- Modify: app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt
- Modify: app/src/test/java/com/daily/life/feature/timetable/TimetableGridAppearanceTest.kt

**Interfaces:**
- Consumes the Task 3 callbacks and TimetableCourseEditorState.
- Produces an expanded TimetableScreen callback signature wired by DailyNavHost.

- [ ] **Step 1: Write failing card tests**

~~~kotlin
@Test fun cardContentShowsLocationWithoutTeacher() {
    val content = timetableCourseCardContent("数据库", "教二 203", "李老师")
    assertEquals("数据库", content.title)
    assertEquals("教二 203", content.location)
    assertEquals(null, content.teacher)
}

@Test fun colorRolesFollowFourDayCycle() {
    assertEquals(listOf(0,1,2,3,0,1,2), (1..7).map(::timetableCourseColorSlot))
}
~~~

- [ ] **Step 2: Run the card tests and confirm they fail**

Run: ./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableGridAppearanceTest --rerun-tasks

Expected: FAIL because timetableCourseCardContent does not exist and the former five-color mapping remains.

- [ ] **Step 3: Add empty-cell and card callbacks**

In WeeklyTimetableGrid, create a full empty cell for every day/period with clickable { onNewCourse(day.dayOfWeek, period.period) }. Keep course cards above it, so a multi-period course remains one card and opens editor by ID. Use only normal clickable inside existing verticalScroll to preserve scroll priority; do not use a pointerInput gesture consumer.

- [ ] **Step 4: Add editor and deletion AlertDialogs**

Implement a TimetableCourseEditorDialog with central Material AlertDialog. It contains course name, weekday, start/end periods, week-rule text, location, teacher, notes, validation error, save, and cancel. Use dropdown menus for weekday 1..7 and period 1..12. Show 1-16周、1-16周 单周、2,4,6周 beneath week-rule input. Existing courses show a TextButton delete action; a second AlertDialog contains the full course name and a weak-danger confirm action.

- [ ] **Step 5: Implement visual card content**

Replace the five colors with four palette records:
- Purple background #EDE9FF and text #7464D9.
- Green background #E6F5EE and text #579B78.
- Pink background #FCE8EF and text #D96B91.
- Blue background #E5F3F6 and text #5C9CA8.

Render the title at 13-14sp / 600 and location at 12-13sp / 600 with visible spacing. Never render teacher in CourseGridCard. Wire callbacks in DailyNavHost.

- [ ] **Step 6: Verify and commit**

Run: ./gradlew :app:testDebugUnitTest --tests com.daily.life.feature.timetable.TimetableGridAppearanceTest --tests com.daily.life.feature.timetable.TimetableViewModelTest --rerun-tasks
Run: ./gradlew :app:assembleDebug

Expected: tests PASS and debug APK assembles.

~~~bash
git add app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt app/src/test/java/com/daily/life/feature/timetable/TimetableGridAppearanceTest.kt
git commit -m "feat: add timetable course editing dialogs"
~~~

## Task 5: Regressions, review, and APK

**Files:**
- Modify only files from Tasks 1–4 if a test establishes a mismatch with the approved spec.

**Interfaces:**
- Consumes the manual-course flow and every existing timetable import, period, calendar mapping, adjustment, and week test.
- Produces a verified debug APK and review-ready commits.

- [ ] **Step 1: Run all timetable tests**

Run: ./gradlew :app:testDebugUnitTest --tests 'com.daily.life.feature.timetable.*' --rerun-tasks

Expected: PASS for editor, repository, ViewModel, import, holiday, adjustment, period, parser, and mapping tests.

- [ ] **Step 2: Diagnose any failure before changing source**

Use the failing test, its assertion, and the production call chain to classify it as a new-course regression or a pre-existing unrelated failure. Add a narrow failing test only for an uncovered approved requirement. Do not alter holiday, import, or adjustment code simply to hide a failing test.

- [ ] **Step 3: Build the installable debug APK**

Run: ./gradlew :app:assembleDebug --rerun-tasks

Expected: the configured debug output contains app-debug.apk.

- [ ] **Step 4: Inspect state and request final review**

Run: git status --short
Run: git log --oneline -5

Expected: no uncommitted source changes and distinct commits for domain, repository, ViewModel, and UI work. Request code review against the linked spec before publishing the APK.

- [ ] **Step 5: Commit a verified regression correction only if one was needed**

~~~bash
git add app/src/main/java/com/daily/life/feature/timetable/TimetableCourseEditor.kt app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt app/src/test/java/com/daily/life/feature/timetable/TimetableCourseEditorTest.kt app/src/test/java/com/daily/life/feature/timetable/TimetableRepositoryTest.kt app/src/test/java/com/daily/life/feature/timetable/TimetableViewModelTest.kt app/src/test/java/com/daily/life/feature/timetable/TimetableGridAppearanceTest.kt
git commit -m "fix: cover timetable course editor regression"
~~~

Do not make an empty commit when no regression correction is needed.
