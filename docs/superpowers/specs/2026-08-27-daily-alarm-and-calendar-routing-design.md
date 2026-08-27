# Daily 闹钟与系统日历提醒分流设计

## 目标

让 Daily 的普通日程根据提醒方式走两条完全独立的通道：选择“消息提醒”时写入系统日历并由系统日历负责提醒；选择“闹钟提醒”时不写入系统日历，改由 Daily 自己注册精确闹钟，在 Daily 不在前台、锁屏或息屏状态下触发响铃与全屏提醒。

课程和生日提醒仍然固定使用系统日历消息提醒，不允许切换为 Daily 自有闹钟。

## 现状与问题

- `ReminderMode` 数据枚举已经包含 `NOTIFICATION` 和 `ALARM`，但日程编辑器和 `ScheduleEditorState.toEvent()` 当前把提醒方式固定为 `NOTIFICATION`。
- `RoomScheduleRepository` 当前对所有日程都调用 `CalendarReminderSyncer`，并把系统日历提醒固定写成 `METHOD_ALERT`。
- Daily 之前的自有响铃代码已删除，因此目前没有 `AlarmManager`、开机重登记、锁屏响铃 Activity 或前台响铃服务。
- iQOO/vivo 系统可能限制精确闹钟、后台启动、通知和全屏提醒；应用必须显示真实权限状态，不得把受限状态报告为保存成功。

## 设计决策

### 提醒路由

使用下面的确定性规则：

```text
课程                 -> 系统日历 + 消息提醒
生日（每年重复日程） -> 系统日历 + 消息提醒
普通日程 + 消息提醒  -> 系统日历 + 消息提醒
普通日程 + 闹钟提醒  -> Daily 自有精确闹钟，不写入系统日历
```

闹钟日程不会在系统日历创建事件，也不会创建系统日历提醒，从根源上避免系统日历把 `METHOD_ALARM` 降级为通知或清空提醒造成重复/失效。

### Daily 自有闹钟链路

1. 保存普通闹钟日程时，先检查精确闹钟权限；权限不足时不保存为已生效状态，并返回可读的权限提示。
2. 使用 `AlarmManager.setAlarmClock()` 注册 `RTC_WAKEUP` 闹钟；Android 12 及以上使用 `canScheduleExactAlarms()` 判断权限，较低版本使用系统能力。
3. `PendingIntent` 携带日程 ID、标题、目标时间和提醒提前量，使用稳定 request code，编辑时覆盖同一日程旧闹钟。
4. 到点由显式 `BroadcastReceiver` 接收，立即启动前台响铃服务。
5. 前台服务创建 `USAGE_ALARM`/`STREAM_ALARM` 音频通道，循环播放系统闹钟铃声，并发布 `CATEGORY_ALARM`、`VISIBILITY_PUBLIC` 的高优先级通知。
6. 通知配置全屏 Intent，打开一个锁屏可见、可点亮屏幕的响铃 Activity；Activity 提供停止/稍后提醒操作。
7. 设备重启、时区变化、系统时间变化和应用更新后，Receiver 从数据库读取所有未来的 Daily 闹钟并重新注册。

### 消息提醒链路

普通消息日程仍由 `CalendarReminderSyncer` 写入系统日历，使用 `CalendarContract.Reminders.METHOD_ALERT`。系统日历事件的创建、更新、删除和同步链接维持现有业务逻辑。

生日日程继续写入系统日历并保留每年重复。课程提醒不改动现有同步逻辑。

### 编辑与删除

- 消息日程编辑：更新对应系统日历事件和系统日历提醒。
- 闹钟日程编辑：取消旧 `PendingIntent` 后按新时间重新注册，不创建系统日历事件。
- 消息日程切换为闹钟：删除 Daily 对应的系统日历事件，再注册 Daily 闹钟。
- 闹钟日程切换为消息：取消 Daily 闹钟，再创建系统日历事件。
- 删除任意日程：同时清除相应通道的提醒资源；系统日历同步链接和 Daily 闹钟不能残留。

### 权限与失败处理

Manifest 和运行时处理覆盖：

- `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`
- `POST_NOTIFICATIONS`
- `USE_FULL_SCREEN_INTENT`
- `RECEIVE_BOOT_COMPLETED`
- `FOREGROUND_SERVICE` 与 Android 14 所需的前台服务类型
- `VIBRATE` 与 `WAKE_LOCK`

若 iQOO 系统关闭“闹钟与提醒”、通知、全屏弹出或后台运行，Daily 显示具体受限项目，并引导进入对应系统设置。不能保证绕过 ROM 限制；但所有保存状态、日志和测试结果必须反映真实状态。

## 测试策略

先写单元测试覆盖：

- 课程/生日/消息/闹钟的路由决策。
- 闹钟目标时间等于事件时间减提前分钟数。
- 过去的触发时间不会注册为未来闹钟。
- 编辑使用相同日程 ID 覆盖旧闹钟。
- 删除会取消闹钟。
- 闹钟路由不产生系统日历同步请求。
- 消息路由不产生 Daily 自有闹钟请求。

再运行 Android 构建和模拟器验证：保存消息日程可在系统日历看到；保存闹钟日程不出现在系统日历，但 `dumpsys alarm` 有 Daily 闹钟；离开 Daily、锁屏后通过短延时测试观察响铃服务、公开通知和锁屏 Activity。真机 iQOO 13 仍需用户手动确认 ROM 权限开关。

## 范围外

- 不修改课程 PDF 解析和课程时间数据。
- 不恢复已经删除的健康步数/Health Connect/月报逻辑。
- 不改变首页、日程页面既有视觉设计，只补回提醒方式选择和必要的权限状态提示。
