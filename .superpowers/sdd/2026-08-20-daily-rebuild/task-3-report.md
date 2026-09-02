# Task 3 Report

## Summary

Implemented the Task 3 local foundation in the `codex/daily-rebuild` worktree on top of Task 2 commit `a0e2d23`. Added the Room schema, DAOs, converters, non-sensitive DataStore preferences, keystore-backed secret storage with an in-memory test fake, and a lazy `AppContainer` surface wired through `DailyApplication` without adding startup health/network/worker/alarm work.

## Changed files

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/java/com/daily/life/DailyApplication.kt`
- `app/src/main/java/com/daily/life/core/AppContainer.kt`
- `app/src/main/java/com/daily/life/core/database/DailyConverters.kt`
- `app/src/main/java/com/daily/life/core/database/DailyDaos.kt`
- `app/src/main/java/com/daily/life/core/database/DailyDatabase.kt`
- `app/src/main/java/com/daily/life/core/database/DailyEntities.kt`
- `app/src/main/java/com/daily/life/core/datastore/DailyPreferences.kt`
- `app/src/main/java/com/daily/life/core/security/AndroidSecretStore.kt`
- `app/src/main/java/com/daily/life/core/security/SecretStore.kt`
- `app/src/test/java/com/daily/life/core/database/DailyDatabaseTest.kt`
- `app/src/test/java/com/daily/life/core/security/SecretStoreTest.kt`

## Implementation notes

- Added Room entities for `SemesterEntity`, `CourseEntity`, `ScheduleEventEntity`, `WeightRecordEntity`, `ActivityRecordEntity`, `MonthlyReportEntity`, `TransactionEntity`, `BudgetEntity`, and `ImportLogEntity`.
- Added explicit converters for `LocalDate`, `YearMonth`, `Set<Int>`, and the Task 3 enums so Room schema storage stays deterministic.
- Added DAO interfaces for semester, course, schedule, health, transaction, budget, and import-log access with suspend CRUD plus `Flow` queries.
- Kept `amountCents: Long`, `weightJin: Double`, `reminderOffsetMinutes: Int`, and `repeatYearly: Boolean` explicit in the schema.
- Added indices for schedule event time, transaction time/category/direction, course semester/week/day, and monthly report month.
- Implemented `DailyPreferences` for non-sensitive values only and routed secrets through `SecretStore`.
- Implemented `AndroidSecretStore` with `EncryptedSharedPreferences`/`MasterKey` and provided `InMemorySecretStore` for unit tests.
- Replaced the Task 1 `DailyApplication` stub installer with a lazy `DefaultAppContainer`; the application no longer performs work in `onCreate`.
- Exposed repository and adapter placeholders through `AppContainer` so later tasks can attach module-specific implementations without changing startup behavior.

## Tests and verification

### Test-first step

Created the following tests before the implementation files:

- `app/src/test/java/com/daily/life/core/database/DailyDatabaseTest.kt`
- `app/src/test/java/com/daily/life/core/security/SecretStoreTest.kt`

### Commands run

1. Initial failing-test verification attempt with the brief JDK:

```powershell
$env:JAVA_HOME='C:\Users\19857\AppData\Local\Temp\jdk17\jdk-17.0.20+8'
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.database.DailyDatabaseTest --tests com.daily.life.core.security.SecretStoreTest
```

Observed output:

```text
Exception in thread "main" java.lang.RuntimeException: Could not create parent directory for lock file C:\Users\19857\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1-bin.zip.lck
    at org.gradle.wrapper.GradleWrapperMain.main(SourceFile:65)
```

2. Retried with a worktree-local Gradle user home:

```powershell
$env:JAVA_HOME='C:\Users\19857\AppData\Local\Temp\jdk17\jdk-17.0.20+8'
$env:GRADLE_USER_HOME='C:\Users\19857\OneDrive\文档\ChatGPT\APP\.worktrees\daily-rebuild\.gradle-user-home'
.\gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.database.DailyDatabaseTest --tests com.daily.life.core.security.SecretStoreTest
```

Observed output:

```text
Exception in thread "main" java.io.IOException: Downloading from https://services.gradle.org/distributions/gradle-8.11.1-bin.zip failed: timeout (10000ms)
Caused by: java.net.SocketTimeoutException: Read timed out
```

3. Copied the already-installed local Gradle 8.11.1 distribution into the worktree-local cache key and retried with `--no-daemon`:

```powershell
$env:JAVA_HOME='C:\Users\19857\AppData\Local\Temp\jdk17\jdk-17.0.20+8'
$env:GRADLE_USER_HOME='C:\Users\19857\OneDrive\文档\ChatGPT\APP\.worktrees\daily-rebuild\.gradle-user-home'
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.core.database.DailyDatabaseTest --tests com.daily.life.core.security.SecretStoreTest
```

Observed output before the bounded stop requested by the user:

```text
To honour the JVM settings for this build a single-use Daemon process will be forked.
Daemon will be stopped at the end of the build
```

### Verification status

- Fresh compiler/test/build success was not obtained inside the bounded window.
- The concrete blocker was environment-specific Gradle wrapper startup and dependency/bootstrap timing in this Unicode worktree, not a confirmed Task 3 test failure.
- I stopped the last Gradle run on user instruction rather than waiting indefinitely.

## Self-review

- Confirmed `DailyApplication` no longer performs installation work in `onCreate`; the container is lazy.
- Confirmed no health adapter, network client, worker, or alarm scheduling is created from the application constructor.
- Confirmed secrets are not routed through `DailyPreferences`.
- Confirmed test fake secret storage is in-memory only.
- Confirmed Room schema includes all Task 3 entities and the required explicit numeric/boolean fields.
- Confirmed Gradle changes are limited to Task 3 dependencies/plugins needed for Room KSP, secret storage, and JVM tests.
- Tightened the implementation after static review by:
  - adding explicit enum converters instead of relying on implicit Room handling
  - correcting the `DailyPreferences.create` test helper to pass a `CoroutineScope`
  - removing the Task 1 inline `AppContainer` stub from `DailyApplication.kt`

## Concerns

- Verification remains incomplete. I do not have a passing `:app:testDebugUnitTest` or `:app:assembleDebug` result to claim.
- `androidx.security:security-crypto` is pinned to `1.1.0-alpha06` because the repository already targets modern AndroidX stacks and this is the straightforward keystore-backed path; this should be compiler-verified in the next environment-enabled run.
- The new `AppContainer` repository/adapter placeholders are intentionally minimal and may be refined by Tasks 4-9 as concrete module types land.

## Commit

- Implemented in commit `1d6b39e` (`feat: add local data and secret storage`).

The original staged command was:

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/daily/life/DailyApplication.kt app/src/main/java/com/daily/life/core app/src/test/java/com/daily/life/core .superpowers/sdd/2026-08-20-daily-rebuild/task-3-report.md
git commit -m "feat: add local data and secret storage"
```

## Fix Round 1

### Findings addressed

- Completed suspend CRUD coverage for `HealthDao`, `BudgetDao`, and `ImportLogDao`; added the missing course read-by-id API and retained Flow query surfaces for each DAO.
- Added normalized `CourseWeekEntity` storage plus the `CourseWithWeeks` Room relation. `CourseDao.observeBySemesterWeekAndDay` now uses an indexed SQL join on semester, week, and day instead of scanning a semester and interpreting serialized weeks in application code. The original `CourseEntity.weekRuleText` and `parsedWeeks` fields remain intact.
- Replaced throwing `DeferredFactory` placeholders with typed `ComponentFactory<T>` providers returning nullable values. Default providers are inert and injectable; they do not throw when later feature implementations are absent.

### Tests added first

- `DailyDatabaseTest.courseLookupUsesSemesterWeekAndDayRelation` verifies the normalized semester/week/day query and Room relation.
- `DailyDatabaseTest.healthBudgetAndImportDaosSupportUpdateAndDelete` exercises the newly added update, find, and delete APIs.
- `AppContainerTest.unavailableFactoriesReturnNullInsteadOfThrowing` verifies default optional providers are safe to call.

### Bounded verification and exact limitation

The focused command was run with the JDK required by the brief:

```powershell
$env:JAVA_HOME='C:\Users\19857\AppData\Local\Temp\jdk17\jdk-17.0.20+8'
$env:GRADLE_USER_HOME='C:\Users\19857\OneDrive\文档\ChatGPT\APP\.worktrees\daily-rebuild\.gradle-user-home'
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.core.database.DailyDatabaseTest --tests com.daily.life.core.AppContainerTest
```

The direct worktree run failed before compilation with:

```text
> Task :app:generateDebugBuildConfig FAILED
Execution failed for task ':app:generateDebugBuildConfig'.
> java.nio.file.AccessDeniedException: C:\Users\19857\OneDrive\�ĵ�\ChatGPT\APP\.worktrees\daily-rebuild\app\build\generated\source\buildConfig\debug\com\daily\life
BUILD FAILED in 1m 1s
1 actionable task: 1 executed
```

A retry outside the sandbox with the same JDK produced the same `AccessDeniedException`. A temporary init script relocated only Gradle's app build output to a writable visualization directory. That bounded retry reached:

```text
> Task :app:generateDebugBuildConfig
> Task :app:checkDebugAarMetadata
```

It was stopped after the user's bounded-verification instruction; the final interrupted process exited with code 1 and produced no test result. The remaining Java process (PID 11872) was explicitly stopped, and a follow-up process check showed no `java` or `gradle` processes. Therefore no fresh compiler, unit-test, or APK success can be claimed for this fix round.

### Fix-round self-review

- Confirmed `course_weeks` is part of `DailyDatabase` and has a foreign-key cascade from courses.
- Confirmed the timetable query filters in SQL by all three requested dimensions and orders by start period.
- Confirmed all default AppContainer providers return `null` without network, health, worker, alarm, or other startup work.
- Confirmed the temporary Gradle init script was not retained as a source/build change.

### Fix-round commit

Implemented in commit `c5cc095` (`fix: complete Task 3 data interfaces`); the report append happened after the source/test commit.

## Fix Round 2

### Findings addressed

- Bumped `DailyDatabase` schema version from `1` to `2` after the `CourseWeekEntity` addition.
- Added `DailyDatabase.MIGRATION_1_2` to create the new `course_weeks` table and its `index_course_weeks_week_courseId` index without dropping existing course rows.
- Wired the persistent database builder to register `MIGRATION_1_2`.
- Added a focused Robolectric regression test that creates a version-1 SQLite database, runs the migration, and verifies the new table/index exist while legacy `courses` data remains available.
- Added the missing `assertNull` import in `DailyDatabaseTest` so the existing DAO coverage compiles once Gradle can write build outputs.

### Bounded verification and exact limitation

The fresh focused verification command for this round was:

```powershell
$env:JAVA_HOME='C:\Users\19857\AppData\Local\Temp\jdk17\jdk-17.0.20+8'
$env:GRADLE_USER_HOME='C:\Users\19857\OneDrive\文档\ChatGPT\APP\.worktrees\daily-rebuild\.gradle-user-home'
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.core.database.DailyDatabaseTest
```

Observed output:

```text
To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.11.1/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build

> Configure project :app
Unable to initialize metrics, ensure C:\Users\19857\.android is writable, details: C:\Users\19857\.android\analytics.settings (�ܾ����ʡ�)
WARNING: The option setting 'android.overridePathCheck=true' is experimental.
The current default is 'false'.

> Task :app:checkKotlinGradlePluginConfigurationErrors SKIPPED
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:generateDebugBuildConfig FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':app:generateDebugBuildConfig'.
> java.nio.file.AccessDeniedException: C:\Users\19857\OneDrive\�ĵ�\ChatGPT\APP\.worktrees\daily-rebuild\app\build\generated\source\buildConfig\debug\com\daily\life

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 9s
1 actionable task: 1 executed
```

### Fix-round self-review

- Confirmed the schema bump is the smallest coherent change: one version increment and one additive migration for the newly introduced table/index only.
- Confirmed the persistent Room builder now knows how to open existing version-1 installations instead of failing on schema mismatch.
- Confirmed the migration regression test exercises the exact compatibility risk called out in re-review rather than only asserting constants.
- Verification is still blocked by the Windows path write failure before compilation, so no fresh green test result can be claimed for this round.

### Fix-round commit

This round's source and report changes are committed together in the latest `codex/daily-rebuild` fix-round-2 commit created during this takeover.
