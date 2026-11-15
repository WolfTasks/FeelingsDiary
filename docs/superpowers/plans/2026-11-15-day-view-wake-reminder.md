# Tagesansicht ab erstem Eintrag + Tagesbeginn-Erinnerung – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Tagesansicht zeigt nur noch den Bereich ab der vollen Stunde des ersten Eintrags bis 24 Uhr, und eine neue lautlose „Tagesbeginn“-Erinnerung (Standard an, 7:00) kommt unabhängig von Slots und Nachtruhe.

**Architecture:** `GraphBuilder` gibt für `PeriodKind.DAY` die Startminute des sichtbaren Bereichs in `GraphData` mit; Punkte und Achsenlabels skalieren darauf. Die Tagesbeginn-Zeit ist eine Sonderzeit in `ReminderPlanner`, die in derselben einen Alarmkette wie die Slots läuft; der Alarm trägt ein Extra „wake“, `onAlarm` nimmt sie von der Nachtruhe aus und `ReminderNotifier` zeigt sie über einen eigenen lautlosen Kanal.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, AlarmManager, JUnit4/Robolectric/Compose-UI-Test.

**Spec:** `docs/superpowers/specs/2026-11-15-day-view-wake-reminder-design.md`

## Global Constraints

- Arbeit im Worktree `C:\Users\Admin\IdeaProjects\FeelingsDiary-wake`, Branch `feat/day-view-wake-reminder`; nur explizite Pfade stagen.
- Build/Verify: `export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot"`, dann `./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug`.
- Keine neuen Berechtigungen, keine exakten Alarme; nur `setAndAllowWhileIdle`.
- Jeder UI-Text als String-Ressource in `values/strings.xml` (EN) und `values-de/strings.xml` (DE); `StringsParityTest` muss grün bleiben.
- Tagesbeginn: Standard `wakeEnabled = true`, `wakeTime = 07:00`; immer lautlos (kein Ton, keine Vibration), unabhängig von `alarmSound`; nicht von der Nachtruhe betroffen; vom Hauptschalter `enabled` und vom Onboarding (`onboardingDone`) abhängig.
- Graph-x bleibt nach echter Zeit (`epochMinute`).
- Commits: Conventional Commits, letzte Zeile `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. Erster Eintrag spät (z. B. 23:40) → Bereich 23–24 Uhr, Punkt liegt bei x≈0.67, mindestens ein Label (23:00). → Test in Task 1 und Task 2.
2. Aufwachzeit in der Nachtruhe (7:00 bei Nachtruhe 22–8), Alarm kommt verspätet um 7:50 → Meldung erscheint trotzdem, lautlos. → Test in Task 5.
3. Aufwachzeit = fester Slot (beide 10:00) → genau eine Meldung, lautlos. → Test in Task 3 und Task 5.
4. Hauptschalter aus → kein Tagesbeginn geplant oder gezeigt; Tagesbeginn aus → Planung identisch zu vorher. → Tests in Task 3.
5. Bestandsinstallation ohne neue Keys bzw. mit falsch typisierten Werten → `wakeEnabled = true`, `wakeTime = 07:00`. → Test in Task 3.

---

### Task 1: GraphBuilder – Tagesbereich ab erstem Eintrag

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/domain/GraphBuilder.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/domain/GraphBuilderTest.kt`

**Interfaces:**
- Produces: `GraphData.dayStartMinute: Int` (neues Feld, Default `0`): Minute des Ortstags, an der die x-Achse beginnt; nur für `PeriodKind.DAY` mit Einträgen ≠ 0, sonst 0. x der Punkte ist relativ zu `[dayStartMinute, 1440)`.

- [ ] **Step 1: Failing tests** (Helfer `entry(...)`/`at(...)` des bestehenden Tests verwenden)

```kotlin
@Test fun dayStartsAtFullHourOfFirstEntry() {
    // Einträge 08:30 und 16:00 am 2026-11-04 (Offset 60)
    val data = GraphBuilder.build(listOf(e("2026-11-04T08:30"), e("2026-11-04T16:00")), day, monday)
    assertEquals(8 * 60, data.dayStartMinute)
    assertEquals(30f / (16 * 60), data.points[0].x, 1e-4f)
    assertEquals((8 * 60f) / (16 * 60), data.points[1].x, 1e-4f)
}
@Test fun lateFirstEntryGivesOneHourDay() {
    val data = GraphBuilder.build(listOf(e("2026-11-04T23:40")), day, monday)
    assertEquals(23 * 60, data.dayStartMinute)
    assertEquals(40f / 60, data.points.single().x, 1e-4f)
}
@Test fun emptyDayAndOtherPeriodsStartAtMidnight() {
    assertEquals(0, GraphBuilder.build(emptyList(), day, monday).dayStartMinute)
    assertEquals(0, GraphBuilder.build(listOf(e("2026-11-04T08:30")), week, monday).dayStartMinute)
}
```

- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*GraphBuilderTest"` → FAIL (`dayStartMinute` unbekannt).
- [ ] **Step 3: Implementieren.** In `unaggregatedPoints` für `period.kind == DAY`: `startMinute = erster Eintrag (nach epochMinute) .localDateTime().hour * 60`. Echtzeit-Zweig: `startEpoch += startMinute`, Spanne = `endEpoch - startEpoch`. Wall-Time-Zweig: `(minutesSinceMidnight - startMinute) / (periodMinutes - startMinute)`. `useRealTime` weiterhin gegen die Hälfte der (gekürzten) Spanne prüfen. `build` setzt `dayStartMinute` in `GraphData`. Bestehende DAY-Tests (`fallBack…`, `springForward…`, `extremeOffset…`) bleiben Reihenfolge-Tests; falls einer absolute x-Werte prüft, die erwarteten Werte auf den gekürzten Bereich umrechnen (nicht die Logik anpassen).
- [ ] **Step 4:** Gleicher Befehl → PASS.
- [ ] **Step 5: Commit** `feat(graph): start day view at the hour of the first entry`

### Task 2: Achsenlabels für den gekürzten Tag

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/ui/home/TimeFormat.kt`, `app/src/main/java/com/wolf/feelingsdiary/ui/home/HomeScreen.kt:154`
- Test: `app/src/test/java/com/wolf/feelingsdiary/ui/home/TimeFormatTest.kt`

**Interfaces:**
- Consumes: `GraphData.dayStartMinute` (Task 1).
- Produces: `fun xAxisLabels(p: GraphPeriod, locale: Locale, dayStartMinute: Int = 0): List<AxisLabel>`.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun dayLabelsFollowStartHour() {
    // 8–24 Uhr = 16 h → Raster 3 h: 9, 12, 15, 18, 21
    val labels = xAxisLabels(day, Locale.GERMANY, dayStartMinute = 8 * 60)
    assertEquals(listOf("09:00", "12:00", "15:00", "18:00", "21:00"), labels.map { it.text })
    assertEquals(1f / 16, labels.first().x, 1e-4f)
}
@Test fun lateDayHasOneLabel() {
    assertEquals(listOf("23:00"), xAxisLabels(day, Locale.GERMANY, 23 * 60).map { it.text })
}
@Test fun fullDayLabelsUnchanged() {
    assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f), xAxisLabels(day, Locale.GERMANY).map { it.x })
}
```

- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*TimeFormatTest"` → FAIL.
- [ ] **Step 3: Implementieren.** Spanne `h = (1440 - dayStartMinute) / 60` Stunden; Raster: `h ≤ 6 → 1`, `≤ 12 → 2`, `≤ 18 → 3`, sonst `6`. Labels = alle vollen Stunden `≥ startHour`, `< 24`, die Vielfache des Rasters sind; `x = (hour*60 - dayStartMinute) / (1440 - dayStartMinute)` (das Raster ist nie größer als die Spanne, also gibt es immer mindestens ein Label). Andere Perioden ignorieren den Parameter. `HomeScreen` übergibt `state.graph.dayStartMinute` (Feldname im UI-State wie vorhanden).
- [ ] **Step 4:** Gleicher Befehl → PASS; zusätzlich `--tests "*HomeScreenTest" --tests "*HistoryGraphTest"` → PASS.
- [ ] **Step 5: Commit** `feat(graph): adapt day axis labels to the shortened day`

### Task 3: Einstellung + Planer für den Tagesbeginn

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/domain/Settings.kt`, `app/src/main/java/com/wolf/feelingsdiary/domain/ReminderPlanner.kt`, `app/src/main/java/com/wolf/feelingsdiary/data/settings/SettingsRepository.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/domain/ReminderPlannerTest.kt`, `app/src/test/java/com/wolf/feelingsdiary/data/SettingsRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `ReminderSettings.wakeEnabled: Boolean = true`, `ReminderSettings.wakeTime: LocalTime = LocalTime.of(7, 0)`
  - `data class PlannedReminder(val time: ZonedDateTime, val wake: Boolean)` in `ReminderPlanner.kt`
  - `fun ReminderPlanner.nextReminder(s: ReminderSettings, after: ZonedDateTime, salt: Long): PlannedReminder?`
  - `fun ReminderPlanner.next(...)` bleibt, = `nextReminder(...)?.time`
  - DataStore-Keys `reminder_wake_enabled` (Boolean), `reminder_wake_time` (Int, Minuten des Tages)

- [ ] **Step 1: Failing tests (Planer)**

```kotlin
@Test fun wakeTimeIsPlannedEvenInQuietHours() {
    val s = ReminderSettings(mode = FIXED) // Nachtruhe 22–8, wake 07:00
    assertEquals(PlannedReminder(ZonedDateTime.of(day, t(7), berlin), wake = true),
        ReminderPlanner.nextReminder(s, ZonedDateTime.of(day, t(6), berlin), 1L))
    assertEquals(PlannedReminder(ZonedDateTime.of(day, t(10), berlin), wake = false),
        ReminderPlanner.nextReminder(s, ZonedDateTime.of(day, t(7), berlin), 1L))
}
@Test fun wakeOnSlotMinuteIsOneWakeReminder() {
    val s = ReminderSettings(mode = FIXED, wakeTime = t(10))
    assertEquals(PlannedReminder(ZonedDateTime.of(day, t(10), berlin), wake = true),
        ReminderPlanner.nextReminder(s, ZonedDateTime.of(day, t(9), berlin), 1L))
    assertEquals(t(13), ReminderPlanner.next(s, ZonedDateTime.of(day, t(10), berlin), 1L)!!.toLocalTime())
}
@Test fun wakeDisabledPlansLikeBefore() {
    val s = ReminderSettings(mode = FIXED, wakeEnabled = false)
    assertEquals(ZonedDateTime.of(day, t(10), berlin), ReminderPlanner.next(s, ZonedDateTime.of(day, t(6), berlin), 1L))
}
@Test fun masterSwitchOffPlansNoWake() {
    assertNull(ReminderPlanner.nextReminder(ReminderSettings(enabled = false), ZonedDateTime.of(day, t(6), berlin), 1L))
}
```

- [ ] **Step 2: Failing tests (Repository)** in `SettingsRepositoryTest`: `wakeDefaultsWhenAbsent` (leerer Store → `true`, `07:00`), `wakeRoundTrips` (`update { wakeEnabled=false, wakeTime=06:15 }` → gelesen gleich), `wrongTypedWakeFallsBack` (`reminder_wake_time` als String gespeichert, außerhalb 0..1439 als Int → `07:00`).
- [ ] **Step 3:** `./gradlew testDebugUnitTest --tests "*ReminderPlannerTest" --tests "*SettingsRepositoryTest"` → FAIL.
- [ ] **Step 4: Implementieren.** Felder in `ReminderSettings`; `nextReminder` sucht wie `next` je Tag über `slotsFor` plus (wenn `wakeEnabled`) `wakeTime` (Sekunden/Nanos 0, **ohne** Nachtruhe-Filter); gleiche Minute → ein Eintrag mit `wake = true`. Repository liest/schreibt die zwei Keys mit `typed(...)`/`time(...)` wie die übrigen. Bestehende Planer-/Scheduler-Tests, die durch den 7:00-Termin kippen, setzen explizit `wakeEnabled = false` (Absicht des Tests bleibt).
- [ ] **Step 5:** Gleicher Befehl → PASS; dann komplette `testDebugUnitTest` → PASS (u. a. `ReminderSchedulerTest`, ggf. dort `wakeEnabled = false` ergänzen).
- [ ] **Step 6: Commit** `feat(reminder): plan a wake-up reminder independent of slots and quiet hours`

### Task 4: Lautloser Kanal im Notifier

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/reminder/ReminderNotifier.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml`
- Test: `app/src/test/java/com/wolf/feelingsdiary/reminder/ReminderNotifierTest.kt`

**Interfaces:**
- Produces: `fun ReminderNotifier.show(alarmSound: Boolean = false, silent: Boolean = false)`; `silent = true` schlägt `alarmSound`. `const val WAKE_CHANNEL_ID = "reminders_wake"`. String `reminder_channel_wake_name`: EN „Day start (silent)“, DE „Tagesbeginn (lautlos)“.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun silentUsesWakeChannelWithoutSoundOrVibration() {
    notifier.show(alarmSound = true, silent = true)
    assertEquals("reminders_wake", posted().channelId)
    val channel = manager.getNotificationChannel("reminders_wake")
    assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    assertNull(channel.sound)
    assertFalse(channel.shouldVibrate())
}
```

- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*ReminderNotifierTest"` → FAIL.
- [ ] **Step 3: Implementieren.** `wakeChannel()`: `IMPORTANCE_HIGH` (Popup), `setSound(null, null)`, `enableVibration(false)`. Notification zusätzlich `setSilent(true)` für den lautlosen Fall. Gleiche `NOTIFICATION_ID`, gleicher Tap-Intent.
- [ ] **Step 4:** Gleicher Befehl + `--tests "*StringsParityTest"` → PASS.
- [ ] **Step 5: Commit** `feat(reminder): silent notification channel for the wake-up reminder`

### Task 5: Scheduler/Receiver – Tagesbeginn in der Alarmkette

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/reminder/ReminderScheduler.kt`, `app/src/main/java/com/wolf/feelingsdiary/reminder/ReminderReceiver.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/reminder/ReminderSchedulerTest.kt`, `app/src/test/java/com/wolf/feelingsdiary/reminder/ReminderReceiverTest.kt`

**Interfaces:**
- Consumes: `ReminderPlanner.nextReminder`, `PlannedReminder` (Task 3); `ReminderNotifier.show(alarmSound, silent)` (Task 4).
- Produces: `const val EXTRA_WAKE = "wake"` (Boolean) im Alarm-Intent; `suspend fun onAlarm(targetEpochMillis: Long? = null, wake: Boolean = false)`; `ReminderReceiver.wakeOf(intent: Intent): Boolean` (fehlend → `false`).

- [ ] **Step 1: Failing tests**
  - `wakeAlarmCarriesWakeExtra`: Uhr 2026-11-08 06:00, Default-Settings → registrierter Alarm hat Target 07:00 und `EXTRA_WAKE = true`.
  - `lateWakeAlarmInQuietHoursStillShowsSilently`: Uhr 07:50 (Nachtruhe bis 8), `onAlarm(target = 07:00, wake = true)` → eine Notification auf Kanal `reminders_wake`, danach nächster Slot geplant.
  - `wakeOnSlotMinuteShowsOneSilentNotification`: FIXED, `wakeTime = 10:00`, `onAlarm(10:00, wake = true)` um 10:00 → genau eine Notification, Kanal `reminders_wake`.
  - `wakeIgnoresAlarmSoundSetting`: `alarmSound = true`, Tagesbeginn-Alarm → Kanal `reminders_wake`.
  - `ReminderReceiverTest`: `wakeOf` liest das Extra, ohne Extra `false`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*ReminderSchedulerTest" --tests "*ReminderReceiverTest"` → FAIL.
- [ ] **Step 3: Implementieren.** `reschedule()` nutzt `nextReminder` und legt `wake` als Extra in `alarmIntent(target, wake)` (Stufen tragen es mit). `onAlarm`: Stufe wie bisher; sonst `remind = enabled && onboardingDone && (wake || !isQuiet(now))`, `notifier.show(alarmSound, silent = wake)`. Receiver reicht `wakeOf(intent)` durch. KDoc von `ReminderScheduler`/`onAlarm` um die Ausnahme ergänzen.
- [ ] **Step 4:** Gleicher Befehl → PASS.
- [ ] **Step 5: Commit** `feat(reminder): deliver the wake-up reminder through the alarm chain`

### Task 6: Einstellungen-UI + Regel in CLAUDE.md

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/ui/settings/ReminderSection.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml`, `CLAUDE.md`
- Test: `app/src/test/java/com/wolf/feelingsdiary/ui/settings/ReminderSectionTest.kt`

**Interfaces:**
- Consumes: `ReminderSettings.wakeEnabled`, `wakeTime` (Task 3).
- Strings (EN / DE):
  - `reminder_wake`: „Day start“ / „Tagesbeginn“
  - `reminder_wake_hint`: „Silent popup at wake-up time, also during quiet hours“ / „Lautloses Popup zur Aufwachzeit, auch in der Nachtruhe“
  - `reminder_wake_time`: „Wake-up time“ / „Aufwachzeit“

- [ ] **Step 1: Failing tests** in `ReminderSectionTest`: `wakeSwitchTogglesSetting` (Klick auf „Day start“ → `onChange` liefert `wakeEnabled = false`), `wakeTimeRowShowsTimeAndOpensPicker` (Zeile „Wake-up time“ zeigt „7:00 AM“ bei `Locale.US`, Klick öffnet den TimePicker), `wakeRowsDisabledWhenRemindersOff`, `wakeTimeHiddenOrDisabledWhenWakeOff` (eigene Wahl: deaktiviert, nicht ausgeblendet).
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*ReminderSectionTest"` → FAIL.
- [ ] **Step 3: Implementieren.** Nach dem Alarmton-Schalter: `SwitchRow(reminder_wake, s.wakeEnabled, enabled = s.enabled, hint = reminder_wake_hint)` und `TimeRow(reminder_wake_time, fmt(s.wakeTime), quietHint = false, enabled = s.enabled && s.wakeEnabled) { edit = Edit.Wake }`; neuer Fall `data object Wake : Edit`, der vorhandene `TimePickerDialog` setzt `wakeTime`. CLAUDE.md, Regel-Zeile „Erinnerungen nur ungenau …“ ändern zu: „Erinnerungen nur ungenau (`setAndAllowWhileIdle`); Slot-Erinnerungen nie in der Nachtruhe, Tagesbeginn-Erinnerung ausgenommen und immer lautlos; Graph-x nach echter Zeit (`epochMinute`), nicht nach Ortszeit.“
- [ ] **Step 4:** Gleicher Befehl + `--tests "*StringsParityTest" --tests "*SettingsScreenTest"` → PASS.
- [ ] **Step 5: Gesamtprüfung:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `feat(settings): day start switch and wake-up time` (inkl. CLAUDE.md)
