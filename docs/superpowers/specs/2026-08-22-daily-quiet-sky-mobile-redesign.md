# Daily Quiet Sky Mobile Redesign

## Goal

Implement the user-approved Daily home-screen direction as a portrait-first Android Compose interface, then reuse its shared visual rules across the other four primary destinations.

## Source of truth

- The supplied Quiet Sky Unified v3 HTML pages define the existing visual identity.
- The approved home-screen preview defines the new home information hierarchy: overview card, Today's Timetable list, Today's To-dos list, and five-item bottom navigation.
- The app remains Chinese-first. Visible navigation and feature copy stays Chinese.

## Visual rules

- Design viewport: Android portrait, targeting a 390 x 844 reference frame. Content is a single vertical column with 18dp horizontal padding.
- Preserve the Quiet Sky palette: background `#F6F8FD`, surfaces `#FFFFFF`, ink `#121B33`, muted text `#7B869D`, primary blue `#6F8FF4`, lavender `#9188E8`, success `#6DA987`, rose `#D9909C`, and borders `#E2E8F4`.
- Cards use 26dp large corners, light cool borders, and soft blue-tinted elevation. Cards exist for summary or grouped list content only, never as a default wrapper for every element.
- Page title and data hierarchy use restrained Chinese sans-serif typography. Headlines are medium-large and semibold, metadata is compact and muted. No English labels except the existing non-user-facing date treatment may be localized to Chinese during implementation.
- The bottom bar is fixed, Chinese, and five destinations wide. The active destination uses the primary blue. It must remain touch-friendly and visually lighter than the content.
- No desktop split panels, wide web grids, horizontal hero sections, stock illustrations, dark themes, or unrelated replacement palettes.

## Home information architecture

1. Header: `首页`, the existing date-based subtitle, notification and settings actions.
2. Summary card: greeting, current date action, and three metrics for courses, schedule, and budget.
3. `今日课表`: a grouped list of the current day's courses, rendered as time, course name, and location/teacher metadata. When there is no course data, show a concise import call to action.
4. `今日待办`: a grouped list of the current day's/near-term schedule entries, with a completion affordance. When empty, provide a concise creation call to action.
5. Navigation: unchanged destinations and routing.

## Data and interaction constraints

- Preserve existing Room repositories, ViewModels, imports, destinations, and user data.
- Extend home summary models only as required to provide today's course rows and today's schedule rows. Do not duplicate database state in the UI.
- Clicking a course section opens Timetable; clicking a to-do section opens Schedule; header settings action remains functional.
- Support loading, empty, and populated states with the same visual system.

## Acceptance criteria

- The actual Android home screen matches the approved portrait hierarchy and keeps the Quiet Sky colors.
- Four module shortcut cards do not appear between the summary and `今日待办` sections.
- No visible English user-facing copy is introduced.
- Existing unit tests pass, the debug APK builds, and an emulator screenshot is inspected at portrait resolution.
