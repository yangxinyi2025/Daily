# Daily Quiet Sky Primary Pages

## Goal

Apply the user-approved, portrait-first Quiet Sky visual language to the four primary pages that follow Home: Timetable, Schedule, Health, and Bill.

## Binding design rules

- Keep the existing Quiet Sky palette exactly: background `#F6F8FD`, surface `#FFFFFF`, ink `#121B33`, muted text `#7B869D`, primary `#6F8FF4`, lavender `#9188E8`, success `#6DA987`, rose `#D9909C`, cyan `#76B8C7`, and border `#E2E8F4`.
- Android portrait is the only target. Use one vertical content column with 18dp side padding and the existing five-item Chinese bottom navigation.
- All visible feature copy is Chinese. Preserve routes, Room data, import flows, editors, and user data.
- Cards are reserved for grouped information. Use 26dp main-card corners and light cool borders; do not add desktop grids or broad horizontal modules.

## Page information hierarchy

### Timetable

1. `课表` header, current semester metadata, and PDF import action.
2. Compact week navigator with previous, current-week, and next actions.
3. One timetable surface with a legible seven-day column grid, a quiet highlight for today, and soft course color variants.
4. Keep the selected-course details and the no-data import path.

### Schedule

1. `日程` header and a single primary “新建日程” action.
2. Compact segmented mode control and date navigation.
3. Timeline list grouped in one surface; show time, title, reminder metadata, and a restrained delete action.
4. Keep quick-create actions, editing, reminders, and the empty state.

### Health

1. `健康` header and month control.
2. Compact Chinese tabs rather than default Material tabs.
3. Weight: primary latest-weight card, target card, and chart card.
4. Activity and monthly report: grouped summary, clear actions, and consistent empty states.

### Bill

1. `账单` header, selected period metadata, and import action.
2. Compact period/direction filters and search.
3. One spending overview card with expense, income, budget, and budget-progress information.
4. A readable transaction list with Chinese metadata and non-destructive editor entry.

## Acceptance criteria

- All five primary destinations share the actual native mobile Quiet Sky system.
- The four new pages remain vertically legible at a 390 x 844 reference viewport with no web/desktop split layouts.
- Existing functionality stays reachable and the debug build, full unit suite, and emulator page walkthrough succeed.
