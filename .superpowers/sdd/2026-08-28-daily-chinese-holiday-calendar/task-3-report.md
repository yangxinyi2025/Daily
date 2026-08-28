# Task 3 Report

## Scope

Implemented Task 3 only in `app/src/main/java/com/daily/life/core/calendar` and `app/src/test/java/com/daily/life/core/calendar`:

- Added a pure ICS parser with unfolded-line handling, all-day exclusive `DTEND` support, timed event date normalization, escaped text decoding, malformed-event isolation, and Chinese holiday/makeup classification.
- Added an OkHttp-based ICS client that enforces HTTPS, sends conditional `If-None-Match` / `If-Modified-Since` headers, converts timeout/network failures into `Failure`, parses successful bodies, and returns metadata without touching Room.
- Reused shared Chinese classification helpers from the calendar parser path so system calendar and ICS parsing follow the same holiday/makeup recognition rules.

## Fix Round 1

Addressed the Task 3 review issues without expanding scope beyond parser/client/tests/report:

- Changed parser failure propagation so a broken or malformed 200 OK ICS feed no longer becomes `IcsFetchResult.Success(emptyList)`.
- Added a dedicated `IcsCalendarParseException`; the parser throws it for malformed overall feeds or feeds whose `VEVENT`s are all malformed, and the client converts that into `IcsFetchResult.Failure(retryable = false)`.
- Kept per-event isolation: one malformed `VEVENT` is still skipped when other valid holiday events are present.
- Fixed escaped-text decoding order so a literal backslash followed by `n` or `N` stays literal instead of becoming a newline.

## TDD Record

1. Wrote `IcsCalendarParserTest` first, covering:
   - all-day `DTSTART`/exclusive `DTEND`
   - `国庆补周五`
   - `调休上班` with unknown source weekday staying `null`
   - timed ICS event normalization in `Asia/Shanghai`
   - escaped commas/newlines
   - malformed event isolation
   - ignoring unrelated events
   - literal `\\n` / `\\N` preservation after escaped backslashes
   - malformed overall feed failure
   - all-malformed holiday feed failure
   - valid empty calendar success
2. Ran parser red test and observed missing API compilation failures for `IcsCalendarSource`, `parseIcsCalendar`, `IcsCalendarClient`, and `IcsFetchResult`.
3. Implemented minimal parser/client code and reran parser tests to green.
4. Wrote `IcsCalendarClientTest` first for client behaviors, then verified:
   - 200 success returns parsed events and response metadata
   - 304 maps to `NotModified`
   - timeout maps to retryable `Failure`
   - conditional headers are sent
   - non-HTTPS URLs are rejected before network access
   - malformed 200 feed maps to non-retryable `Failure`
   - valid empty calendar remains `Success(emptyList)`
5. Ran the combined focused Task 3 suite after the fix round and verified green.

## Verification

Focused red:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.calendar.IcsCalendarParserTest --console=plain
```

Observed expected red state via unresolved ICS parser/client symbols before implementation.

Focused green:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.core.calendar.IcsCalendarParserTest --console=plain
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.core.calendar.IcsCalendarClientTest --console=plain
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.core.calendar.IcsCalendarParserTest --tests com.daily.life.core.calendar.IcsCalendarClientTest --console=plain
```

All selected Task 3 tests passed.

Fix round 1 verification:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.core.calendar.IcsCalendarParserTest --tests com.daily.life.core.calendar.IcsCalendarClientTest --console=plain
```

Observed `BUILD SUCCESSFUL` on August 28, 2026. Gradle emitted pre-existing unrelated warnings from other modules/tests, but the selected Task 3 suites passed.

`git diff --check` reported no whitespace errors. Git did warn that `SystemCalendarScheduleParser.kt` will normalize line endings to `CRLF` on a future write.

## Compatibility Notes

- Kept the Task 2 Room/data contracts unchanged.
- Kept unknown makeup source weekday as `null`.
- Kept malformed events isolated instead of failing the whole feed.
- Did not implement repository, worker, settings, or UI behavior from Task 4+.

## Remaining Risks

- The parser currently supports the ICS fields required by the brief (`UID`, `DTSTART`, `DTEND`, `SUMMARY`, `DESCRIPTION`) but not broader recurrence/timezone edge cases beyond this task’s scope.
- Shared classification now routes both system calendar and ICS through the same helper; future changes to Chinese keyword parsing should retest both paths together.
- Non-holiday feeds with well-formed but unrecognized events still return `Success(emptyList)`; this preserves current parser semantics for unrelated calendars while only failing clearly malformed or unparseable holiday-event feeds.
