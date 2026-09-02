# Health Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply one shared pastel Daily design system and rebuild the Health screen as one functional weight-and-period timeline matching the supplied mobile references.

**Architecture:** Shared Compose tokens live in `core/designsystem` and are consumed by all surfaces without changing routes or repositories. `HealthScreen` becomes the single composition point for the existing `HealthState` and callbacks; the current weight and period logic remains in the ViewModel and repositories. Decorative content is delivered as independent transparent bitmap resources.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room/DataStore flows, Android emulator QA.

**Spec:** `C:\Users\19857\AppData\Local\Temp\design (1).md`

## Global Constraints

- Preserve weight, target-weight, period create/update/delete, and navigation behavior.
- Use one Chinese system sans-serif family with Regular, Medium, and Semibold/Bold hierarchy only.
- Use background `#F7F7FD`, ink `#10162A`, muted `#7C8498`, purple `#7B6CF6`, pink `#F57FA3`, and green `#61B486`.
- Implement cards and illustrations as normal Compose layouts and assets, never as a full-page screenshot.
- Run the app, compare screenshots to the supplied reference, and make at least two visual correction rounds.

---

### Task 1: Establish shared visual tokens

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyColors.kt`
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyTypography.kt`
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyShapes.kt`
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyComponents.kt`
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyBottomBar.kt`

- [ ] Write a focused token test for health presentation strings and hierarchy inputs.
- [ ] Run the test to confirm missing presentation tokens fail.
- [ ] Add the shared color, type, radius, light-shadow card, primary button, and navigation constants.
- [ ] Run the focused test and the existing design-system callers.

### Task 2: Merge Health composition while preserving callbacks

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/health/HealthScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/health/HealthPresentation.kt`
- Modify: `app/src/test/java/com/daily/life/feature/health/HealthPresentationTest.kt`

- [ ] Write a failing presentation test requiring one screen model with weight overview, trend, target, period, and history data.
- [ ] Run the test and confirm the presentation type is missing.
- [ ] Add the minimal presentation model and wire the existing `HealthState` into one vertical Compose page.
- [ ] Run the focused test and retain the existing ViewModel tests.

### Task 3: Implement reference cards and assets

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/health/HealthScreen.kt`
- Add: `app/src/main/res/drawable-nodpi/health_scale_illustration.png`
- Add: `app/src/main/res/drawable-nodpi/health_period_illustration.png`

- [ ] Render the overview, chart, target, period, and history cards with the real callbacks.
- [ ] Use generated transparent illustrations for the purple scale and pink calendar/heart areas.
- [ ] Run `:app:assembleDebug` and install the APK on the emulator.

### Task 4: Visual QA and release artifact

**Files:**
- Add: `docs/qa/health-design-iteration-*.png`
- Add: `C:\Users\19857\OneDrive\文档\ChatGPT\APP\Daily-health-redesign-debug.apk`

- [ ] Capture the initial Health-screen screenshot.
- [ ] Compare typography, spacing, card geometry, illustration placement, and navigation to `health-full.png`; apply correction round one.
- [ ] Capture again, apply correction round two, and capture the final screen.
- [ ] Rerun unit tests and assemble a separate debug APK without overwriting earlier deliverables.
