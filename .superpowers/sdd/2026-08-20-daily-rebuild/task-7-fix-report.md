# Task 7 fix report

Base commit: `f3f2920`

## Changed files

- `app/src/main/java/com/daily/life/core/database/DailyDaos.kt`
- `app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt`
- `app/src/main/java/com/daily/life/feature/health/HealthRepository.kt`
- `app/src/main/java/com/daily/life/feature/health/HealthScreen.kt`
- `app/src/main/java/com/daily/life/feature/health/HealthViewModel.kt`
- `app/src/main/java/com/daily/life/feature/health/MonthlyReportCalculator.kt`
- `app/src/test/java/com/daily/life/feature/health/HealthRepositoryTest.kt`
- `app/src/test/java/com/daily/life/feature/health/MonthlyReportCalculatorTest.kt`
- `app/src/androidTest/java/com/daily/life/feature/health/HealthScreenTest.kt`

## Fixes

- Prevented repeated health activity imports from duplicating persisted activity rows by looking up existing imported records with `source + rawRecordId` when available, or a deterministic source/timestamp/activity/metrics fingerprint otherwise, then reusing the existing Room id during insert-upsert.
- Preserved manual activity rows by matching imported records only against the importing source identity.
- Added explicit `HealthTab` selection state in `HealthState`, a `selectTab` action in `HealthViewModel`, and tab-scoped health screen content for weight, activity, and report views.
- Fixed `MonthlyReportCalculator` so the trailing 30-day chart pulls from all weight records inside the window, including dates from the previous month when the window crosses a month boundary.

## Tests and checks attempted

- Added `HealthRepositoryTest.repeatedActivityImportsUpsertByStableIdentityAndKeepManualRecords`
- Added `MonthlyReportCalculatorTest.recentWeightsIncludePreviousMonthDatesInsideTrailingWindow`
- Reworked `HealthScreenTest` to assert weight/activity/report tab visibility and content scoping
- Ran `git diff --check` successfully; only line-ending warnings were reported
- Ran `git status --short` to confirm the intended file set
- Attempted `./gradlew app:testDebugUnitTest --tests com.daily.life.feature.health.HealthRepositoryTest --tests com.daily.life.feature.health.MonthlyReportCalculatorTest`

## Verification limitations

- Gradle verification is still blocked by the existing invalid `JAVA_HOME` setting:
  `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- I also checked common system and per-user install locations during this fix round and did not find an alternative local JDK to point Gradle at without changing machine configuration.
