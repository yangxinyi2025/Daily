# 首页日程地点与今日待办 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional location to schedules and render today's schedules in the home course-timeline style as “今日待办”.

**Architecture:** Persist `location` as an optional schedule-event field through Room, model mapping and backup snapshots. Map it into `HomeScheduleRow`, then reuse the course timeline's layout primitives for the schedule panel, with the only intentional visual difference being its “今日待办” heading and green accent.

**Tech Stack:** Kotlin, Jetpack Compose, Room, kotlinx serialization-free JSON snapshots, JUnit 4, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-02-home-schedule-location-design.md`

## Global Constraints

- Location is optional and blank editor input persists as `null`.
- Existing database rows and older snapshots must remain readable with a `null` location.
- Database version is raised from 11 to 12 with `MIGRATION_11_12`.
- The schedule tab remains inside the home screen; it must not navigate to the schedule destination.
- Home schedule rows show `无` when the location is absent.

---

### Task 1: Persist schedule locations

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/database/DailyEntities.kt`
- Modify: `app/src/main/java/com/daily/life/core/database/DailyDatabase.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleModels.kt`
- Modify: `app/src/test/java/com/daily/life/core/database/DailyDatabaseMigrationTest.kt`
- Create: `app/src/test/java/com/daily/life/feature/schedule/ScheduleLocationModelTest.kt`

**Interfaces:**
- Produces: `ScheduleEvent.location: String?`, `ScheduleEventEntity.location: String?`, `ScheduleEditorState.location: String`, and `DailyDatabase.MIGRATION_11_12`.
- Consumes: Existing `ScheduleEvent.toEntity()` and `ScheduleEventEntity.toModel()` conversions.

- [ ] **Step 1: Write failing model and migration tests**

```kotlin
@Test fun editorLocationPersistsThroughScheduleEventAndEntity() {
    val now = Instant.parse("2026-09-01T00:00:00Z")
    val event = ScheduleEditorState(title = "讲座", date = "2026-09-01", time = "14:00", location = "教学楼 A201")
        .toEvent(now, ZoneId.of("Asia/Shanghai"))
    assertEquals("教学楼 A201", event.toEntity().location)
    assertEquals("教学楼 A201", event.toEntity().toModel().location)
}

@Test fun migrationFrom11To12AddsNullableScheduleLocationWithoutDroppingRows() {
    val database = createVersion11Database().writableDatabase
    database.execSQL("""
        CREATE TABLE schedule_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL, eventAt INTEGER NOT NULL,
            reminderOffsetMinutes INTEGER NOT NULL, reminderMode TEXT NOT NULL,
            repeatYearly INTEGER NOT NULL, notes TEXT,
            isDismissed INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL
        )
    """.trimIndent())
    database.execSQL("""
        INSERT INTO schedule_events
        (id, title, eventAt, reminderOffsetMinutes, reminderMode, repeatYearly, notes, isDismissed, createdAt, updatedAt)
        VALUES (1, '已有日程', 0, 0, 'NOTIFICATION', 0, NULL, 0, 0, 0)
    """.trimIndent())
    DailyDatabase.MIGRATION_11_12.migrate(database)
    assertTrue(database.hasRow("SELECT name FROM pragma_table_info('schedule_events') WHERE name = 'location'"))
    assertTrue(database.hasRow("SELECT title FROM schedule_events WHERE title = '已有日程'"))
}
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleLocationModelTest --tests com.daily.life.core.database.DailyDatabaseMigrationTest --no-daemon --console=plain`

Expected: compilation failure because `location` and `MIGRATION_11_12` do not exist.

- [ ] **Step 3: Implement the nullable storage and mappings**

```kotlin
Add `val location: String? = null` immediately after `notes` on `ScheduleEvent` and `ScheduleEventEntity`, and `val location: String = ""` immediately after `repeatYearly` on `ScheduleEditorState`.

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE schedule_events ADD COLUMN location TEXT")
    }
}
```

Raise the Room version to 12, register the migration, and pass `location` through `from`, `toEvent`, `toEntity`, and `toModel`.

- [ ] **Step 4: Run the focused tests and verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleLocationModelTest --tests com.daily.life.core.database.DailyDatabaseMigrationTest --no-daemon --console=plain`

Expected: both new behaviors pass; any existing Robolectric environment failure is reported separately rather than masked.

- [ ] **Step 5: Commit Task 1**

```bash
git add app/src/main/java/com/daily/life/core/database/DailyEntities.kt app/src/main/java/com/daily/life/core/database/DailyDatabase.kt app/src/main/java/com/daily/life/feature/schedule/ScheduleModels.kt app/src/test/java/com/daily/life/core/database/DailyDatabaseMigrationTest.kt app/src/test/java/com/daily/life/feature/schedule/ScheduleLocationModelTest.kt
git commit -m "feat: persist schedule locations"
```

### Task 2: Edit and synchronize schedule locations

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleEditorScreen.kt`
- Modify: `app/src/main/java/com/daily/life/core/sync/SnapshotSerializer.kt`
- Modify: `app/src/test/java/com/daily/life/core/sync/SnapshotSerializerTest.kt`

**Interfaces:**
- Consumes: `ScheduleEditorState.location` and `ScheduleEventEntity.location` from Task 1.
- Produces: A “地点（可选）” field and snapshot JSON `scheduleEvents[].location` with null-safe old-snapshot decoding.

- [ ] **Step 1: Write the failing snapshot test**

```kotlin
@Test fun scheduleLocationRoundTripsInSnapshotsAndMissingFieldRemainsNull() {
    val event = ScheduleEventEntity(
        id = 1L, title = "讲座", eventAt = 1_700_000_000_000L,
        reminderOffsetMinutes = 0, reminderMode = ReminderMode.NOTIFICATION,
        repeatYearly = false, notes = null, location = "教学楼 A201",
        isDismissed = false, createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L
    )
    val encoded = serializer.encode(sampleSnapshot().copy(scheduleEvents = listOf(event)))
    assertEquals("教学楼 A201", serializer.decode(encoded).scheduleEvents.single().location)
    val oldJson = encoded.toString(Charsets.UTF_8).replace(",\"location\":\"教学楼 A201\"", "")
    assertNull(serializer.decode(oldJson.toByteArray()).scheduleEvents.single().location)
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests com.daily.life.core.sync.SnapshotSerializerTest --no-daemon --console=plain`

Expected: the serialized schedule JSON omits location or the model has no such field.

- [ ] **Step 3: Implement the editor and snapshot mapping**

Add an outlined `地点（可选）` field before `消息/备注`, bound to `state.copy(location = value)`. Encode location using `putNullable("location", v.location)` and decode it with `optionalString("location")` so older snapshots continue to decode.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `./gradlew.bat testDebugUnitTest --tests com.daily.life.core.sync.SnapshotSerializerTest --no-daemon --console=plain`

Expected: location round-trip and old-schema decode both pass.

- [ ] **Step 5: Commit Task 2**

```bash
git add app/src/main/java/com/daily/life/feature/schedule/ScheduleEditorScreen.kt app/src/main/java/com/daily/life/core/sync/SnapshotSerializer.kt app/src/test/java/com/daily/life/core/sync/SnapshotSerializerTest.kt
git commit -m "feat: edit and sync schedule locations"
```

### Task 3: Render today’s schedules in the home timeline

**Files:**
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeState.kt`
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeSummaryRepositories.kt`
- Modify: `app/src/main/java/com/daily/life/feature/home/HomeScreen.kt`
- Modify: `app/src/test/java/com/daily/life/feature/home/HomePresentationTest.kt`
- Modify: `app/src/test/java/com/daily/life/feature/home/HomeSummaryRepositoriesTest.kt`

**Interfaces:**
- Consumes: `ScheduleEventEntity.location` from Task 1.
- Produces: `HomeScheduleRow.location: String?` and `scheduleCardContent` items whose `primaryText`, `secondaryText`, and `trailingText` are title, resolved location, and time.

- [ ] **Step 1: Write failing home presentation tests**

```kotlin
@Test fun scheduleCardContentUsesTitleLocationAndTime() {
    val content = scheduleCardContent(HomeState(todaySchedules = listOf(
        HomeScheduleRow(1L, "讲座", "14:00", "教学楼 A201"),
        HomeScheduleRow(2L, "课后作业", "18:00", null)
    )))
    assertEquals(listOf("讲座", "课后作业"), content.items.map { it.primaryText })
    assertEquals(listOf("教学楼 A201", "无"), content.items.map { it.secondaryText })
    assertEquals(listOf("14:00", "18:00"), content.items.map { it.trailingText })
}
```

- [ ] **Step 2: Run the focused presentation test and verify it fails**

Run: `./gradlew.bat testDebugUnitTest --tests com.daily.life.feature.home.HomePresentationTest --no-daemon --console=plain`

Expected: constructor or `trailingText` assertions fail because home schedules do not yet carry locations or timeline values.

- [ ] **Step 3: Implement the home summary and shared timeline layout**

Add `location` to `HomeScheduleRow`; map it from `DaoScheduleSummaryRepository`. Map schedule cards to `HomeCardItem(title, location ?: "无", timeLabel)`. Extract the course panel's shared surface, date header, row and details into reusable private composables where needed, then render the schedule panel with `title = "今日待办"` and `accent = HomeTodoPill`. Retain the existing empty copy and home-local tab switching.

- [ ] **Step 4: Run the focused home tests and verify they pass**

Run: `./gradlew.bat testDebugUnitTest --tests com.daily.life.feature.home.HomePresentationTest --tests com.daily.life.feature.home.HomeSummaryRepositoriesTest --no-daemon --console=plain`

Expected: daily schedule titles, locations, times and empty state pass; course behavior remains covered.

- [ ] **Step 5: Commit Task 3**

```bash
git add app/src/main/java/com/daily/life/feature/home/HomeState.kt app/src/main/java/com/daily/life/feature/home/HomeSummaryRepositories.kt app/src/main/java/com/daily/life/feature/home/HomeScreen.kt app/src/test/java/com/daily/life/feature/home/HomePresentationTest.kt app/src/test/java/com/daily/life/feature/home/HomeSummaryRepositoriesTest.kt
git commit -m "feat: show schedule timeline on home"
```

### Task 4: Verify the integrated feature

**Files:**
- Modify only if verification exposes a direct defect in Tasks 1–3.

**Interfaces:**
- Consumes: all Task 1–3 production code and tests.
- Produces: fresh test and APK build evidence for the completed feature.

- [ ] **Step 1: Run the targeted test suite**

Run: `./gradlew.bat testDebugUnitTest --tests com.daily.life.feature.schedule.ScheduleLocationModelTest --tests com.daily.life.feature.home.HomePresentationTest --tests com.daily.life.feature.home.HomeSummaryRepositoriesTest --tests com.daily.life.core.sync.SnapshotSerializerTest --no-daemon --console=plain`

Expected: all targeted tests pass.

- [ ] **Step 2: Build a Debug APK**

Run: `./gradlew.bat assembleDebug --no-daemon --console=plain`

Expected: `BUILD SUCCESSFUL` and `app-debug.apk` exists under the configured Gradle build directory.

- [ ] **Step 3: Review the branch diff and commit any direct verification correction**

Run: `git diff master...HEAD --check` and `git status --short`

Expected: no whitespace errors and only intended tracked changes.
