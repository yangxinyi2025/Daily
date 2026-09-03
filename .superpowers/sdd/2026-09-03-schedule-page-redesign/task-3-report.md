# Task 3 Report — FlipCalendar coordinator and calendar UI

## TDD evidence

- RED: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleCalendarModelTest --no-daemon --console=plain` failed as expected with unresolved `CalendarFlipUiState` and `requestCalendarFlip` references in `ScheduleCalendarModelTest`.
- GREEN: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleCalendarModelTest --tests com.daily.life.feature.schedule.ScheduleCalendarPresentationTest --no-daemon --console=plain` completed successfully (`BUILD SUCCESSFUL`, 26 actionable tasks up-to-date) after implementation.

## Changed files

- Added `app/src/main/java/com/daily/life/feature/schedule/FlipCalendar.kt`.
- Updated `app/src/main/java/com/daily/life/feature/schedule/ScheduleCalendarModel.kt` with the pure flip state coordinator.
- Updated `app/src/main/java/com/daily/life/feature/schedule/ScheduleMonthScreen.kt` to supply the visible-month grid and render `FlipCalendar`.
- Updated `app/src/test/java/com/daily/life/feature/schedule/ScheduleCalendarModelTest.kt` with the two required flip tests.
- Updated `app/src/main/java/com/daily/life/feature/schedule/ScheduleScreen.kt` and `app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt` solely to pass the already-existing `ScheduleViewModel.browseMonth` callback through the existing screen boundary. No navigation route changed.

## Implementation details

- `CalendarFlipUiState`, `requestCalendarFlip`, and `completeCalendarFlip` preserve pending month and direction, ignore repeat requests while flipping, and commit only the pending month.
- `FlipCalendar` uses seven green binder rings, cream paper layers, Monday-first 42-day grids, outside-month styling, selected-day treatment, event dots, and visible `休/调/改` labels.
- Next flips rotate the current sheet from `0f` to `-90f`; previous flips rotate the pending sheet from `90f` to `0f`, both around the top center. Arrows are disabled while a flip is pending, and the browse callback is invoked only after the animation.
- The bundled Compose version exposes the duration scale through `MotionDurationScale` in the coroutine context rather than `LocalMotionDurationScale`; a zero scale selects the 120ms alpha-crossfade path.

## Self-review

- Verified Dec 2026 → Jan 2027 next flips and Jan 2027 → Dec 2026 previous flips with real coordinator tests.
- Verified rapid follow-up requests leave the pending flip state unchanged.
- Confirmed the former `schedule_calendar_illustration` is no longer referenced from the schedule source.
- `git diff --check` reports no whitespace errors.

## Commit

`feat: add schedule flip calendar`

## Concerns

- The callback propagation required two boundary files beyond the four files named in the Task 3 scope; it is the minimal integration needed to preserve Task 1's browse-month semantics. No route, database, reminder, or bottom-navigation behavior changed.
- Visual/emulator QA remains Task 5 scope.
