# 首页可爱风重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变首页真实数据与五个一级路由的前提下，将首页改造成带课程、日历、健康三种局部状态的可爱吉祥物界面。

**Architecture:** `HomeScreen` 持有可保存的 `HomeSection`，顶部三入口只改变该本地状态。`HomeViewModel` 继续组合既有摘要仓库；健康摘要仓库额外组合经期记录和周期偏好，提供首页所需的经期展示模型。资源全部是 Android 本地 drawable，底栏保持同一导航接口，仅替换视觉资源。

**Tech Stack:** Kotlin、Jetpack Compose、Material 3、Room DAO、Kotlin Flow、Android VectorDrawable、JUnit。

**Spec:** `docs/superpowers/specs/2026-09-01-homepage-cute-redesign-design.md`

## Global Constraints

- 首页顶部固定显示“首页”，三入口不得改变 `DailyDestination.Home` 路由或底栏选中状态。
- 保留真实课程、今日待办、体重、经期、设置和所有已有空状态的数据语义；不写入或迁移业务数据。
- 使用 `#F6F8E7`、`#F9F7EE`、`#204A0A`、`#B69DDB`、`#B1D685`、`#FFB246`；禁止明显渐变、玻璃拟态和新的大面积蓝色。
- 顶部小羊必须来自交付 zip 的原始素材，保持比例且不覆盖源文件。
- 课程状态不显示科目图标；待办状态不显示新建按钮、优先级标签或额外图标；健康状态只显示体重与经期两张主卡。
- 课程名、时间、地点与待办时间在窄屏可换行但不得被缩成难读的辅助文字。

---

## File Structure

- `feature/home/HomeState.kt`：首页展示模型、`HomeSection`、经期摘要与纯展示函数。
- `feature/home/HomeSummaryRepositories.kt`：从 `HealthDao`、`PeriodDao` 和偏好组合首页健康摘要。
- `feature/home/HomeScreen.kt`：顶部拱形入口、三种主体面板、Header 软糖花设置按钮。
- `core/navigation/DailyNavHost.kt`：为首页健康摘要注入 `PeriodDao`。
- `core/designsystem/DailyBottomBar.kt`：统一使用可爱底栏 drawable 和绿色选中底座。
- `res/drawable-nodpi/home_mascot_*.png`：三张可运行的小羊素材。
- `res/drawable/nav_cute_*.xml`：五个统一的胖圆底栏图标。
- 首页测试：`HomePresentationTest.kt` 与 `HomeViewModelTest.kt`。

### Task 1: 导入首页吉祥物资源并建立可爱底栏图标资源

**Files:**
- Create: `app/src/main/res/drawable-nodpi/home_mascot_course_reading.png`
- Create: `app/src/main/res/drawable-nodpi/home_mascot_calendar.png`
- Create: `app/src/main/res/drawable-nodpi/home_mascot_health_dumbbell.png`
- Create: `app/src/main/res/drawable/nav_cute_home.xml`
- Create: `app/src/main/res/drawable/nav_cute_timetable.xml`
- Create: `app/src/main/res/drawable/nav_cute_schedule.xml`
- Create: `app/src/main/res/drawable/nav_cute_health.xml`
- Create: `app/src/main/res/drawable/nav_cute_bill.xml`
- Modify: `app/src/main/java/com/daily/life/core/designsystem/DailyBottomBar.kt`

**Interfaces:**
- Consumes: `DailyDestination.primaryDestinations`、`DailyBottomBar(current, onDestinationSelected)`。
- Produces: `DailyDestination.cuteIconResource(): Int`，供五个底栏入口使用。

- [ ] **Step 1: 从交付包提取图片到临时目录，确认文件名和尺寸**

Run:

```powershell
$bundle = 'D:\19857\Documents\codex_homepage_redesign_bundle.zip'
$temp = Join-Path $env:TEMP 'daily-home-redesign-assets'
New-Item -ItemType Directory -Force -Path $temp
Expand-Archive -LiteralPath $bundle -DestinationPath $temp -Force
Get-ChildItem "$temp\assets\mascot_*.png" | Select-Object Name,Length
```

Expected: 三个 `mascot_*.png` 文件存在，且交付包未被写入。

- [ ] **Step 2: 复制三只小羊到 `drawable-nodpi`**

```powershell
Copy-Item "$temp\assets\mascot_course_reading.png" app/src/main/res/drawable-nodpi/home_mascot_course_reading.png
Copy-Item "$temp\assets\mascot_calendar.png" app/src/main/res/drawable-nodpi/home_mascot_calendar.png
Copy-Item "$temp\assets\mascot_health_dumbbell.png" app/src/main/res/drawable-nodpi/home_mascot_health_dumbbell.png
```

Expected: 图片保持方形原始比例，资源名只含小写字母和下划线。

- [ ] **Step 3: 新建五个深墨绿胖圆 VectorDrawable**

每个资源使用 `24dp` viewport 和 `#204A0A`：`nav_cute_home` 为圆弧屋顶与圆门，`nav_cute_timetable` 为翻开的圆角课本，`nav_cute_schedule` 为圆角便签和三条粗短线，`nav_cute_health` 为胖圆爱心与圆头哑铃，`nav_cute_bill` 为圆角小票和三段圆弧底边。

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#204A0A" android:pathData="M2,11 C2,7 5,4 9,4 L15,4 C19,4 22,7 22,11 L22,20 C22,21.1 21.1,22 20,22 L4,22 C2.9,22 2,21.1 2,20 Z M9,13 L15,13 L15,22 L9,22 Z" />
</vector>
```

路径使用一致的粗圆视觉，不使用 Material 系统图标、尖顶或锯齿底边。

- [ ] **Step 4: 修改底栏使所有状态使用 `cuteIconResource()`**

```kotlin
@DrawableRes
private fun DailyDestination.cuteIconResource(): Int = when (this) {
    DailyDestination.Home -> R.drawable.nav_cute_home
    DailyDestination.Timetable -> R.drawable.nav_cute_timetable
    DailyDestination.Schedule -> R.drawable.nav_cute_schedule
    DailyDestination.Health -> R.drawable.nav_cute_health
    DailyDestination.Bill -> R.drawable.nav_cute_bill
    DailyDestination.Settings -> R.drawable.nav_cute_home
}
```

`FigmaNavigationIcon` 改用无 tint 的 `Image`。选中态统一使用 `#DDEFC8`、18dp 圆角背景，仍以 `current == destination` 决定，不改变点击回调。

- [ ] **Step 5: 验证资源合并并提交**

Run: `./gradlew.bat --no-daemon :app:mergeDebugResources`

Expected: `BUILD SUCCESSFUL`，无非法资源名或 vector XML 错误。

```bash
git add app/src/main/res/drawable-nodpi app/src/main/res/drawable/nav_cute_*.xml app/src/main/java/com/daily/life/core/designsystem/DailyBottomBar.kt
git commit -m "feat: add cute home and navigation assets"
```

### Task 2: 扩展首页摘要以包含经期预测与真实课程时间

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeState.kt`
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeSummaryRepositories.kt`
- Modify: `app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt`
- Modify: `app/src/test/java/com/daily/life/feature/home/HomeViewModelTest.kt`
- Modify: `app/src/test/java/com/daily/life/feature/home/HomeSummaryRepositoriesTest.kt`

**Interfaces:**
- Consumes: `HealthDao.observeWeights()`、`PeriodDao.observeAll()`、`DailyPreferences.menstrualCycleDays`，以及现有 `SemesterPeriodDao.observeBySemester()`。
- Produces: `HealthHomeSummary(latestWeightJin, latestPeriod, nextPeriodStart, isEmpty)`；`HomeState` 透传同名字段；每个 `HomeCourseRow` 带有基于当前学期节次配置的 `timeLabel`。

- [ ] **Step 1: 写出经期摘要传播的失败测试**

```kotlin
@Test
fun healthPeriodSummaryPropagatesToHomeState() = runTest {
    val latest = HomePeriodSummary(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 6))
    val viewModel = HomeViewModel(
        timetableRepository = FakeTimetableSummaryRepository(),
        scheduleRepository = FakeScheduleSummaryRepository(),
        healthRepository = FakeHealthSummaryRepository(
            MutableStateFlow(HealthHomeSummary(latestWeightJin = 104.8, latestPeriod = latest, nextPeriodStart = LocalDate.of(2026, 9, 29)))
        ),
        billRepository = FakeBillSummaryRepository(),
        clock = fixedClock(),
        coroutineScope = backgroundScope
    )
    val state = viewModel.state.first { it.nextPeriodStart != null }
    assertEquals(LocalDate.of(2026, 9, 29), state.nextPeriodStart)
    assertEquals(latest, state.latestPeriod)
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.feature.home.HomeViewModelTest.healthPeriodSummaryPropagatesToHomeState`

Expected: 编译失败，因为 `HomePeriodSummary`、`latestPeriod` 和 `nextPeriodStart` 尚未定义。

- [ ] **Step 3: 定义模型并在 ViewModel 透传**

```kotlin
data class HomePeriodSummary(val startDate: LocalDate, val endDate: LocalDate)

data class HealthHomeSummary(
    val latestWeightJin: Double? = null,
    val latestPeriod: HomePeriodSummary? = null,
    val nextPeriodStart: LocalDate? = null,
    val isEmpty: Boolean = true
)
```

在 `HomeState` 增加 `latestPeriod: HomePeriodSummary?` 与 `nextPeriodStart: LocalDate?`，并在 `HomeViewModel.createHomeState` 赋值。

- [ ] **Step 4: 组合体重、经期与周期偏好**

将 `DaoHealthSummaryRepository` 构造函数扩展为 `(healthDao, periodDao, preferences)`，实现：

```kotlin
combine(healthDao.observeWeights(), periodDao.observeAll(), preferences.menstrualCycleDays) { weights, periods, cycleDays ->
    val latestPeriod = periods.maxByOrNull { it.startDate }
    HealthHomeSummary(
        latestWeightJin = weights.firstOrNull()?.weightJin,
        latestPeriod = latestPeriod?.let { HomePeriodSummary(it.startDate, it.endDate) },
        nextPeriodStart = latestPeriod?.startDate?.plusDays(cycleDays.coerceIn(21, 45).toLong()),
        isEmpty = weights.isEmpty() && periods.isEmpty()
    )
}
```

在 `DailyNavHost` 传入 `database.periodDao()` 与 `container.preferences`。

- [ ] **Step 5: 运行测试确认通过并提交**

Run: `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.feature.home.HomeViewModelTest`

Expected: 所有 `HomeViewModelTest` 用例通过。

```bash
git add app/src/main/java/com/daily/life/feature/home/HomeState.kt app/src/main/java/com/daily/life/feature/home/HomeSummaryRepositories.kt app/src/main/java/com/daily/life/core/navigation/DailyNavHost.kt app/src/test/java/com/daily/life/feature/home/HomeViewModelTest.kt
git commit -m "feat: expose period summary on home"
```

将 `DaoTimetableSummaryRepository` 扩展为接收 `SemesterPeriodDao`，在课程流与对应学期的节次时间流之间使用 `combine`。将课程开始节与结束节映射成例如 `08:00–09:35` 的 `timeLabel`；若学期尚未保存节次设置，则仅读取 `defaultSemesterPeriodTimes()` 作为展示回退，不向数据库写入默认值。将该 DAO 从 `DailyNavHost` 注入。`HomeCourseRow` 新增 `timeLabel`，现有 `detail` 继续代表地点。为默认节次时间和自定义节次时间各添加一条断言，确保首页不硬编码课程时间。

### Task 3: 建立首页局部状态与三种纯展示模型

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeState.kt`
- Modify: `app/src/test/java/com/daily/life/feature/home/HomePresentationTest.kt`

**Interfaces:**
- Consumes: `HomeState.todayCourses`、`todaySchedules`、`latestWeightJin`、`latestPeriod`、`nextPeriodStart`。
- Produces: `HomeSection`、`HomeHealthPresentation`、`healthHomePresentation(state)`。

- [ ] **Step 1: 写出健康和空状态的失败测试**

```kotlin
@Test
fun healthPresentationUsesWeightAndPeriodOnly() {
    val presentation = healthHomePresentation(
        HomeState(
            latestWeightJin = 104.8,
            latestPeriod = HomePeriodSummary(LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 9)),
            nextPeriodStart = LocalDate.of(2026, 9, 1)
        )
    )
    assertEquals("52.4", presentation.weightKg)
    assertEquals("6 天", presentation.periodValue)
    assertEquals("距离预计经期", presentation.periodCaption)
}

@Test
fun emptyHealthPresentationUsesNaturalCopy() {
    val presentation = healthHomePresentation(HomeState())
    assertEquals("尚未记录", presentation.weightKg)
    assertEquals("尚未记录经期", presentation.periodValue)
}
```

保留现有课程、待办“每条都展示”的回归测试。

- [ ] **Step 2: 运行展示测试确认失败**

Run: `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.feature.home.HomePresentationTest`

Expected: 编译失败，因为 `HomeSection`、`HomeHealthPresentation` 与 `healthHomePresentation` 未定义。

- [ ] **Step 3: 实现最小展示模型**

```kotlin
enum class HomeSection { COURSE, CALENDAR, HEALTH }

internal data class HomeHealthPresentation(
    val weightKg: String,
    val periodValue: String,
    val periodCaption: String
)

internal fun healthHomePresentation(state: HomeState): HomeHealthPresentation {
    val days = state.latestPeriod?.let { ChronoUnit.DAYS.between(it.startDate, it.endDate).toInt() + 1 }
    return HomeHealthPresentation(
        weightKg = state.latestWeightJin?.div(2.0)?.let { String.format(Locale.US, "%.1f", it) } ?: "尚未记录",
        periodValue = days?.let { "$it 天" } ?: "尚未记录经期",
        periodCaption = if (state.nextPeriodStart == null) "记录后可预测经期" else "距离预计经期"
    )
}
```

- [ ] **Step 4: 运行展示测试确认通过并提交**

Run: `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests com.daily.life.feature.home.HomePresentationTest`

Expected: 所有首页展示测试通过。

```bash
git add app/src/main/java/com/daily/life/feature/home/HomeState.kt app/src/test/java/com/daily/life/feature/home/HomePresentationTest.kt
git commit -m "feat: add home section presentations"
```

### Task 4: 重建首页为三入口与三种可爱主体面板

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `HomeState`、`HomeSection`、`healthHomePresentation(state)`、Task 1 的三个 mascot resource。
- Produces: 仅在 `HomeScreen` 内保存的三入口状态；顶部入口不调用导航。

- [ ] **Step 1: 写入稳定语义标签**

为三个入口添加 `home_section_course`、`home_section_calendar`、`home_section_health`，为主体添加 `home_content_course`、`home_content_calendar`、`home_content_health`。标签不改变可见文案，供 UI 树与后续截图核验。

- [ ] **Step 2: 实现页面壳和 Header**

```kotlin
var section by rememberSaveable { mutableStateOf(HomeSection.COURSE) }
Column(
    modifier = Modifier.fillMaxSize().background(HomePageBackground)
        .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp)
) {
    CuteHomeHeader(onOpenSettings)
    HomeSectionShell(section, onSectionSelected = { section = it })
}
```

Header 固定显示“首页”和“今天的重点，一眼看清。”；设置按钮用圆瓣组合成软糖花。`HomeSectionShell` 使用同一米白 `Surface` 连接低矮胖圆的三入口与下方主体，入口间只保留浅 U 型留白，选中入口没有描边。

- [ ] **Step 3: 实现课程和日历主体**

课程为“今日课程”，待办为“今日待办”，两者日期同行。每项只包含彩色序号圆块、标题和高对比时间/地点或时间，行间使用轻分隔线：

```kotlin
HomeListRow(index = index + 1, title = title, primaryDetail = course.timeLabel, secondaryDetail = location)
```

窄屏时标题可单独成行，时间和地点下一行且至少 14sp。课程不显示科目图标，待办不显示新建按钮、优先级标签或额外图标；空状态为自然文案且不导航。

- [ ] **Step 4: 实现健康主体**

“今日健康”下使用并排等权两卡。左卡为“今日体重 / 数值 / kg”或“尚未记录”；右卡为“经期 / 天数 / 距离预计经期”或空状态，强调色严格为 `#FFB246`。卡片纯色、大圆角、自适应高度；可用顶部健康小羊的小尺寸版本装饰体重卡，不生成新图片。

- [ ] **Step 5: 删除旧概览和跳转型卡片，编译并提交**

删除 `HomeOverviewCard` 与 `HomeContentCard` 的调用。顶部入口不得调用 `onDestinationSelected`；保留设置回调和底栏现有路由。

Run: `./gradlew.bat --no-daemon :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`。

```bash
git add app/src/main/java/com/daily/life/feature/home/HomeScreen.kt
git commit -m "feat: redesign home sections"
```

### Task 5: 端到端回归、视觉核验与成品 APK

**Files:**
- Modify: `app/src/test/java/com/daily/life/feature/home/HomePresentationTest.kt`（仅补足 Task 4 暴露的纯展示缺口）
- Output: `C:\Users\19857\OneDrive\文档\ChatGPT\APP\Daily-debug-homepage-cute-redesign-v8.apk`

**Interfaces:**
- Consumes: Tasks 1–4 的资源、数据模型和 `HomeScreen`。
- Produces: 已通过验证的 Debug APK，不改变数据库 schema。

- [ ] **Step 1: 运行首页与底栏相关单元测试**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.daily.life.feature.home.*' --tests 'com.daily.life.core.designsystem.*'
```

Expected: 全部目标测试通过。

- [ ] **Step 2: 构建 Debug APK**

Run: `./gradlew.bat --no-daemon :app:assembleDebug`

Expected: `BUILD SUCCESSFUL` 且生成 `app-debug.apk`。

- [ ] **Step 3: 执行视觉验收**

在 emulator 或真机检查：浅青绿背景与米白主体、无渐变、Header 始终为“首页”、三只羊为交付资源、三状态切换不改变底栏首页选中、健康经期为橙色、课程无科目图标、待办无新建与标签、底栏五图标均为胖圆深墨绿风。

- [ ] **Step 4: 复制成品、计算哈希并提交**

```powershell
$source = 'C:\Users\19857\AppData\Local\Temp\daily-gradle-build\app\outputs\apk\debug\app-debug.apk'
$target = 'C:\Users\19857\OneDrive\文档\ChatGPT\APP\Daily-debug-homepage-cute-redesign-v8.apk'
Copy-Item -LiteralPath $source -Destination $target -Force
Get-FileHash -Algorithm SHA256 $target
```

Expected: APK 存在、哈希已记录、工作树没有未预期代码改动。

```bash
git status --short
git commit -am "test: verify homepage cute redesign"
```
