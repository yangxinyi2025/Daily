# Daily 真正闹钟 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Daily 的“闹钟提醒”在后台、锁屏和息屏时通过 Android 精确闹钟播放铃声和震动，并显示关闭入口。

**Architecture:** 消息提醒继续由系统日历的通知提醒执行。闹钟提醒改为 `AlarmManager.setAlarmClock` → 静态广播接收器 → 前台铃声服务与全屏闹钟通知；系统日历仅镜像闹钟事件，不为其写入日历提醒。重启等系统事件由接收器读取本地未来闹钟并重新登记。

**Tech Stack:** Kotlin、Jetpack Compose、Android AlarmManager、NotificationCompat、Foreground Service、Room、Robolectric、Android unit tests。

**Spec:** `docs/superpowers/specs/2026-08-25-daily-true-alarm-design.md`

## Global Constraints

- 仅 `ReminderMode.ALARM` 使用 Daily 的精确闹钟；`ReminderMode.NOTIFICATION` 不得启动 Daily 铃声服务。
- 使用 `AlarmManager.setAlarmClock` 与 `PendingIntent`，不使用已失败的日历 `METHOD_ALARM` 作为执行通道。
- 保存前必须检查精确闹钟授权；未授权不得显示“已设置闹钟”。
- 闹钟事件仍镜像到系统日历，但不创建系统日历提醒；消息事件维持已有日历通知提醒。
- 不能删除历史日程、课表、账单、经期或数据库迁移代码。
- 不提交或重置现有工作区的改动。

---

### Task 1: 建立精确闹钟注册器

**Files:**
- Modify: `app/src/main/java/com/daily/life/core/notification/ReminderScheduler.kt`
- Modify: `app/src/main/java/com/daily/life/core/AppContainer.kt`
- Test: `app/src/test/java/com/daily/life/core/notification/ReminderSchedulerTest.kt`

**Interfaces:**
- Consumes: `ScheduleEvent`, `ReminderMode`, `DailyDatabase`。
- Produces: `AndroidReminderScheduler.schedule(event)` 使用 `setAlarmClock`，`cancel(eventId)` 取消相同 `PendingIntent`，`rescheduleFutureEvents(now)` 只重新登记未来闹钟。

- [ ] **Step 1: 写出失败测试**

```kotlin
@Test
fun alarmModeUsesAlarmClockAndNotificationModeDoesNotRegisterDailyAlarm() = runTest {
    val scheduler = AndroidReminderScheduler(fakeContext, database, fakeAlarmManager)
    scheduler.schedule(alarmEvent)
    scheduler.schedule(notificationEvent)
    assertEquals(alarmEvent.id, fakeAlarmManager.lastAlarmClockEventId)
    assertEquals(1, fakeAlarmManager.alarmClockCount)
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.daily.life.core.notification.ReminderSchedulerTest" --console=plain`

Expected: FAIL，因为当前实现没有 `setAlarmClock` 行为。

- [ ] **Step 3: 实现最小注册与取消逻辑**

```kotlin
if (event.reminderMode == ReminderMode.ALARM) {
    alarmManager.setAlarmClock(
        AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
        receiverPendingIntent
    )
}
```

为 `SCHEDULE_EXACT_ALARM` 未授权返回 `PERMISSION_RESTRICTED`；为每个日程 ID 使用稳定 request code 和 action。

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.daily.life.core.notification.ReminderSchedulerTest" --console=plain`

Expected: PASS。

### Task 2: 到点响铃、震动与锁屏关闭入口

**Files:**
- Create: `app/src/main/java/com/daily/life/core/notification/AlarmActivity.kt`
- Create: `app/src/main/java/com/daily/life/core/notification/AlarmRingtoneService.kt`
- Create: `app/src/main/java/com/daily/life/core/notification/ReminderReceiver.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/daily/life/core/notification/ReminderReceiverTest.kt`

**Interfaces:**
- Consumes: 精确闹钟广播的 `eventId`、标题、备注。
- Produces: `ReminderReceiver` 启动前台服务并发布 `CATEGORY_ALARM` 全屏通知；`AlarmActivity` 与通知操作可关闭同一日程的铃声。

- [ ] **Step 1: 写出失败测试**

```kotlin
@Test
fun alarmBroadcastStartsRingingServiceAndBuildsAlarmNotification() {
    val dispatch = ReminderReceiver.alarmDispatch(context, 12L, "考试", "带准考证")
    assertEquals(12L, dispatch.eventId)
    assertTrue(dispatch.notification.category == Notification.CATEGORY_ALARM)
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.daily.life.core.notification.ReminderReceiverTest" --console=plain`

Expected: FAIL，因为铃声接收器、服务和关闭动作已不存在。

- [ ] **Step 3: 实现铃声服务和关闭动作**

```kotlin
startForeground(NOTIFICATION_ID, alarmNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
ringtone?.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
ringtone?.play()
vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
```

声明接收器、前台服务和 `showWhenLocked`／`turnScreenOn` Activity；关闭时停止声音、震动、服务和通知。

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.daily.life.core.notification.ReminderReceiverTest" --console=plain`

Expected: PASS。

### Task 3: 权限、系统日历镜像与日程保存状态

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleScreen.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleViewModel.kt`
- Modify: `app/src/main/java/com/daily/life/feature/schedule/ScheduleRepository.kt`
- Modify: `app/src/main/java/com/daily/life/core/calendar/CalendarReminderSyncer.kt`
- Test: `app/src/test/java/com/daily/life/feature/schedule/ScheduleViewModelTest.kt`

**Interfaces:**
- Consumes: 日程编辑器的提醒模式、系统权限状态。
- Produces: 保存闹钟时请求精确闹钟与通知权限；闹钟镜像事件不在系统日历写提醒；状态文案区分“闹钟已登记”和“未获授权”。

- [ ] **Step 1: 写出失败测试**

```kotlin
@Test
fun savingAnAlarmWithExactPermissionShowsRegisteredAlarmTime() = runTest {
    viewModel.startCreate()
    viewModel.updateReminderMode(ReminderMode.ALARM)
    viewModel.saveEditor()
    assertEquals("闹钟已登记", viewModel.state.value.statusMessage)
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.daily.life.feature.schedule.ScheduleViewModelTest" --console=plain`

Expected: FAIL，因为当前状态把闹钟交给系统日历。

- [ ] **Step 3: 实现保存分流**

```kotlin
if (event.reminderMode == ReminderMode.ALARM) {
    calendarMirror.syncSchedule(event.withoutCalendarReminder())
    reminderScheduler.schedule(event)
} else {
    calendarReminderSyncer.syncSchedule(event)
}
```

添加 `POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM`、前台服务和全屏通知声明；在用户从系统授权页返回后重新调用保存或登记。

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.daily.life.feature.schedule.ScheduleViewModelTest" --console=plain`

Expected: PASS。

### Task 4: 重启重登记与闹钟自检

**Files:**
- Create: `app/src/main/java/com/daily/life/core/notification/BootReceiver.kt`
- Modify: `app/src/main/java/com/daily/life/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/daily/life/feature/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/daily/life/core/notification/BootReceiverTest.kt`
- Test: `app/src/test/java/com/daily/life/feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `BOOT_COMPLETED`、时区变化、时间修改、应用更新广播和本地日程数据库。
- Produces: 未来 Daily 闹钟被重新登记；设置页显示权限、下次闹钟和最近到点记录。

- [ ] **Step 1: 写出失败测试**

```kotlin
@Test
fun bootReceiverReschedulesOnlyFutureAlarmEvents() = runTest {
    receiver.handleBroadcast(bootIntent)
    assertEquals(listOf(futureAlarm.id), scheduler.rescheduledIds)
}
```

- [ ] **Step 2: 运行失败测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.daily.life.core.notification.BootReceiverTest" --console=plain`

Expected: FAIL，因为开机重登记接收器不存在。

- [ ] **Step 3: 实现重登记与自检数据**

```kotlin
if (intent.action in RESCHEDULE_ACTIONS) {
    AndroidReminderScheduler(app, app.container.database).rescheduleFutureEvents(Instant.now())
}
```

记录最近一次登记、到点广播和启动铃声服务的时间；设置页只展示诊断信息，不恢复旧的无用权限入口。

- [ ] **Step 4: 运行测试验证通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.daily.life.core.notification.BootReceiverTest" --tests "com.daily.life.feature.settings.SettingsViewModelTest" --console=plain`

Expected: PASS。

### Task 5: 集成验证与设备验收 APK

**Files:**
- Test: `app/src/test/java/com/daily/life/core/calendar/SystemCalendarGatewayTest.kt`
- Test: `app/src/test/java/com/daily/life/core/notification/ReminderSchedulerTest.kt`
- Output: `C:/Users/19857/AppData/Local/Temp/daily-gradle-build/app/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: 所有前述组件。
- Produces: 已构建的 Debug APK 与明确的真机验收步骤。

- [ ] **Step 1: 运行完整单元测试**

Run: `./gradlew :app:testDebugUnitTest --console=plain`

Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 构建 Debug APK**

Run: `./gradlew :app:assembleDebug --console=plain`

Expected: BUILD SUCCESSFUL，输出 APK 存在。

- [ ] **Step 3: 真机验收**

在 iQOO 13 上允许 Daily 的“闹钟和提醒”、通知和全屏通知；设置 2 分钟后的闹钟，按主页键后锁屏。到点确认铃声、震动、锁屏关闭入口；再创建消息提醒确认不会响 Daily 铃声。
