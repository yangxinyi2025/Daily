# Daily Quiet Sky Mobile Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing generic home summary stack with the approved Quiet Sky portrait home screen, while establishing reusable native mobile design rules for the remaining destinations.

**Architecture:** Keep the current Compose, Room, and ViewModel structure. Extend only the home summary contracts needed to expose today's course and schedule rows. Build the screen from focused design-system composables so the same spacing, surface, title, and bottom-navigation rules can be adopted by the other four screens later.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AndroidX Navigation, Room, kotlinx.coroutines Flow, JUnit.

**Spec:** `docs/superpowers/specs/2026-08-22-daily-quiet-sky-mobile-redesign.md`

## Global Constraints

- Target Android portrait layouts and a 390 x 844 visual reference.
- Preserve the Quiet Sky palette and Chinese-first visible copy.
- Keep current navigation routes, data models, imports, and user data backward compatible.
- Use no new UI libraries.
- Verify with unit tests, debug assembly, and an emulator screenshot.

---

### Task 1: Extend Home summaries with list-row data

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeState.kt`
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeSummaryRepositories.kt`
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/daily/life/feature/home/HomeViewModelTest.kt`

**Interfaces:**
- Produces: `HomeCourseRow(startPeriod: Int, courseName: String, detail: String)`.
- Produces: `HomeScheduleRow(id: Long, title: String, timeLabel: String)`.
- Extends: `TimetableHomeSummary.todayCourses` and `ScheduleHomeSummary.todaySchedules`.

- [ ] **Step 1: Write failing ViewModel tests**

Add fake summary values containing one `HomeCourseRow` and one `HomeScheduleRow`, collect `HomeViewModel.state`, and assert the same rows are exposed by `HomeState`.

- [ ] **Step 2: Run the focused test to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.daily.life.feature.home.HomeViewModelTest"`

Expected: FAIL because `HomeCourseRow`, `HomeScheduleRow`, or the new state fields do not exist.

- [ ] **Step 3: Add minimal contracts and repository mapping**

Map sorted current-day `CourseEntity` rows to course name plus location. Map current-day `ScheduleEventDao` rows to title plus local time. Copy both collections into `HomeState` in `HomeViewModel`.

- [ ] **Step 4: Run the focused test to verify success**

Run: `./gradlew :app:testDebugUnitTest --tests "com.daily.life.feature.home.HomeViewModelTest"`

Expected: PASS.

### Task 2: Create the Quiet Sky shared mobile primitives

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyColors.kt`
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyTypography.kt`
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyShapes.kt`
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyComponents.kt`
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyBottomBar.kt`

**Interfaces:**
- Produces: `QuietSkySectionCard`, `QuietSkyPageHeader`, and `QuietSkyListRow` composables.
- Produces: a five-item bottom bar with Chinese labels and Quiet Sky colors.

- [ ] **Step 1: Add color and shape constants**

Use the exact palette and 10dp, 16dp, 22dp, and 26dp corner scale from the spec. Keep semantic success/error colors separate from the primary blue/lavender brand colors.

- [ ] **Step 2: Implement surface, header, and list-row primitives**

Use `Card` only for grouped content. Provide optional trailing content, click handlers, content descriptions, consistent 18dp content padding, and cool-tinted elevation.

- [ ] **Step 3: Replace the default Material navigation appearance**

Keep the existing destination routes and icons. Use a fixed surface, compact labels, primary-blue active state, and no oversized selection pill.

- [ ] **Step 4: Build the app**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

### Task 3: Implement the approved Home composition

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeScreen.kt`
- Test: `app/src/test/java/com/daily/life/feature/home/HomeScreenTest.kt` if Compose UI test infrastructure is present; otherwise rely on the Task 1 ViewModel tests plus emulator verification.

**Interfaces:**
- Consumes: `HomeState.todayCourses`, `HomeState.todaySchedules`, existing destination callback, and `onOpenSettings`.
- Produces: header, overview card, `今日课表`, and `今日待办` in a single portrait scroll column.

- [ ] **Step 1: Build the header and overview card**

Render Chinese title/subtitle, notification/settings actions, greeting, date chip, and course/schedule/budget metrics. Use existing aggregate state for metrics and retain the settings callback.

- [ ] **Step 2: Build 今日课表**

Render current-day course rows as time, course title, and detail. Use a concise empty state with a Timetable destination action when there are no rows.

- [ ] **Step 3: Build 今日待办**

Render current-day schedule rows with time, title, and a non-destructive completion affordance. Use a concise empty state with a Schedule destination action when no rows exist.

- [ ] **Step 4: Verify existing navigation callbacks**

Tap the timetable and schedule section actions in the emulator. Expected: they route to the existing pages without changing route names.

### Task 4: Verify the portrait implementation

**Files:**
- Output: `app/build/outputs/apk/debug/app-debug.apk`
- Output: emulator screenshot artifact recorded in the task notes.

- [ ] **Step 1: Run full unit tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: all tests pass.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect the actual screen in an emulator**

Launch `com.daily.life`, take a portrait screenshot, and compare it against the approved hierarchy: no 2x2 module grid, clear course and to-do cards, Chinese labels, Quiet Sky palette, and five-item bottom navigation.

- [ ] **Step 4: Commit**

Run:

```powershell
git add app/src/main/java/com/daily/life/core/designsystem app/src/main/java/com/daily/life/feature/home app/src/test docs/superpowers
git commit -m "feat: redesign Daily home for Quiet Sky mobile"
```
