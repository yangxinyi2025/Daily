# Daily Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从零构建一个名为 Daily、applicationId 为 `com.daily.life` 的中文优先 Android 应用，完整交付首页、课表、日程、健康、账单五个页面和四大本地优先功能模块。

**Architecture:** 使用单一 Android app module，以 Kotlin、Jetpack Compose 和 Material 3 实现 UI；以 Room 保存结构化业务数据，DataStore 保存非敏感配置，Keystore 保存秘密。页面通过 ViewModel 和 repository 访问本地数据，通知、解析、健康、AI 和 WebDAV 使用接口隔离，保证核心功能在离线和权限受限时仍可用。

**Tech Stack:** Kotlin；Android SDK 36；minSdk 26；Jetpack Compose；Material 3；Navigation Compose；Room；DataStore Preferences；WorkManager；AlarmManager；Android Keystore；Health Connect；PDFBox；Apache POI；OpenCSV；OkHttp；JUnit；Compose UI Test。

**Spec:** `docs/superpowers/specs/2026-08-20-daily-rebuild-design.md`

## Global Constraints

- 应用名称必须为 `Daily`，applicationId 必须为 `com.daily.life`。
- 第一版必须包含：首页、课表、日程、健康、账单五个页面。
- 第一版必须完整实现：课表、日程、健康、账单四大功能模块。
- UI 使用原生 Compose，不使用 WebView；Quiet Sky Unified HTML 仅作为视觉参考。
- 核心数据本地可用，网络只用于可选的 DeepSeek 建议和 Nutstore WebDAV 备份/同步。
- 不添加广告、行为画像或与核心功能无关的第三方统计 SDK。
- 金额使用分为单位的整数保存；体重使用斤保存；日期时间按本地时区展示并明确保存。
- 导入流程必须是选择文件、解析、预览、用户确认、正式写入；确认前不得修改正式数据。
- 通知、精确闹钟、健康访问和网络服务不得阻塞应用启动；健康前台服务不得在启动时运行。
- DeepSeek Key、WebDAV 用户名和密码不得明文写入源码、DataStore 或普通日志。
- 每个实现任务都先写可失败的测试，再写最小实现，再执行该任务的定向测试。
- 每个独立任务完成后创建一次小提交，提交信息使用 `feat:`、`test:`、`fix:` 或 `chore:` 前缀。
- 真实课表 PDF、微信账单和支付宝账单尚未提供；解析开发先使用仓库内合成 fixture，真实文件取得后必须追加回归样本。

---

## 文件地图

完成计划后，工程的主要文件职责如下：

```text
settings.gradle.kts                         Gradle plugin/dependency repositories and app module
build.gradle.kts                            Root plugin versions
gradle/libs.versions.toml                   Version catalog
app/build.gradle.kts                        Android/Compose/Room/WorkManager dependencies
app/src/main/AndroidManifest.xml             Permissions, receivers, services, launcher
app/src/main/java/com/daily/life/MainActivity.kt
app/src/main/java/com/daily/life/DailyApplication.kt
app/src/main/java/com/daily/life/core/designsystem/*
app/src/main/java/com/daily/life/core/database/*
app/src/main/java/com/daily/life/core/datastore/*
app/src/main/java/com/daily/life/core/security/*
app/src/main/java/com/daily/life/core/notification/*
app/src/main/java/com/daily/life/core/sync/*
app/src/main/java/com/daily/life/feature/home/*
app/src/main/java/com/daily/life/feature/timetable/*
app/src/main/java/com/daily/life/feature/schedule/*
app/src/main/java/com/daily/life/feature/health/*
app/src/main/java/com/daily/life/feature/bill/*
app/src/main/java/com/daily/life/feature/settings/*
app/src/test/java/com/daily/life/*                        JVM unit tests
app/src/androidTest/java/com/daily/life/*                Compose/database/device tests
app/src/test/resources/fixtures/*                        Synthetic PDF/CSV/XLSX fixtures
```

`core` 只包含跨功能基础能力；`feature` 目录包含对应页面、ViewModel、repository facade 和 feature-specific parser/use case。Composable 不直接调用 DAO、OkHttp、Health Connect 或 AlarmManager。

## Task 1: 创建可编译的 Daily Android 工程

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/daily/life/DailyApplication.kt`
- Create: `app/src/main/java/com/daily/life/MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/test/java/com/daily/life/ProjectSmokeTest.kt`

**Interfaces:**
- Produces an Android application whose `applicationId` is `com.daily.life`, whose label is `Daily`, and whose launcher Activity is `com.daily.life.MainActivity`.
- Produces Gradle tasks `:app:testDebugUnitTest` and `:app:assembleDebug` for all later tasks.

- [ ] **Step 1: Write the project smoke test**

```kotlin
package com.daily.life

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun applicationIdentityIsDaily() {
        assertEquals("com.daily.life", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 2: Run the smoke test and confirm the project is not yet configured**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.ProjectSmokeTest`

Expected: the command cannot run because the Android project files do not yet exist.

- [ ] **Step 3: Add the minimal Gradle and Android files**

Configure one `app` module with `namespace = "com.daily.life"`, `applicationId = "com.daily.life"`, `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`, version name `0.1.0`, and `buildConfig = true`. Add Compose, Material 3, Navigation Compose, Room, DataStore, WorkManager, OkHttp, PDFBox, POI and OpenCSV dependencies through the version catalog. Add only the manifest permissions needed by the design: network, notifications, exact alarms, activity recognition/health access, wake lock, boot completed, and the alarm foreground service declarations.

`DailyApplication` should only install the future dependency container and create no health service, network client request, parser job, or notification alarm at startup. `MainActivity` should call `setContent` with a temporary single `Text("Daily")` root.

- [ ] **Step 4: Run the smoke test and build**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.ProjectSmokeTest :app:assembleDebug`

Expected: PASS and a debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 5: Commit the project foundation**

```powershell
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/libs.versions.toml app
git commit -m "chore: scaffold Daily Android app"
```

## Task 2: Build the Quiet Sky design system and five-page navigation shell

**Files:**
- Create: `app/src/main/java/com/daily/life/core/designsystem/DailyColors.kt`
- Create: `app/src/main/java/com/daily/life/core/designsystem/DailyTypography.kt`
- Create: `app/src/main/java/com/daily/life/core/designsystem/DailyShapes.kt`
- Create: `app/src/main/java/com/daily/life/core/designsystem/DailyTheme.kt`
- Create: `app/src/main/java/com/daily/life/core/designsystem/DailyComponents.kt`
- Create: `app/src/main/java/com/daily/life/core/designsystem/DailyBottomBar.kt`
- Create: `app/src/main/java/com/daily/life/core/navigation/DailyDestination.kt`
- Create: `app/src/main/java/com/daily/life/core/navigation/DailyRootState.kt`
- Create: `app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt`
- Modify: `app/src/main/java/com/daily/life/MainActivity.kt`
- Create: `app/src/test/java/com/daily/life/core/navigation/DailyDestinationTest.kt`
- Create: `app/src/androidTest/java/com/daily/life/core/navigation/DailyNavigationTest.kt`

**Interfaces:**
- `DailyDestination`: sealed destinations `Home`, `Timetable`, `Schedule`, `Health`, `Bill`, and `Settings` with stable route strings.
- `DailyRootState`: `data class DailyRootState(val currentDestination: DailyDestination, val onDestinationSelected: (DailyDestination) -> Unit)`.
- `DailyNavHost(navController: NavHostController, rootState: DailyRootState)` renders all five primary pages and a settings destination.
- `DailyTheme(content: @Composable () -> Unit)` provides the Quiet Sky tokens.
- `DailyBottomBar(current: DailyDestination, onDestinationSelected: (DailyDestination) -> Unit)` exposes the five primary destinations.

- [ ] **Step 1: Write destination and navigation tests**

```kotlin
@Test
fun primaryDestinationsHaveStableRoutes() {
    assertEquals("home", DailyDestination.Home.route)
    assertEquals("timetable", DailyDestination.Timetable.route)
    assertEquals("schedule", DailyDestination.Schedule.route)
    assertEquals("health", DailyDestination.Health.route)
    assertEquals("bill", DailyDestination.Bill.route)
}
```

The Compose test should launch the root and assert the labels `首页`, `课表`, `日程`, `健康`, `账单`; tapping `健康` must show the health page label without recreating the Activity.

- [ ] **Step 2: Run the tests and confirm missing navigation types fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.navigation.DailyDestinationTest`

Expected: FAIL because the destination and theme types are not defined.

- [ ] **Step 3: Implement the shared tokens, components, and navigation shell**

Define the colors from the spec (`#F7FAFF`, `#111A32`, `#5E8CFF`, `#76B8C7`, `#6DA987`, `#9188E8`, `#D9909C`), 16/22/26/28dp shapes, light card elevation, system Chinese typography, page scaffold, card, primary action, empty state, error state and bottom navigation. Create five temporary page composables with correct titles and an empty-state message; later tasks replace each page body without changing routes.

- [ ] **Step 4: Run unit, Compose, and APK checks**

Run: `./gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug`

Expected: PASS; the emulator displays all five destinations and the app label is `Daily`.

- [ ] **Step 5: Commit the navigation shell**

```powershell
git add app/src/main/java/com/daily/life/core/designsystem app/src/main/java/com/daily/life/core/navigation app/src/main/java/com/daily/life/MainActivity.kt app/src/test app/src/androidTest
git commit -m "feat: add Quiet Sky navigation shell"
```

## Task 3: Add Room, DataStore, Keystore, and test data interfaces

**Files:**
- Create: `app/src/main/java/com/daily/life/core/database/DailyDatabase.kt`
- Create: `app/src/main/java/com/daily/life/core/database/DailyEntities.kt`
- Create: `app/src/main/java/com/daily/life/core/database/DailyDaos.kt`
- Create: `app/src/main/java/com/daily/life/core/database/DailyConverters.kt`
- Create: `app/src/main/java/com/daily/life/core/datastore/DailyPreferences.kt`
- Create: `app/src/main/java/com/daily/life/core/security/SecretStore.kt`
- Create: `app/src/main/java/com/daily/life/core/security/AndroidSecretStore.kt`
- Create: `app/src/main/java/com/daily/life/core/AppContainer.kt`
- Modify: `app/src/main/java/com/daily/life/DailyApplication.kt`
- Create: `app/src/test/java/com/daily/life/core/database/DailyDatabaseTest.kt`
- Create: `app/src/test/java/com/daily/life/core/security/SecretStoreTest.kt`

**Interfaces:**
- Entities: `SemesterEntity`, `CourseEntity`, `ScheduleEventEntity`, `WeightRecordEntity`, `ActivityRecordEntity`, `MonthlyReportEntity`, `TransactionEntity`, `BudgetEntity`, `ImportLogEntity`.
- DAOs: `SemesterDao`, `CourseDao`, `ScheduleEventDao`, `HealthDao`, `TransactionDao`, `BudgetDao`, `ImportLogDao` with suspend CRUD and Flow query methods.
- `DailyPreferences`: `val currentSemesterId: Flow<Long?>`, `val targetWeightJin: Flow<Int?>`, `val defaultBudgetCents: Flow<Long?>`, `suspend fun setTargetWeightJin(value: Int?)`, `suspend fun setDefaultBudgetCents(value: Long?)`.
- `SecretId`: enum values `DeepSeekApiKey`, `WebDavUsername`, and `WebDavPassword`.
- `SecretStore`: `suspend fun put(key: SecretId, value: String)`, `suspend fun read(key: SecretId): String?`, `suspend fun remove(key: SecretId)`.
- `AppContainer`: exposes database, preferences, secretStore and all repositories/adapter factories without performing external work in its constructor.

- [ ] **Step 1: Write failing database and secret tests**

```kotlin
@Test
fun transactionAmountIsStoredAsCents() = runTest {
    val record = TransactionEntity(id = 1L, occurredAt = 1_700_000_000_000L, amountCents = 12345L, direction = "EXPENSE", category = "餐饮", counterparty = "测试商户", source = "TEST")
    dao.insert(record)
    assertEquals(12345L, dao.findById(1L)!!.amountCents)
}

@Test
fun secretStoreReturnsValueWithoutWritingItToPreferences() = runTest {
    store.put(SecretId.DeepSeekApiKey, "test-key")
    assertEquals("test-key", store.read(SecretId.DeepSeekApiKey))
}
```

- [ ] **Step 2: Run the tests and verify they fail before implementation**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.database.DailyDatabaseTest --tests com.daily.life.core.security.SecretStoreTest`

Expected: FAIL because the entities, DAO, and secret store are absent.

- [ ] **Step 3: Implement the schema and local infrastructure**

Create Room entities with the exact fields in the design spec. Use type converters for enums and local dates where needed. Keep `amountCents: Long`, `weightJin: Double`, `reminderOffsetMinutes: Int`, and `repeatYearly: Boolean` explicit. Add indices for event time, transaction time/category/direction, course semester/week/day, and report month. Implement DataStore only for non-sensitive preferences. Implement `AndroidSecretStore` with Android Keystore-backed encrypted storage and a test fake that stores values only in memory.

- [ ] **Step 4: Run database, security, and startup tests**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

Expected: PASS; the application starts without reading Health Connect, opening a network connection, or scheduling a worker.

- [ ] **Step 5: Commit the local foundation**

```powershell
git add app/src/main/java/com/daily/life/core/database app/src/main/java/com/daily/life/core/datastore app/src/main/java/com/daily/life/core/security app/src/main/java/com/daily/life/core/AppContainer.kt app/src/main/java/com/daily/life/DailyApplication.kt app/src/test
git commit -m "feat: add local data and secret storage"
```

## Task 4: Implement settings and data-backed home page

**Files:**
- Create: `app/src/main/java/com/daily/life/feature/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/daily/life/feature/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/settings/SettingsState.kt`
- Create: `app/src/main/java/com/daily/life/feature/settings/SettingsModels.kt`
- Create: `app/src/main/java/com/daily/life/feature/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/daily/life/feature/home/HomeScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/home/HomeState.kt`
- Modify: `app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt`
- Create: `app/src/test/java/com/daily/life/feature/settings/SettingsViewModelTest.kt`
- Create: `app/src/test/java/com/daily/life/feature/home/HomeViewModelTest.kt`
- Create: `app/src/androidTest/java/com/daily/life/feature/home/HomeScreenTest.kt`

**Interfaces:**
- `WebDavConfigInput`: `data class WebDavConfigInput(val endpoint: String, val username: String, val password: String)`; the password is passed directly to `SecretStore` and is not retained in `SettingsState`.
- `SettingsState`: current semester name/start date, target weight, monthly budget, permission summaries, sync status.
- `SettingsViewModel`: `fun updateSemesterStartDate(date: LocalDate)`, `fun updateTargetWeightJin(value: Double?)`, `fun updateMonthlyBudgetCents(value: Long?)`, `fun saveDeepSeekKey(value: String)`, `fun saveWebDavConfig(config: WebDavConfigInput)`.
- `HomeCardState`: `data class HomeCardState(val title: String, val value: String, val actionLabel: String?, val destination: DailyDestination?)`.
- `HomeViewModel`: exposes `StateFlow<HomeState>` containing today course count/next course, upcoming events, latest weight/activity, monthly spending and budget.
- `HomeScreen(state: HomeState, onOpenSettings: () -> Unit, onDestinationSelected: (DailyDestination) -> Unit)` renders the five Quiet Sky summary cards and actionable empty states.

- [ ] **Step 1: Write failing ViewModel tests**

```kotlin
@Test
fun savingTargetWeightUpdatesPreferences() = runTest {
    viewModel.updateTargetWeightJin(132.5)
    assertEquals(132.5, preferences.targetWeightJin.first())
}

@Test
fun emptyHomeUsesActionableStates() = runTest {
    val state = viewModel.state.first()
    assertTrue(state.cards.any { it.actionLabel == "导入第一份课表" })
    assertTrue(state.cards.any { it.actionLabel == "记录今天体重" })
}
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.settings.SettingsViewModelTest --tests com.daily.life.feature.home.HomeViewModelTest`

Expected: FAIL because the states and ViewModels are absent.

- [ ] **Step 3: Implement settings and home state aggregation**

Use DataStore for semester start date, target weight and default budget. Route secret fields through `SecretStore`; never expose the actual key in `SettingsState`. Home reads flows from the timetable, schedule, health and bill repositories and combines them into one state. Use “你好，心怡” only if the user profile name is later configured; otherwise use a neutral `你好` greeting so the app does not hard-code personal identity.

- [ ] **Step 4: Implement and test the Compose layout**

Create the Quiet Sky home header, date row, course/event/health/bill cards, today actions, settings icon, and actionable empty states. Settings uses sections for semester date, goal, budget, DeepSeek and WebDAV, with masked secret fields and explicit save status. Verify that entering a value and navigating away preserves it.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug`

Expected: PASS; home displays local values and settings remain usable without network or health permission.

```powershell
git add app/src/main/java/com/daily/life/feature/home app/src/main/java/com/daily/life/feature/settings app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt app/src/test app/src/androidTest
git commit -m "feat: add home dashboard and settings"
```

## Task 5: Implement timetable calculation, PDF import preview, and timetable page

**Files:**
- Create: `app/src/main/java/com/daily/life/feature/timetable/WeekCalculator.kt`
- Create: `app/src/main/java/com/daily/life/feature/timetable/WeekRuleParser.kt`
- Create: `app/src/main/java/com/daily/life/feature/timetable/PdfTimetableParser.kt`
- Create: `app/src/main/java/com/daily/life/feature/timetable/TimetableRepository.kt`
- Create: `app/src/main/java/com/daily/life/feature/timetable/TimetableViewModel.kt`
- Create: `app/src/main/java/com/daily/life/feature/timetable/TimetableScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/timetable/TimetableImportScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/timetable/TimetableModels.kt`
- Create: `app/src/test/java/com/daily/life/feature/timetable/WeekCalculatorTest.kt`
- Create: `app/src/test/java/com/daily/life/feature/timetable/WeekRuleParserTest.kt`
- Create: `app/src/test/java/com/daily/life/feature/timetable/PdfTimetableParserTest.kt`
- Create: `app/src/test/resources/fixtures/timetable-synthetic.pdf`
- Create: `app/src/androidTest/java/com/daily/life/feature/timetable/TimetableImportTest.kt`

**Interfaces:**
- `WeekCalculator.currentWeek(startDate: LocalDate, date: LocalDate): Int` returns one-based week number, clamped to at least 1.
- `WeekRuleParser.parse(raw: String): WeekRuleResult` supports continuous ranges, comma-separated weeks, odd/even week markers and preserves warnings/raw text.
- `PdfTimetableParser.parse(input: InputStream): TimetableParseResult` returns parsed courses, warnings and unsupported rows without writing to Room.
- `TimetableRepository.confirmImport(preview: TimetableParseResult, semester: SemesterInput, replaceExisting: Boolean)` performs one transaction.
- `TimetableViewModel` exposes selected week, visible courses, import state and `fun confirmImport(...)`.

- [ ] **Step 1: Write failing week and parser tests**

```kotlin
@Test
fun weekOneStartsOnSemesterStartDate() {
    assertEquals(1, WeekCalculator.currentWeek(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1)))
    assertEquals(2, WeekCalculator.currentWeek(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8)))
}

@Test
fun parsesRangeAndOddEvenRules() {
    assertEquals(setOf(1, 2, 3, 4), WeekRuleParser.parse("1-4周").weeks)
    assertEquals(WeekParity.ODD, WeekRuleParser.parse("单周").parity)
    assertEquals(setOf(2, 4, 6), WeekRuleParser.parse("2,4,6周").weeks)
}
```

- [ ] **Step 2: Run the focused tests and confirm failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.WeekCalculatorTest --tests com.daily.life.feature.timetable.WeekRuleParserTest --tests com.daily.life.feature.timetable.PdfTimetableParserTest`

Expected: FAIL because the calculation, parser, and fixture reader are absent.

- [ ] **Step 3: Implement pure week calculation and rule parsing**

Use `ChronoUnit.DAYS.between(startDate, date) / 7 + 1`, return 1 for dates before the semester start, and test leap days/month boundaries. Parse Chinese and ASCII separators, ranges, `单周`, `双周`, and mixed expressions; preserve any unparsed text in `warnings`.

- [ ] **Step 4: Implement PDF parsing without persistence**

Read PDF text/table content off the main thread. Map weekday, start/end period, course name, weeks, location, teacher and optional metadata. Return a preview model with `needsReview` when a required field is missing or a week rule has warnings. The parser must close input resources and leave Room untouched.

- [ ] **Step 5: Implement preview confirmation and timetable UI**

The import screen has file selection, loading, warning list, editable preview rows, cancel and confirm actions. The confirm action writes `SemesterEntity` and `CourseEntity` in a Room transaction and only then schedules course reminders. The timetable screen shows previous/next week, current week label, a seven-column day grid, fixed 12-period time labels, course cards and details. Empty state links to PDF import.

- [ ] **Step 6: Run parser, database, Compose, and build checks**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.timetable.* :app:connectedDebugAndroidTest --tests com.daily.life.feature.timetable.TimetableImportTest :app:assembleDebug`

Expected: PASS; canceling or leaving preview does not change the existing timetable, and confirming replaces it only after explicit confirmation.

- [ ] **Step 7: Commit the timetable module**

```powershell
git add app/src/main/java/com/daily/life/feature/timetable app/src/test app/src/androidTest
git commit -m "feat: add timetable import and weekly view"
```

## Task 6: Implement schedule events, notification channels, and alarm flow

**Files:**
- Create: `app/src/main/java/com/daily/life/core/notification/ReminderScheduler.kt`
- Create: `app/src/main/java/com/daily/life/core/notification/ReminderReceiver.kt`
- Create: `app/src/main/java/com/daily/life/core/notification/BootReceiver.kt`
- Create: `app/src/main/java/com/daily/life/core/notification/AlarmActivity.kt`
- Create: `app/src/main/java/com/daily/life/core/notification/AlarmRingtoneService.kt`
- Create: `app/src/main/java/com/daily/life/feature/schedule/ScheduleRepository.kt`
- Create: `app/src/main/java/com/daily/life/feature/schedule/ScheduleViewModel.kt`
- Create: `app/src/main/java/com/daily/life/feature/schedule/ScheduleScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/schedule/ScheduleEditorScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/schedule/ScheduleModels.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/daily/life/core/notification/ReminderSchedulerTest.kt`
- Create: `app/src/test/java/com/daily/life/feature/schedule/ScheduleRepositoryTest.kt`
- Create: `app/src/androidTest/java/com/daily/life/feature/schedule/ScheduleScreenTest.kt`

**Interfaces:**
- `ReminderScheduler.schedule(event: ScheduleEvent)`, `cancel(eventId: Long)`, `rescheduleFutureEvents(now: Instant)`.
- `ReminderTimeCalculator.messageTrigger(event: ScheduleEvent): Instant` and `alarmTrigger(event: ScheduleEvent): Instant`.
- `ScheduleRepository.create`, `update`, `delete`, `observeBetween`, and `createYearlyInstancesIfNeeded`.
- `AlarmActivity` accepts `eventId` and a close action; `AlarmRingtoneService` stops only after the user closes the alarm or the system cancels it.

- [ ] **Step 1: Write failing reminder and yearly-repeat tests**

```kotlin
@Test
fun messageReminderUsesConfiguredOffset() {
    val event = eventAt("2026-08-21T10:00:00+08:00", reminderOffsetMinutes = 20)
    assertEquals(OffsetDateTime.parse("2026-08-21T09:40:00+08:00").toInstant(), ReminderTimeCalculator.messageTrigger(event))
}

@Test
fun birthdayRepeatsOnTheNextYear() {
    val event = yearlyBirthday("2025-08-21T09:00:00+08:00")
    assertEquals(2026, ScheduleRepository.nextOccurrence(event, LocalDate.of(2026, 1, 1)).year)
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.notification.ReminderSchedulerTest --tests com.daily.life.feature.schedule.ScheduleRepositoryTest`

Expected: FAIL because the scheduler, calculator, and event repository are absent.

- [ ] **Step 3: Implement event CRUD and schedule UI**

Implement month/week/day filters, date selection, timeline, create/edit/delete, and quick actions `考试`、`生日`、`小事`. Editor fields include title, local date/time, reminder offset, message/alarm mode, yearly repeat and notes. Keep unsaved editor state in the ViewModel until explicit save.

- [ ] **Step 4: Implement notification and alarm adapters**

Create notification channels on first use. Use exact AlarmManager only when the event requires it and the system allows it; otherwise save the event and expose a permission-restricted status. `ReminderReceiver` posts a message or launches the alarm flow. `BootReceiver` reschedules future events after boot, time zone change, time change, or package replacement. The alarm service uses a foreground notification and a user-visible close action; do not use an endless background loop.

- [ ] **Step 5: Run UI and notification tests**

Run: `./gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --tests com.daily.life.feature.schedule.ScheduleScreenTest :app:assembleDebug`

Expected: PASS; event creation works with notification permission denied, message trigger math is correct, yearly birthdays select the next occurrence, and closing an alarm stops its service.

- [ ] **Step 6: Commit the schedule module**

```powershell
git add app/src/main/java/com/daily/life/core/notification app/src/main/java/com/daily/life/feature/schedule app/src/main/AndroidManifest.xml app/src/test app/src/androidTest
git commit -m "feat: add schedule reminders and alarms"
```

## Task 7: Implement health records, activity adapters, charts, and monthly reports

**Files:**
- Create: `app/src/main/java/com/daily/life/feature/health/HealthRepository.kt`
- Create: `app/src/main/java/com/daily/life/feature/health/HealthViewModel.kt`
- Create: `app/src/main/java/com/daily/life/feature/health/HealthScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/health/WeightEditor.kt`
- Create: `app/src/main/java/com/daily/life/feature/health/HealthConnectAdapter.kt`
- Create: `app/src/main/java/com/daily/life/feature/health/SensorActivityAdapter.kt`
- Create: `app/src/main/java/com/daily/life/feature/health/MonthlyReportCalculator.kt`
- Create: `app/src/main/java/com/daily/life/feature/health/HealthModels.kt`
- Create: `app/src/test/java/com/daily/life/feature/health/MonthlyReportCalculatorTest.kt`
- Create: `app/src/test/java/com/daily/life/feature/health/HealthRepositoryTest.kt`
- Create: `app/src/androidTest/java/com/daily/life/feature/health/HealthScreenTest.kt`

**Interfaces:**
- `ActivityDataSource`: `suspend fun read(start: Instant, end: Instant): List<ActivityRecord>` and `suspend fun availability(): DataSourceAvailability`.
- `HealthRepository.recordWeight`, `setTargetWeight`, `readActivity`, `observeWeightRecords`, `generateMonthlyReport`.
- `MonthlyReportCalculator.calculate(month: YearMonth, weights: List<WeightRecord>, activities: List<ActivityRecord>, targetWeightJin: Double?): LocalReport`.
- `HealthViewModel` exposes weight, activity, report, permission and loading/error state without initializing adapters in its constructor.

- [ ] **Step 1: Write failing report and repository tests**

```kotlin
@Test
fun reportContainsWeeklyAverageAndMonthOverMonthChange() {
    val report = MonthlyReportCalculator.calculate(
        month = YearMonth.of(2026, 8),
        weights = listOf(weight("2026-07-31", 140.0), weight("2026-08-07", 138.0), weight("2026-08-14", 136.0)),
        activities = listOf(walk("2026-08-01", 5000), run("2026-08-02", 3000)),
        targetWeightJin = 130.0
    )
    assertEquals(137.0, report.monthAverageJin, 0.01)
    assertEquals(-4.0, report.monthOverMonthJin, 0.01)
    assertEquals(8000L, report.totalSteps)
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.health.MonthlyReportCalculatorTest --tests com.daily.life.feature.health.HealthRepositoryTest`

Expected: FAIL because the report calculator, repository, and adapters are absent.

- [ ] **Step 3: Implement local health records and report calculation**

Implement manual weight entry in jin, target weight, recent 30-day series, weekly average, month-over-month change, walking/running totals, and goal difference. Store local reports even when the AI service is unavailable. Define explicit empty and insufficient-data states.

- [ ] **Step 4: Implement lazy Health Connect and sensor adapters**

Health Connect adapter checks availability and requests permission only from the health screen action. Sensor adapter is injectable and returns a clear unavailable result when the device lacks the required sensor. Both adapters must be fakes in unit tests. No adapter call is allowed from `DailyApplication`, `MainActivity`, or the root navigation constructor.

- [ ] **Step 5: Implement health UI**

Build month selector, weight/activity/report tabs, 30-day line chart, record weight button, read phone data action, walking/running cards, target weight card, report summary and monthly feedback card using the design system. Display permission and source states inline.

- [ ] **Step 6: Run tests and check startup behavior**

Run: `./gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --tests com.daily.life.feature.health.HealthScreenTest :app:assembleDebug`

Expected: PASS; health page works with no Health Connect provider, manual records remain visible offline, and logcat shows no health foreground service at app launch.

- [ ] **Step 7: Commit the health module**

```powershell
git add app/src/main/java/com/daily/life/feature/health app/src/test app/src/androidTest
git commit -m "feat: add health records and monthly reports"
```

## Task 8: Implement bill parsing, classification, statistics, budget thresholds, and bill page

**Files:**
- Create: `app/src/main/java/com/daily/life/feature/bill/BillParser.kt`
- Create: `app/src/main/java/com/daily/life/feature/bill/CsvBillParser.kt`
- Create: `app/src/main/java/com/daily/life/feature/bill/ExcelBillParser.kt`
- Create: `app/src/main/java/com/daily/life/feature/bill/BillClassifier.kt`
- Create: `app/src/main/java/com/daily/life/feature/bill/BillRepository.kt`
- Create: `app/src/main/java/com/daily/life/feature/bill/BillViewModel.kt`
- Create: `app/src/main/java/com/daily/life/feature/bill/BillScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/bill/BillImportScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/bill/BillEditorScreen.kt`
- Create: `app/src/main/java/com/daily/life/feature/bill/BillModels.kt`
- Create: `app/src/test/java/com/daily/life/feature/bill/BillParserTest.kt`
- Create: `app/src/test/java/com/daily/life/feature/bill/BillClassifierTest.kt`
- Create: `app/src/test/java/com/daily/life/feature/bill/BudgetThresholdTest.kt`
- Create: `app/src/test/resources/fixtures/wechat-synthetic.csv`
- Create: `app/src/test/resources/fixtures/alipay-synthetic.csv`
- Create: `app/src/test/resources/fixtures/bills-synthetic.xlsx`
- Create: `app/src/androidTest/java/com/daily/life/feature/bill/BillImportTest.kt`

**Interfaces:**
- `BillParser.parse(input: InputStream, source: BillSource): BillParseResult` returns `BillPreviewRow` values, warnings, skipped rows and duplicate candidates without persistence.
- `BillClassifier.classify(counterparty: String, rawText: String, direction: Direction): Category` uses local deterministic rules.
- `BillRepository.confirmImport(preview: BillParseResult): ImportLogEntity` writes confirmed rows in one transaction.
- `BudgetThresholdCalculator.evaluate(spendingCents: Long, budgetCents: Long, triggered: Set<Int>): ThresholdResult` returns new thresholds and one-time notifications.
- `BillViewModel` exposes period, date range, direction filter, category filter, search text, summary, import state and editor state.

- [ ] **Step 1: Write failing CSV, Excel, classification, and budget tests**

```kotlin
@Test
fun parsesExpenseAmountAsPositiveCentsWithExpenseDirection() {
    val row = parser.parse(wechatFixture(), BillSource.WECHAT).rows.single()
    assertEquals(12345L, row.amountCents)
    assertEquals(Direction.EXPENSE, row.direction)
}

@Test
fun budgetNotifiesEachThresholdOnlyOnce() {
    val first = BudgetThresholdCalculator.evaluate(5000, 10000, emptySet())
    val second = BudgetThresholdCalculator.evaluate(7500, 10000, first.triggeredPercentages)
    assertEquals(setOf(50), first.newNotifications)
    assertEquals(setOf(70), second.newNotifications)
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.bill.BillParserTest --tests com.daily.life.feature.bill.BillClassifierTest --tests com.daily.life.feature.bill.BudgetThresholdTest`

Expected: FAIL because parsers, classifier and threshold calculator are absent.

- [ ] **Step 3: Implement CSV/Excel parsers and deterministic classification**

Recognize common WeChat and Alipay headers, UTF-8/GBK CSV encodings, dates, signed or direction-separated amounts, counterparties, payment methods, statuses and order IDs. Excel parsing must close workbooks and streams. Preserve raw row text and warnings. Use an explicit category rule table for food, transport, shopping, entertainment, bills, health, education, transfer, income and other; unknown rows become `其他` and remain editable.

- [ ] **Step 4: Implement preview, duplicate detection, confirmation, and repository statistics**

Create source/time/amount/counterparty/order-ID fingerprints. Show duplicate candidates before confirmation. Keep the preview in memory or a temporary ViewModel state until the user confirms. On confirmation write all accepted rows and an `ImportLogEntity` in one transaction. Add monthly/weekly/yearly totals, category totals, count, date-range filtering, direction filtering and search.

- [ ] **Step 5: Implement budget thresholds and bill UI**

Render month/week/year tabs, date range, count, budget progress, category spending, total spending, recent bills and import actions. Support single-record edit for date, amount, direction, category, counterparty and remark. Thresholds are exactly 50, 70, 90 and 110 percent, persisted per month and notified at most once per threshold.

- [ ] **Step 6: Run tests and verify preview safety**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.feature.bill.* :app:connectedDebugAndroidTest --tests com.daily.life.feature.bill.BillImportTest :app:assembleDebug`

Expected: PASS; synthetic CSV and XLSX fixtures parse, canceling preview leaves transaction count unchanged, confirmed rows use cents, duplicate candidates are visible, and budget notifications are one-time per month/threshold.

- [ ] **Step 7: Commit the bill module**

```powershell
git add app/src/main/java/com/daily/life/feature/bill app/src/test app/src/androidTest
git commit -m "feat: add bill import statistics and budgets"
```

## Task 9: Add optional DeepSeek reports, local snapshots, and Nutstore WebDAV sync

**Files:**
- Create: `app/src/main/java/com/daily/life/core/sync/SnapshotSerializer.kt`
- Create: `app/src/main/java/com/daily/life/core/sync/LocalBackupRepository.kt`
- Create: `app/src/main/java/com/daily/life/core/sync/WebDavClient.kt`
- Create: `app/src/main/java/com/daily/life/core/sync/WebDavSyncRepository.kt`
- Create: `app/src/main/java/com/daily/life/core/sync/SyncWorker.kt`
- Create: `app/src/main/java/com/daily/life/core/sync/SyncModels.kt`
- Create: `app/src/main/java/com/daily/life/feature/health/DeepSeekAdviceClient.kt`
- Create: `app/src/main/java/com/daily/life/feature/health/DeepSeekAdviceRepository.kt`
- Create: `app/src/test/java/com/daily/life/core/sync/SnapshotSerializerTest.kt`
- Create: `app/src/test/java/com/daily/life/core/sync/WebDavSyncRepositoryTest.kt`
- Create: `app/src/test/java/com/daily/life/feature/health/DeepSeekAdviceRepositoryTest.kt`
- Modify: `app/src/main/java/com/daily/life/feature/health/MonthlyReportCalculator.kt`

**Interfaces:**
- `SnapshotSerializer.encode(snapshot: DailySnapshot): ByteArray`, `decode(bytes: ByteArray): DailySnapshot` with schema version and updated timestamp.
- `LocalBackupRepository.createSnapshot(): File`, `restoreSnapshot(file: File, replaceExisting: Boolean)`.
- `WebDavClient.putTemp`, `replace`, `download`, `exists`.
- `WebDavSyncRepository.upload()`, `inspectRemote()`, `restoreRemote()` with explicit conflict result.
- `DeepSeekAdviceClient.generateAdvice(summary: HealthSummary, apiKey: String): Result<String>`.

- [ ] **Step 1: Write failing snapshot, conflict, and AI fallback tests**

```kotlin
@Test
fun snapshotRoundTripPreservesCentsAndSchemaVersion() {
    val decoded = serializer.decode(serializer.encode(sampleSnapshot))
    assertEquals(sampleSnapshot.schemaVersion, decoded.schemaVersion)
    assertEquals(12345L, decoded.transactions.single().amountCents)
}

@Test
fun missingKeyOrNetworkReturnsLocalReportWithoutThrowing() = runTest {
    val report = repository.generateAdviceOrLocalSummary(summary = sampleHealthSummary, apiKey = null)
    assertEquals(AdviceSource.LOCAL, report.source)
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.daily.life.core.sync.SnapshotSerializerTest --tests com.daily.life.core.sync.WebDavSyncRepositoryTest --tests com.daily.life.feature.health.DeepSeekAdviceRepositoryTest`

Expected: FAIL because snapshot, WebDAV and AI adapters are absent.

- [ ] **Step 3: Implement versioned local snapshots and restore safety**

Serialize all Room entities and necessary non-sensitive settings with schema version, device identifier, created time and updated time. Restore through a temporary database transaction or validated staging object. Before replacing local data, create a local safety copy. Reject unsupported schema versions and malformed records with a user-facing error.

- [ ] **Step 4: Implement WebDAV atomic upload, inspection, conflict choice, and retry worker**

Use OkHttp with WebDAV methods. Upload to a temporary remote path, verify the response, then replace the canonical snapshot. Compare local last-sync timestamp and remote timestamp; return `NoRemote`, `RemoteNewer`, `LocalNewer`, `Conflict` or `ReadyToRestore` rather than silently overwriting. Add a WorkManager worker with network constraint, bounded retry and a stored status string; automatic sync remains disabled until the user enables it.

- [ ] **Step 5: Implement DeepSeek optional advice and settings integration**

Send only the already-computed monthly health summary. Read the API key from `SecretStore`, use a bounded timeout, redact it from errors, and preserve the local report when the request fails. Add WebDAV and DeepSeek status cards to settings without showing secrets.

- [ ] **Step 6: Run tests and build an offline-compatible APK**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

Expected: PASS; removing network access does not break startup or local report generation, restore requires explicit confirmation, and no secret appears in test logs.

- [ ] **Step 7: Commit optional network and backup features**

```powershell
git add app/src/main/java/com/daily/life/core/sync app/src/main/java/com/daily/life/feature/health app/src/test
git commit -m "feat: add optional backup sync and health advice"
```

## Task 10: Complete integration verification, accessibility checks, and debug APK delivery

**Files:**
- Create: `app/src/androidTest/java/com/daily/life/DailyNavigationIntegrationTest.kt`
- Create: `app/src/androidTest/java/com/daily/life/DailyOfflineModeTest.kt`
- Create: `app/src/androidTest/java/com/daily/life/DailyPermissionDegradedModeTest.kt`
- Create: `app/src/test/java/com/daily/life/core/StartupSafetyTest.kt`
- Create: `docs/qa/daily-v1-test-matrix.md`
- Modify: `README.md`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- The complete app exposes five navigable pages, all module data flows through Room, optional integrations are injectable, and release checks can be run with Gradle commands from the repository root.

- [ ] **Step 1: Write integration tests for the complete user journey**

```kotlin
@Test
fun allFivePagesOpenFromBottomNavigation() {
    launchDaily()
    listOf("首页", "课表", "日程", "健康", "账单").forEach { label ->
        onNodeWithText(label).performClick()
        onNodeWithText(label).assertIsDisplayed()
    }
}

@Test
fun offlineModeStillSupportsLocalNavigationAndRecords() {
    launchDaily(networkAvailable = false)
    onNodeWithText("健康").performClick()
    onNodeWithText("记录体重").performClick()
    onNodeWithText("账单").performClick()
    onNodeWithText("导入账单").assertIsDisplayed()
}
```

- [ ] **Step 2: Run the complete test suite before release adjustments**

Run: `./gradlew.bat clean :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug`

Expected: PASS; no unit or emulator test fails.

- [ ] **Step 3: Verify permission-degraded and startup-safe behavior**

Install the debug APK on the test emulator with notification, exact alarm, health, and network access unavailable in turn. Confirm the app opens, local pages render, events can be saved, health manual records work, and bill previews can be canceled. Inspect logcat for absence of startup exceptions, startup health foreground-service activation, plaintext secrets, and uncaught parser exceptions.

- [ ] **Step 4: Perform the real-device acceptance pass**

On iQOO 13 / Android 16 verify: direct APK install, first launch speed, five-page navigation, PDF picker, CSV/XLSX picker, notification permission, exact alarm permission, full-screen alarm, boot rescheduling, Health Connect permission, screen density, back navigation, keyboard input, and Chinese text rendering. Record failures and fix them before delivery; do not declare parser compatibility for real files until actual samples are tested.

- [ ] **Step 5: Write the QA matrix and usage README**

Document every acceptance item in `docs/qa/daily-v1-test-matrix.md` with environment, action, expected result and actual result fields. Add `README.md` instructions for opening the project, running unit/UI tests, building `app-debug.apk`, entering optional secrets through the app, and using synthetic fixtures. Do not put any real key or account credential in the repository.

- [ ] **Step 6: Build and checksum the handoff APK**

Run:

```powershell
./gradlew.bat :app:assembleDebug
Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
```

Expected: a directly installable APK with label `Daily`, package `com.daily.life`, version `0.1.0`, and a recorded SHA-256 value in the handoff note.

- [ ] **Step 7: Commit the verified first release**

```powershell
git add README.md docs/qa app/src/main app/src/test app/src/androidTest
git commit -m "chore: verify Daily v0.1 debug release"
```

## Self-review checklist

- [ ] Requirements and APK findings are represented as context only; the new name and package are taken from the user's explicit request.
- [ ] The plan includes all five pages: home, timetable, schedule, health, and bill.
- [ ] The plan includes all four modules: timetable PDF import/reminders, schedule/reminders, health/reports, and bills/import/budget.
- [ ] Every external integration has a local fallback and an injectable test fake.
- [ ] Import preview cannot write confirmed data before the user confirms.
- [ ] Startup avoids Health Connect initialization, network requests, and long-running foreground services.
- [ ] The plan has tests for week rules, reminders, reports, bills, budgets, snapshots, UI navigation, offline mode, and permissions.
- [ ] No API key, WebDAV credential, real bill, or real personal data is committed.
- [ ] Every task names concrete files, interfaces, tests, commands, expected results, and a commit boundary; no task relies on an unspecified future action.
