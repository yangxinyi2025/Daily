# Daily Quiet Sky Primary Pages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle Timetable, Schedule, Health, and Bill with the approved Quiet Sky portrait interface while preserving all existing behavior.

**Architecture:** Reuse the already shipped Quiet Sky Compose primitives instead of introducing a second visual system. Each task owns a disjoint pair of feature screen files and only rearranges existing UI state and callbacks; repositories, ViewModels, routes, imports, and editors remain intact.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AndroidX Navigation, Room, JUnit, Android emulator.

**Spec:** `docs/superpowers/specs/2026-08-22-daily-quiet-sky-primary-pages.md`

## Global Constraints

- Target Android portrait layouts with a 390 x 844 visual reference.
- Preserve the Quiet Sky palette and Chinese-first visible copy.
- Keep navigation routes, data models, imports, editors, and user data backward compatible.
- Use no new UI libraries and no web/desktop layout patterns.
- Verify with focused unit tests where state changes, a debug build, and emulator screenshots of all primary destinations.

---

### Task 1: Redesign the timetable and schedule screens

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleScreen.kt`

**Interfaces:**
- Consumes: existing `TimetableState`, `ScheduleState`, callbacks, and Quiet Sky primitives.
- Produces: portrait-native timetable and schedule composition without changing callbacks or routes.

- [ ] **Step 1: Capture the current function signatures and callbacks**

Keep every public screen parameter and all import/editor branches unchanged so app navigation and data flows remain source compatible.

- [ ] **Step 2: Restyle timetable information hierarchy**

Use `QuietSkyPageHeader` and `QuietSkySectionCard`; keep the import action visible, make week controls compact, retain the seven-day course grid, and use the Quiet Sky blue/lavender/green/rose/cyan variants for course cards.

- [ ] **Step 3: Restyle schedule information hierarchy**

Replace default button rows and nested cards with a compact mode control, date controls, one quick-create group, and one timeline surface. Keep edit/delete and editor behavior intact.

- [ ] **Step 4: Compile and run focused tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.daily.life.feature.timetable.*" --tests "com.daily.life.feature.schedule.*"`

Expected: BUILD SUCCESSFUL and focused tests pass.

- [ ] **Step 5: Commit**

Run: `git add app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt app/src/main/java/com/daily/life/feature/schedule/ScheduleScreen.kt && git commit -m "feat: redesign timetable and schedule screens"`

### Task 2: Redesign the health and bill screens

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/health/HealthScreen.kt`
- Modify: `app/src/main/java/com/daily/life/feature/bill/BillScreen.kt`

**Interfaces:**
- Consumes: existing `HealthState`, `BillState`, callbacks, and Quiet Sky primitives.
- Produces: portrait-native health and bill composition without changing callbacks, import, or editor branches.

- [ ] **Step 1: Preserve screen contracts**

Leave all public parameters and every current import/editor route unchanged. Reorganize only presentation code.

- [ ] **Step 2: Restyle health views**

Add a Quiet Sky header, compact month/tab controls, and grouped cards for weight, activity, and report content. Retain health-data reading, target/weight input, chart, and report actions.

- [ ] **Step 3: Restyle bill views**

Add a Quiet Sky header, compact filters/search, a single budget overview card, and a grouped transactions list. Retain period navigation, import, search, filters, transaction editor, status, and error flows.

- [ ] **Step 4: Compile and run focused tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.daily.life.feature.health.*" --tests "com.daily.life.feature.bill.*"`

Expected: BUILD SUCCESSFUL and focused tests pass.

- [ ] **Step 5: Commit**

Run: `git add app/src/main/java/com/daily/life/feature/health/HealthScreen.kt app/src/main/java/com/daily/life/feature/bill/BillScreen.kt && git commit -m "feat: redesign health and bill screens"`

### Task 3: Verify all five primary destinations

**Files:**
- Output: `app/build/outputs/apk/debug/app-debug.apk`
- Output: `docs/qa/daily-*-quiet-sky.png`

- [ ] **Step 1: Run the full unit suite**

Run: `./gradlew :app:testDebugUnitTest`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Assemble and install a debug APK**

Run: `./gradlew :app:assembleDebug :app:installDebug`

Expected: BUILD SUCCESSFUL and `com.daily.life` is installed.

- [ ] **Step 3: Inspect every destination in the emulator**

Open 首页、课表、日程、健康、账单; capture portrait screenshots and verify Chinese labels, bottom navigation, no desktop layouts, and working import/editor access points.

- [ ] **Step 4: Commit the implementation and screenshots**

Run: `git add app docs/qa docs/superpowers && git commit -m "feat: complete Quiet Sky primary pages"`
