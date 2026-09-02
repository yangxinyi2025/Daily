# Daily

Daily 是一个离线优先的个人 Android 生活管理应用，包名为 `com.daily.life`，当前版本 `0.1.0`。第一版包含首页、课表、日程、健康和账单五个页面，以及课表导入/提醒、日程提醒、健康记录/报告、账单导入/预算四个模块。

## 开发与构建

使用 Android Studio 打开仓库根目录，等待 Gradle 同步后执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
Get-FileHash app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

APK 输出位置是 `app/build/outputs/apk/debug/app-debug.apk`。网络、WebDAV 和 DeepSeek 都是可选能力；不配置密钥时，本地记录、报告和账单功能仍应工作。密钥必须通过应用设置页输入，仓库和测试中不放真实凭据。

## 数据与测试

课表、日程、健康和账单数据保存在 Room；账单导入先预览，用户确认后才写入。可使用测试中的合成 CSV/XLSX 数据验证解析，不要将个人账单或 API Key 加入仓库。WebDAV 自动同步默认关闭，远端更新冲突需要明确选择，恢复备份前会要求确认并创建本地安全副本。

验收清单见 [`docs/qa/daily-v1-test-matrix.md`](docs/qa/daily-v1-test-matrix.md)。
