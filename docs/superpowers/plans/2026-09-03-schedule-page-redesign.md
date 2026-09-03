# 日程页可爱风翻页日历 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有日程月视图升级为可爱风翻页日历，同时保留真实日程、日期状态和快捷创建业务。

**Architecture:** `ScheduleViewModel` 将浏览月份和选中日期分离，分别准备月历网格与选中日详情所需的数据。新的 `FlipCalendar` 只管理相邻月页面和翻页表现，完成动画后再通知 ViewModel 提交可见月份；`ScheduleMonthScreen` 负责把它与保留的日期修正、日程详情、编辑器和快捷创建组合起来。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、StateFlow、Room 日程仓储、现有 HolidayCalendarRepository、JUnit4 与 kotlinx-coroutines-test。

**Spec:** `docs/superpowers/specs/2026-09-02-schedule-page-redesign-design.md`

## Global Constraints

- 底部导航已完成，本计划不得修改 `DailyBottomBar.kt`、导航图标或导航路由。
- 不修改 `ScheduleEventEntity`、Room schema、日程提醒、节假日规则或手动修正的业务语义。
- 直接使用 `D:/19857/Documents/codex_schedule_redesign_bundle.zip` 内提供的 PNG，不生成、覆盖或拉伸素材。
- 浏览月份不得改变 `selectedDate`；只有用户点日期时才更新 selectedDate。
- 右翻为当前页围绕顶部装订边向上翻走；左翻为上一页从顶部向下落回；动画期间忽略重复翻页。
- UI 采用浅青绿 `#F6F8E7`、米白 `#F9F7EE`、深墨绿 `#204A0A`、浅绿 `#B1D685`、橙色 `#FFB246` 和紫色 `#B69DDB`；不得新增大面积蓝色或明显渐变。
- 所有新增交互提供中文 contentDescription；休、调、改状态保留文字。

---

## 文件结构

- `app/src/main/java/com/daily/life/feature/schedule/ScheduleModels.kt`：保存 `visibleMonth`、选中日详情事件和月份状态接口。
- `app/src/main/java/com/daily/life/feature/schedule/ScheduleCalendarModel.kt`：提供月窗口、选中日期与可见月份同步、翻页请求与完成的纯函数。
- `app/src/main/java/com/daily/life/feature/schedule/ScheduleViewModel.kt`：订阅可见月事件、选中日事件和对应日期规则；实现浏览月份。
- `app/src/main/java/com/daily/life/feature/schedule/FlipCalendar.kt`：新的翻页月历表现组件，包含装订环、纸页层、方向动画与日期网格。
- `app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt`：组合新月历、日期状态卡、当日日程和快捷创建区。
- `app/src/main/res/drawable/`：导入 ZIP 的 6 个日程相关 PNG。
- `app/src/test/java/com/daily/life/feature/schedule/`：扩展状态、月份、翻页和保留业务的测试。

### Task 1: 分离可见月份与选中日期状态

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleModels.kt:97-107`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleCalendarModel.kt:1-17`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleViewModel.kt:31-82, 209-227`
- Test: `app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarModelTest.kt`
- Test: `app/src/test/java/com/daily/life/feature/schedule/ScheduleViewModelTest.kt`

**Interfaces:**
- Produces `ScheduleState.visibleMonth: YearMonth`, `ScheduleState.selectedDateEvents: List<ScheduleEvent>`, and `ScheduleViewModel.browseMonth(month: YearMonth)`.
- Produces `calendarGridWindow(month: YearMonth): ClosedRange<LocalDate>` and `selectCalendarDate(visibleMonth: YearMonth, date: LocalDate): CalendarSelection` for UI and ViewModel tests.
- `ScheduleMonthScreen` will consume `state.visibleMonth`, `state.events` as grid markers, and `state.selectedDateEvents` as detail rows.

- [ ] **Step 1: Write failing state-separation tests**

```kotlin
@Test
fun selectingAnOutsideGridDateUpdatesBothTheDateAndItsVisibleMonth() {
    val selection = selectCalendarDate(
        visibleMonth = YearMonth.of(2026, 9),
        date = LocalDate.of(2026, 10, 1)
    )

    assertEquals(LocalDate.of(2026, 10, 1), selection.selectedDate)
    assertEquals(YearMonth.of(2026, 10), selection.visibleMonth)
}
```

Add this test to `ScheduleViewModelTest.kt`:

```kotlin
@Test
fun browsingMonthKeepsTheBusinessDateSelected() = runTest {
    val selected = LocalDate.of(2026, 9, 1)
    val viewModel = ScheduleViewModel(
        repository = RecordingScheduleRepository(),
        clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC),
        coroutineScope = backgroundScope
    )

    viewModel.selectDate(selected)
    viewModel.browseMonth(YearMonth.of(2026, 10))

    assertEquals(selected, viewModel.state.value.selectedDate)
    assertEquals(YearMonth.of(2026, 10), viewModel.state.value.visibleMonth)
}
```

- [ ] **Step 2: Run the new model tests and confirm they fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleCalendarModelTest --no-daemon --console=plain
```

Expected: compilation failure because `CalendarFlipDirection`, `selectCalendarDate`, `visibleMonth`, and `browseMonth` do not exist.

- [ ] **Step 3: Add the pure calendar state contracts**

```kotlin
internal enum class CalendarFlipDirection { PREVIOUS, NEXT }

internal data class CalendarSelection(
    val visibleMonth: YearMonth,
    val selectedDate: LocalDate
)

internal fun browseCalendarMonth(month: YearMonth, direction: CalendarFlipDirection): YearMonth =
    if (direction == CalendarFlipDirection.NEXT) month.plusMonths(1) else month.minusMonths(1)

internal fun selectCalendarDate(visibleMonth: YearMonth, date: LocalDate): CalendarSelection =
    CalendarSelection(visibleMonth = YearMonth.from(date), selectedDate = date)
```

Add `visibleMonth` and `selectedDateEvents` to `ScheduleState`. In `ScheduleViewModel`, hold `MutableStateFlow(YearMonth.from(selectedDate.value))`; make `browseMonth` update only that flow and refresh the month grid rules. Make `selectDate` use `selectCalendarDate` so direct date clicks synchronize both values.

For month mode, observe the `calendarGridWindow(visibleMonth)` range and the selected day range separately, then merge by event id into `state.events`; set `state.selectedDateEvents` only from the selected-day query. Retain the existing week/day query for non-month modes. Resolve grid rules for the 42-day visible range and append the selected day rule when it is outside that range.

- [ ] **Step 4: Run state and ViewModel tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleCalendarModelTest --tests com.daily.life.feature.schedule.ScheduleViewModelTest --no-daemon --console=plain
```

Expected: PASS, including existing explicit-save and reminder-status tests.

- [ ] **Step 5: Commit the state change**

```powershell
git add app/src/main/java/com/daily/life/feature/schedule/ScheduleModels.kt app/src/main/java/com/daily/life/feature/schedule/ScheduleCalendarModel.kt app/src/main/java/com/daily/life/feature/schedule/ScheduleViewModel.kt app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarModelTest.kt app/src/test/java/com/daily/life/feature/schedule/ScheduleViewModelTest.kt
git commit -m "feat: separate schedule month browsing from selection"
```

### Task 2: 导入素材并建立日程页颜色令牌

**Files:**
- Create: `app/src/main/res/drawable/schedule_mascot_clipboard.png`
- Create: `app/src/main/res/drawable/schedule_date_status_icon.png`
- Create: `app/src/main/res/drawable/schedule_quick_lightning_icon.png`
- Create: `app/src/main/res/drawable/schedule_quick_exam_icon.png`
- Create: `app/src/main/res/drawable/schedule_quick_birthday_icon.png`
- Create: `app/src/main/res/drawable/schedule_quick_minor_task_icon.png`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt:65-80`

**Interfaces:**
- Produces resource ids used by the new date status and quick-create composables.
- Produces private screen-local colors `SchedulePageBackground`, `ScheduleSurfaceCream`, `ScheduleInk`, `ScheduleGreen`, `ScheduleOrange`, and `SchedulePurple`.

- [ ] **Step 1: Add an asset-presence test before copying resources**

```kotlin
@Test
fun scheduleRedesignUsesTheProvidedMascotAndQuickCreateAssets() {
    assertTrue(R.drawable.schedule_mascot_clipboard != 0)
    assertTrue(R.drawable.schedule_quick_exam_icon != 0)
    assertTrue(R.drawable.schedule_quick_birthday_icon != 0)
    assertTrue(R.drawable.schedule_quick_minor_task_icon != 0)
}
```

Create `ScheduleRedesignAssetsTest.kt` in the schedule test package. Use `R` from `com.daily.life`.

- [ ] **Step 2: Run the asset test and confirm it fails**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleRedesignAssetsTest --no-daemon --console=plain
```

Expected: unresolved drawable resource ids.

- [ ] **Step 3: Copy the six approved PNG files from the ZIP**

Map ZIP entries as follows:

```text
assets/icons/mascot_schedule_clipboard.png -> schedule_mascot_clipboard.png
assets/icons/icon_date_status.png -> schedule_date_status_icon.png
assets/icons/icon_quick_lightning.png -> schedule_quick_lightning_icon.png
assets/icons/icon_exam.png -> schedule_quick_exam_icon.png
assets/icons/icon_birthday.png -> schedule_quick_birthday_icon.png
assets/icons/icon_minor_task.png -> schedule_quick_minor_task_icon.png
```

Define the six screen-local Compose colors exactly as `Color(0xFFF6F8E7)`, `Color(0xFFF9F7EE)`, `Color(0xFF204A0A)`, `Color(0xFFB1D685)`, `Color(0xFFFFB246)`, and `Color(0xFFB69DDB)`. Do not alter `DailyBottomBar.kt` or the global navigation resources.

- [ ] **Step 4: Run the asset test**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleRedesignAssetsTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit the assets and local tokens**

```powershell
git add app/src/main/res/drawable/schedule_mascot_clipboard.png app/src/main/res/drawable/schedule_date_status_icon.png app/src/main/res/drawable/schedule_quick_lightning_icon.png app/src/main/res/drawable/schedule_quick_exam_icon.png app/src/main/res/drawable/schedule_quick_birthday_icon.png app/src/main/res/drawable/schedule_quick_minor_task_icon.png app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt app/src/test/java/com/daily/life/feature/schedule/ScheduleRedesignAssetsTest.kt
git commit -m "feat: add schedule redesign assets"
```

### Task 3: 实现可测试的翻页协调器与 FlipCalendar

**Files:**
- Create: `app/src/main/java/com/daily/life/feature/schedule/FlipCalendar.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleCalendarModel.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt:83-236`
- Test: `app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarModelTest.kt`

**Interfaces:**
- Produces `FlipCalendar(month: YearMonth, selectedDate: LocalDate, days: List<LocalDate>, rules: Map<LocalDate, ScheduleCalendarRuleUi>, eventDates: Set<LocalDate>, onDateSelected: (LocalDate) -> Unit, onMonthCommitted: (YearMonth) -> Unit)`.
- Produces `CalendarFlipUiState` and pure `requestCalendarFlip` / `completeCalendarFlip` helpers for rapid-tap and direction testing.
- Consumes `CalendarFlipDirection`, `monthCalendarDays`, and existing badge semantics.

- [ ] **Step 1: Write failing flip-controller tests**

```kotlin
@Test
fun nextFlipLocksAdditionalRequestsUntilTheCurrentPageCompletes() {
    val started = requestCalendarFlip(
        CalendarFlipUiState(YearMonth.of(2026, 12)),
        CalendarFlipDirection.NEXT
    )
    val ignored = requestCalendarFlip(started, CalendarFlipDirection.NEXT)

    assertTrue(started.isFlipping)
    assertEquals(YearMonth.of(2027, 1), started.pendingMonth)
    assertEquals(started, ignored)
}

@Test
fun previousFlipUsesTheOppositeDirectionAcrossYears() {
    val started = requestCalendarFlip(
        CalendarFlipUiState(YearMonth.of(2027, 1)),
        CalendarFlipDirection.PREVIOUS
    )

    assertEquals(YearMonth.of(2026, 12), started.pendingMonth)
    assertEquals(CalendarFlipDirection.PREVIOUS, started.direction)
}
```

- [ ] **Step 2: Run the flip tests and confirm they fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleCalendarModelTest --no-daemon --console=plain
```

Expected: unresolved `CalendarFlipUiState` and `requestCalendarFlip` references.

- [ ] **Step 3: Add controller and Compose page layers**

```kotlin
internal data class CalendarFlipUiState(
    val shownMonth: YearMonth,
    val pendingMonth: YearMonth? = null,
    val direction: CalendarFlipDirection? = null
) {
    val isFlipping: Boolean get() = pendingMonth != null
}

internal fun requestCalendarFlip(
    state: CalendarFlipUiState,
    direction: CalendarFlipDirection
): CalendarFlipUiState = if (state.isFlipping) state else state.copy(
    pendingMonth = browseCalendarMonth(state.shownMonth, direction),
    direction = direction
)
```

`FlipCalendar` keeps this UI state with `rememberSaveable`. Render a stationary lower page for `pendingMonth`; overlay the moving page with `graphicsLayer { transformOrigin = TransformOrigin(0.5f, 0f); rotationX = animatedAngle }`. For NEXT, animate `0f -> -90f` while the current page leaves; for PREVIOUS, initially render the pending page at `90f` and animate it to `0f` over the current page. On animation completion call `onMonthCommitted(pendingMonth)`, then replace shown month and clear `pendingMonth`.

Use 320ms by default. Check `LocalMotionDurationScale.current.scaleFactor`; when it is zero, use a 120ms alpha crossfade instead of rotation. The month header, Monday-first 42-cell grid, outside-month styling, selected day, event dot and `休/调/改` marker must all be inside a reusable `CalendarPaperPage` composable so both layers have identical data layout.

Replace the old `ScheduleMonthCard` call with `FlipCalendar`; pass `state.visibleMonth`, `state.selectedDate`, `state.events` date set, current rule map, `onDateSelected`, and `onMonthCommitted = onBrowseMonth`.

- [ ] **Step 4: Run calendar presentation tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleCalendarModelTest --tests com.daily.life.feature.schedule.ScheduleCalendarPresentationTest --no-daemon --console=plain
```

Expected: PASS; existing holiday, makeup-workday and manual-marker assertions remain unchanged.

- [ ] **Step 5: Commit FlipCalendar**

```powershell
git add app/src/main/java/com/daily/life/feature/schedule/FlipCalendar.kt app/src/main/java/com/daily/life/feature/schedule/ScheduleCalendarModel.kt app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarModelTest.kt
git commit -m "feat: add schedule flip calendar"
```

### Task 4: 重构日期状态、当日日程和快捷创建视觉

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt:296-557`
- Test: `app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarPresentationTest.kt`

**Interfaces:**
- Consumes `ScheduleState.selectedDateEvents`, `selectedCalendarRule`, `holidayLastSyncAt`, existing edit/delete callbacks and the six imported drawable ids.
- Produces unchanged callbacks: `onCreate`, `onQuickCreate`, `onEdit`, `onDelete`, `onSaveCalendarDayOverride`, and `onClearCalendarDayOverrides`.

- [ ] **Step 1: Write failing presentation tests for retained behavior**

```kotlin
@Test
fun daySummaryUsesSelectedDayEventsInsteadOfTheVisibleMonthMarkerList() {
    val event = sampleScheduleEvent(title = "讲座", day = LocalDate.of(2026, 9, 1))
    val content = selectedDayScheduleContent(selectedEvents = listOf(event))

    assertEquals(listOf("讲座"), content.events.map(ScheduleEvent::title))
    assertFalse(content.isEmpty)
}

@Test
fun quickCreateActionsKeepTheirExistingEditorPresets() {
    assertEquals("考试", quickCreatePreset(ScheduleQuickAction.EXAM).title)
    assertTrue(quickCreatePreset(ScheduleQuickAction.BIRTHDAY).repeatYearly)
    assertEquals("小事", quickCreatePreset(ScheduleQuickAction.SMALL_THING).title)
}
```

Extract the small pure helpers `selectedDayScheduleContent` and `quickCreatePreset` from presentation code. Have `ScheduleViewModel.startCreate` call `quickCreatePreset` when an action is present, preserving the current titles and birthday yearly repeat.

- [ ] **Step 2: Run presentation tests and confirm they fail**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleCalendarPresentationTest --no-daemon --console=plain
```

Expected: unresolved presentation helper references.

- [ ] **Step 3: Implement the redesigned lower content**

Keep `ScheduleCalendarRuleSummary` inputs and date correction controls unchanged, but place the summary text in a cream card with the clipboard mascot aligned end at 25–30% width. Use `ScheduleGreen` for the correction button border and `ScheduleInk` for text.

Render the empty selected-day section with `schedule_date_status_icon`, high-contrast “暂无安排”, its existing selected-date copy, and a `新建` button that invokes `onCreate`. Render non-empty rows from `state.selectedDateEvents`; retain edit-on-row and delete callbacks, add time and optional `event.location` in the metadata line.

Render quick creation with `schedule_quick_lightning_icon`, a “新建日程” row calling `onCreate`, and three outlined pill buttons using their ZIP PNG. Each button calls its existing `onQuickCreate(action)` callback. Use purple text for 考试, deep orange/ink for 生日 and deep orange/ink for 小事; do not use line Material icons in these buttons.

- [ ] **Step 4: Run the schedule presentation and ViewModel tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleCalendarPresentationTest --tests com.daily.life.feature.schedule.ScheduleViewModelTest --no-daemon --console=plain
```

Expected: PASS, including the existing manual correction labels and reminder behavior.

- [ ] **Step 5: Commit the lower-page redesign**

```powershell
git add app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt app/src/main/java/com/daily/life/feature/schedule/ScheduleCalendarPresentation.kt app/src/main/java/com/daily/life/feature/schedule/ScheduleViewModel.kt app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarPresentationTest.kt app/src/test/java/com/daily/life/feature/schedule/ScheduleViewModelTest.kt
git commit -m "feat: redesign schedule detail and quick actions"
```

### Task 5: 集成验证和视觉 QA

**Files:**
- Modify if required by QA: `app/src/main/java/com/daily/life/feature/schedule/FlipCalendar.kt`
- Modify if required by QA: `app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt`
- Test: `app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarModelTest.kt`
- Test: `app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarPresentationTest.kt`
- Test: `app/src/test/java/com/daily/life/feature/schedule/ScheduleViewModelTest.kt`

**Interfaces:**
- Verifies the final public schedule screen contract without changing `ScheduleScreen` callback signatures or navigation.

- [ ] **Step 1: Add final month boundary tests**

```kotlin
@Test
fun monthGridIncludesLeapDayAndKeepsSixWeeks() {
    val days = monthCalendarDays(LocalDate.of(2028, 2, 15))

    assertTrue(days.contains(LocalDate.of(2028, 2, 29)))
    assertEquals(42, days.size)
}

@Test
fun completingFlipCommitsOnlyThePendingMonth() {
    val started = requestCalendarFlip(
        CalendarFlipUiState(YearMonth.of(2026, 12)),
        CalendarFlipDirection.NEXT
    )

    assertEquals(YearMonth.of(2027, 1), completeCalendarFlip(started).shownMonth)
}
```

- [ ] **Step 2: Run the complete schedule-focused test suite**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleCalendarModelTest --tests com.daily.life.feature.schedule.ScheduleCalendarPresentationTest --tests com.daily.life.feature.schedule.ScheduleViewModelTest --tests com.daily.life.feature.schedule.ScheduleLocationModelTest --tests com.daily.life.feature.schedule.ScheduleRepositoryTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 3: Build a Debug APK**

Run:

```powershell
.\gradlew.bat assembleDebug --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL` and `app-debug.apk` under the configured Gradle temporary build directory.

- [ ] **Step 4: Perform emulator visual QA**

Use the Android emulator to open the 日程 tab and capture a screenshot. Verify: green binding rings, cream paper layers, deep-green high-contrast copy, no old calendar illustration, selected date remains unchanged after browsing, both flip directions differ, no double month jump after rapid taps, existing correction controls open, quick actions open the correct prefilled editor, and bottom navigation is unchanged.

- [ ] **Step 5: Commit only visual corrections discovered by QA**

```powershell
git add app/src/main/java/com/daily/life/feature/schedule/FlipCalendar.kt app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarModelTest.kt app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarPresentationTest.kt
git commit -m "fix: polish schedule calendar redesign"
```

## Plan Self-Review

- Spec coverage: Task 1 covers selected date separation, month data and rule loading; Task 2 covers all approved assets and palette; Task 3 covers the desktop calendar and direction-specific animated flip; Task 4 covers date status, selected-day data and all quick actions; Task 5 covers boundaries, build and visual acceptance. Bottom navigation is explicitly excluded.
- Placeholder scan: every task includes concrete files, interfaces, test commands and implementation steps.
- Type consistency: `CalendarFlipDirection`, `CalendarSelection`, `CalendarFlipUiState`, `browseCalendarMonth`, `selectCalendarDate`, `requestCalendarFlip`, `completeCalendarFlip`, `visibleMonth`, and `selectedDateEvents` are introduced before later tasks consume them.
