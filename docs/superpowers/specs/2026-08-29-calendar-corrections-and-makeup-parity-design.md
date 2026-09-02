# Calendar Corrections and Makeup Parity Design

## Goal

Make holiday status trustworthy, expose manual calendar correction, guide new users to allow background battery use, and let every makeup-workday choose both its source weekday and source week parity.

## Calendar classification

An event containing clear rest wording (`假期`, `放假`, `休假`, `休息`, or a public-holiday name) is a holiday. A day is makeup work only when the event explicitly says `补班` or `调休上班`; bare `调休` is insufficient. When non-manual sources disagree on a date, holiday wins. Manual overrides retain the highest priority.

## Manual correction UI

The current selected-day card hides correction behind the text `修改这一天`. Replace it with an always-visible `修正日期状态` button. Its panel applies to the selected date by default and offers `设为休息日`, `设为调休上班`, and `恢复系统日历`; existing range and note fields stay available. The existing `改` marker identifies manual results.

## First-launch battery guide

Persist one acknowledgement flag in `DailyPreferences`. The root navigation host shows a one-time modal before normal interaction, explaining: `应用信息 → 电池 → 后台耗电管理 → 允许后台耗电`. It provides `去设置` (opens this app's system App Info page) and `稍后`; either action acknowledges the guide. The existing Settings entry remains available later.

## Makeup course parity

Each semester makeup adjustment stores both source weekday and source parity (`ODD` or `EVEN`). The importer requires both selections for every makeup date, such as `周五` plus `单周`, and never infers either from a feed. Missing data blocks import.

At display and reminder-sync time, a makeup date uses the selected weekday and the nearest valid semester week of the selected parity. This supplies that parity's weekday course list; weekly courses remain available in both parity choices. Legacy adjustments with no saved parity are unconfirmed and schedule no makeup course or reminder.

## Persistence and verification

Add nullable `sourceWeekParity` to `semester_calendar_adjustments` in Room version 11 with a 10→11 migration. Cover keyword precedence, same-date source conflicts, manual correction visibility, first-launch acknowledgement and intent, incomplete/completed weekday-parity choices, parity-aware timetable and reminder mapping, migration, and the debug APK build.
