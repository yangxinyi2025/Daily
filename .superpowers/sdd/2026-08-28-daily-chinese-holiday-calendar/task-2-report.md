# Task 2 Report

Date: 2026-08-28
Worktree: `C:\Users\19857\OneDrive\文档\ChatGPT\APP\.worktrees\daily-rebuild`
Base commit: `8193651f72dd27e87c252d32f3731655ba8bed1e`

## Summary

Implemented the persistence foundation for normalized holiday calendar rules without touching repository logic, ICS fetching, UI, or destructive database recreation. Added the normalized rule models, Room entities/DAO surface for holiday sources, normalized ICS event ranges, and per-day manual overrides, plus lightweight holiday sync status primitives in `DailyPreferences`.

The Room schema now advances from version 8 to 9 with a non-destructive `MIGRATION_8_9` that creates only the new holiday tables and indexes. Existing `SemesterCalendarAdjustmentEntity` rows are preserved unchanged, so previously confirmed semester adjustments survive the migration exactly as required.

Fix round 1 tightened the DAO replacement contract and migration verification. `replaceEventsForSource(sourceId, events)` now rejects mismatched rows before deleting anything, so the transaction cannot wipe one source's cache and then insert another source's events. The migration test now exercises an actual Room open on a synthesized version-8 database, which validates the declared version-9 schema rather than only checking hand-written SQL.

## Changed Files

- `app/src/main/java/com/daily/life/core/calendar/CalendarDayRuleModels.kt`
- `app/src/main/java/com/daily/life/core/database/DailyConverters.kt`
- `app/src/main/java/com/daily/life/core/database/DailyDaos.kt`
- `app/src/main/java/com/daily/life/core/database/DailyDatabase.kt`
- `app/src/main/java/com/daily/life/core/database/DailyEntities.kt`
- `app/src/main/java/com/daily/life/core/datastore/DailyPreferences.kt`
- `app/src/test/java/com/daily/life/core/database/HolidayCalendarDaoTest.kt`
- `app/src/test/java/com/daily/life/core/database/HolidayCalendarMigrationTest.kt`

## Test Commands And Results

1. Red-phase focused test run before implementation:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.database.HolidayCalendarDaoTest --tests com.daily.life.core.database.HolidayCalendarMigrationTest --console=plain
```

Result: failed as expected during test compilation because `holidayCalendarDao()`, the new entities/models, and `MIGRATION_8_9` did not exist yet.

2. Green-phase focused persistence and migration verification:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.database.HolidayCalendarDaoTest --tests com.daily.life.core.database.HolidayCalendarMigrationTest --tests com.daily.life.core.database.DailyDatabaseMigrationTest --console=plain
```

Result: `BUILD SUCCESSFUL in 22s`

Verified coverage from this command:

- two holiday sources keep separate event rows
- replacing one source's cached events does not delete another source
- day overrides upsert by date
- deleting one override date keeps other dates
- migration `8 -> 9` creates the new tables and indexes
- existing `semester_calendar_adjustments` data survives
- prior migration tests still pass

3. Fix-round focused verification after independent review:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.database.HolidayCalendarDaoTest --tests com.daily.life.core.database.HolidayCalendarMigrationTest --tests com.daily.life.core.database.DailyDatabaseMigrationTest --console=plain
```

Result: `BUILD SUCCESSFUL in 57s`

Additional coverage from this command:

- `replaceEventsForSource` rejects rows whose `sourceId` does not match the replacement target
- a rejected replacement leaves the source's previous cached rows intact
- `findEventsBetween` includes single-day events exactly on the query boundary
- `findEventsBetween` includes multi-day events that end on the start boundary or start on the end boundary
- migration verification now succeeds through an actual Room open and schema validation path

## Self-Review

- Checked `git diff --check`; no patch-format or whitespace errors were reported.
- Checked `git diff --stat` and `git status --short` to confirm the scope stayed inside Task 2 files.
- Kept the implementation limited to persistence primitives only; no repository, sync worker, ICS parsing/fetching, or UI behavior was added.

## Tradeoffs And Remaining Risks

- `DailyPreferences` now stores only lightweight holiday sync display state (`status`, `last sync at`, `error`) because the approved plan keeps source rows and event caches in Room. If Task 5 needs additional lightweight UI state, it should extend these keys rather than duplicate Room data.
- `replaceEventsForSource` now fails fast on mixed-source input instead of silently filtering it. I kept that stricter behavior because it preserves transactional safety and surfaces a caller bug immediately; silent filtering would hide upstream corruption while still mutating stored data.
- The DAO query ordering for `findEventsBetween` is chronological by normalized date range. The new overlap test locks inclusive range semantics, but presentation-level ordering beyond that still belongs to downstream repository/UI code.
- This task intentionally does not seed the built-in ICS source, fetch ICS data, or merge precedence layers. That behavior remains for downstream tasks and is not validated here.
