# Daily v1 QA matrix

| Area | Environment | Action | Expected result | Actual result | Status |
|---|---|---|---|---|---|
| Identity | Unit/static | Check label, package, version | `Daily`, `com.daily.life`, `0.1.0` | Manifest/build config inspected | Pass |
| Navigation | Emulator | Open each bottom destination | 首页、课表、日程、健康、账单均可进入 | Not run: no emulator/JDK | Not Run |
| Timetable | Emulator | Import synthetic PDF and change week | Preview/weekly view updates; reminder remains local | Existing unit tests cover parser/rules | Partial |
| Schedule | Emulator | Create event and exercise reminder permission degraded mode | Event saves; unavailable alarm permission does not block app | Static/legacy tests present | Partial |
| Health | Emulator | Record weight/activity; open monthly report | Manual records and local report work without network/Health Connect | Unit tests present; device not run | Partial |
| Bills | Emulator | Preview synthetic WeChat/Alipay CSV and XLSX, then confirm | Preview has totals/warnings; writes only after confirmation; amounts use positive cents + direction | Unit tests present; device not run | Partial |
| Offline | Emulator | Disable network and open all pages | Startup and local pages remain usable; no network prerequisite | Static architecture inspected | Partial |
| Permissions | Android 13+/16 | Deny notifications, exact alarm, health and network access in turn | App opens; degraded features explain state and local functions remain usable | Device not run | Not Run |
| Backup | Unit/emulator | Round-trip snapshot; attempt restore without confirmation; test newer remote | Schema/secret checks pass; restore refuses without confirmation; newer remote is not overwritten | Focused tests added; Gradle blocked by JDK | Partial |
| Optional advice | Unit | Missing key, network failure, successful fake client | Local report is preserved on missing key/failure; advice overlays only on success | Focused tests added; Gradle blocked by JDK | Partial |
| Accessibility | Device | Chinese text, keyboard, contrast, touch targets, back navigation | Text readable and controls reachable | Not run: no emulator/device | Not Run |
| Release | Build host | Assemble debug APK and record SHA-256 | Installable APK with Daily identity | Blocked: invalid `JAVA_HOME` | Blocked |

## Known verification limitation

The current environment points `JAVA_HOME` to `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`, which is not present. Gradle therefore exits before compilation. No APK or checksum is claimed until a working JDK 17 is configured.
