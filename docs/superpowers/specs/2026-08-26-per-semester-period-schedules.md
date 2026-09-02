# Per-Semester Period Schedules

## Goal

Allow every Daily user to retain the courses parsed from their own PDF while independently configuring the actual start and end time of periods 1–12 for each semester.

## Scope

- Keep the existing PDF course import, course records, week rules, timetable layout, and system-calendar course-message flow.
- Replace the hard-coded 1–12 time list with persisted period times owned by a semester.
- Parse commonly formatted period times from a timetable PDF when possible, then require the user to review the resulting time table before confirming the import.
- Provide a course-page entry for editing the current semester's period times after import.
- Recalculate future system-calendar course events when a semester's period times change.

## Out of Scope

- Multiple period schedules within a single semester.
- Automatic understanding of every university PDF layout.
- Reintroducing Daily's removed alarm implementation. Course reminders remain the existing system-calendar message-reminder flow.
- Moving the new control into the app-wide settings page.

## Data Model

Create a Room entity `SemesterPeriodEntity` backed by `semester_periods`.

| Field | Meaning |
| --- | --- |
| `semesterId` | Owning `SemesterEntity` ID; cascade delete with the semester. |
| `period` | A class period number in `1..12`; combined with `semesterId` it is the primary key. |
| `startTime` | `LocalTime` at which the period starts. |
| `endTime` | `LocalTime` at which the period ends. |

The database migrates from version 6 to 7. Existing semesters receive the current Daily default 12 period rows during the first read or first edit, rather than losing the existing visual labels or reminder times.

`CourseEntity` continues to store only `startPeriod` and `endPeriod`; no existing course data changes. A course starts at the `startTime` of its start period and ends at the `endTime` of its end period.

## Import and Parsing Flow

1. The user selects a course-table PDF as today.
2. The parser continues to build course preview rows. It additionally scans extracted text for explicit times in normal forms such as `08:00-08:45`, `08:00～08:45`, or `08:00 至 08:45`, and associates them with a nearby period number from 1–12.
3. The import state contains an editable list of all 12 period rows. Parsed rows overwrite the standard defaults; remaining rows keep the defaults.
4. The import preview displays a compact “节次时间核对” section before the course-row review. It shows every period's number, start time, and end time, plus whether any values were found in the PDF.
5. Import can proceed only when every row has valid `HH:mm` times, each start is before its end, and the periods are ordered without overlap.
6. `confirmImport` persists the courses and the selected semester period rows in one transaction. Replacing an existing timetable replaces that semester's period schedule as well.

If no reliable time pairs are found, Daily must say “未在 PDF 中识别到节次时间，请核对后导入”; it must not imply that the defaults came from the PDF.

## Course-Page Editing Flow

- The course-page top bar gets a compact “节次时间” action beside the existing import action.
- It opens a dedicated scrollable editor for the current semester, not a main-settings card.
- The editor presents periods 1–12 as rows with Chinese time pickers or `HH:mm` fields, a clear “恢复默认时间” action, and a single save action.
- Saving validates the same rules as import. On success it returns to the timetable, whose left labels immediately use the saved times.
- When there is no current semester, the action is unavailable and the normal empty-state import route remains unchanged.

## Reminder and Calendar Behaviour

- Current course calendar events are generated from the persisted period schedule, rather than from a list in `TimetableRepository`.
- Updating a period schedule first removes the current semester's existing future course calendar links/events, then re-creates future course message reminders using the new times.
- If calendar permission is missing or the cleanup/sync fails, the edit fails without persisting the new period rows, avoiding a timetable that disagrees with its calendar events.
- No alarm path is added. This feature only preserves the existing course message-reminder policy.

## UI Constraints

- Maintain the existing portrait timetable page and its 1–12 grid.
- Each left-side label remains three lines: period number, start time, end time.
- The import review remains functional even when a PDF includes zero explicit time pairs.
- The new editor uses existing Daily typography, colors, cards, rounded corners, and bottom navigation. It must not change the established timetable design outside of the new actions and editable time content.

## Testing and Verification

Unit and repository tests must cover:

1. Default rows are exactly the current 12 Daily times for a new/unconfigured semester.
2. PDF time extraction recognizes hyphen, wave-dash, and Chinese “至” separators and ignores invalid time pairs.
3. A parsed subset overrides only the corresponding default rows.
4. Validation rejects an end time not after its start time and overlapping/out-of-order rows.
5. Import persists the selected period schedule and existing course records still keep their original period numbers.
6. Editing the schedule changes the calculated course start/end timestamps and calendar sync requests.
7. Migration 6→7 leaves existing course rows readable and makes default schedule rows available for their semester.

Manual Android QA must import a PDF, review/edit at least one period, verify the timetable's left labels, reopen the editor to verify persistence, and inspect a future system-calendar course message time.
