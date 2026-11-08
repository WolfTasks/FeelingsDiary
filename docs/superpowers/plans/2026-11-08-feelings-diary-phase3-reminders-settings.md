# FeelingsDiary – Phase 3: Erinnerungen, Einstellungen, Onboarding – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Vollständige App-Logik: Erinnerungen (Zufallsfenster/feste Zeiten, Nachtruhe, Neustart-fest, Benachrichtigungs-Berechtigung), Einstellungen (Theme inkl. System, Sprache, Eingabe A/B mit Schnell-Blatt A, Gefühle verwalten, steuerbare Android-Sicherung) und Onboarding beim ersten Start; dazu die drei aus Phase 2 verschobenen Punkte.

**Architecture:** Einstellungen als reines Modell in `domain/Settings.kt`, gespeichert über Preferences-DataStore (`data/settings`). Die Erinnerungs-Planung ist reine Logik (`domain/ReminderPlanner`, deterministischer Zufall je Tag), die Laufzeit liegt in `reminder/` (ein einzelner ungenauer `AlarmManager`-Alarm für die jeweils nächste Erinnerung, Receiver für Alarm und Boot, Benachrichtigung). UI: neue Pakete `ui/settings`, `ui/feelings`, `ui/onboarding`; Schnell-Blatt A in `ui/entry`, nutzt `EntryViewModel`. Sicherung über eigenen `BackupAgent`, der nur bei eingeschalteter Einstellung an Auto-Backup weitergibt.

**Tech Stack:** wie Phase 2 (Kotlin 2.4.21, Compose BOM 2026.09.00 / Material 3 inkl. `TimePicker`, `ModalBottomSheet`, Room 2.8.5, Hilt 2.60.1, Navigation-Compose 2.10.2, JUnit4 + MockK + Robolectric + Compose UI-Test). **Neu:** `androidx.datastore:datastore-preferences` (aktuelle stabile Version, z. B. 1.1.7). WorkManager wird **nicht** verwendet; die ungenutzten Katalog-Einträge `workmanager`, `hilt-work`, `hilt-work-compiler`, `hiltWork` werden entfernt.

**Spec:** `docs/superpowers/specs/2026-11-07-feelings-diary-design.md` (v. a. 5.1, 5.2, 5.4–5.7, 6, 7). Vorgänger: `docs/superpowers/plans/2026-11-07-feelings-diary-phase2-history-graph.md`.

## Bearbeitungsstand (Stand: 2026-11-08)

Legende: ✅ erledigt + reviewt · 🔄 in Arbeit · ⏳ offen. Bei ✅-Tasks sind unten alle Schritte abgehakt; die Zusatz-Tasks (9b–9d, 10b–10e) stehen direkt hinter ihrem Haupt-Task.

| Task | Inhalt (kurz) | Status | Commits |
|---|---|---|---|
| 1 | Phase-2-Deferrals: Ladezustand, Monatsmittel ab 7 Jahren | ✅ | `aa3750b` |
| 2 | Einstellungs-Modell + DataStore | ✅ | `4cd147e` |
| 3 | Erinnerungs-Planung (reine Logik) | ✅ | `6f95dd0` |
| 4 | Erinnerungen zur Laufzeit (Alarm, Benachrichtigung, Boot) | ✅ | `ed2d8b7`..`92de6f6` |
| 5 | Einstellungs-Bildschirm, Theme, Sprache | ✅ | `fd0a53b` |
| 6 | Abschnitt Erinnerungen | ✅ | `06f073f`..`d8c2a79` |
| 7 | Gefühle verwalten | ✅ | `cab8a25`..`ee8c2a0` |
| 8 | Schnell-Blatt (Eingabe A) + Einstellung | ✅ | `ae0a6d3`..`31b0d81` |
| 9 | Steuerbare Android-Sicherung | ✅ | `cc055ea`..`4b78f6a` |
| 9b | Graph: echte Reihenfolge über Zeitumstellung/Zeitzonen | ✅ | `9bcf4ac` |
| 9c | „(UTC+1)“-Hinweis in den Eintrag-Details | ✅ | `13a29db` |
| 9d | Cloud-Sicherung nur Ende-zu-Ende, D2D erlaubt | ✅ | `4afa6b8` |
| 10 | Onboarding | ✅ | `35941c4`..`551e7f0` |
| 10b | Heads-up-Popup mit Standardton, Alarmton optional | ✅ | `d89a8eb` |
| 10c | Verspätung begrenzen (Etappen-Planung, einstellbar) | ✅ | `b3ab809` |
| 10d | Hinweis „Akku nicht einschränken“ (Samsung Freecess) | ✅ | `f69b976` |
| 10e | Nacharbeit 10c: ehrliche Beschriftung, keine Doppel-Erinnerung | ✅ | `5942312`..`9b0fcbf` |
| Gesamt-Review + Fix-Runde | Abschluss-Review über Tasks 1–10, 9 Korrekturen | ✅ | `40cee5b`..`cee1b4d` |
| 11 | Gerätetest, Doku, PR | 🔄 | Doku `b529f1d`, `237126f`; Gerätetest ✅ (Samsung-Freecess → 10d); PR offen |

**Task 11 im Detail:** Doku ✅ (`b529f1d`, `237126f`). Gerätetest ✅: Onboarding, Berechtigung, Popup (Heads-up, Standardton), Datenrücksicherung, Einstellungen/Uhrzeit-Auswahl, sofortiges Umplanen, Akku-Hinweis mit Weg zu „Nicht eingeschränkt“ und Verschwinden nach Rückkehr, Beschriftung Genauigkeit; Erinnerung mit Akku-Ausnahme pünktlich im Fenster (15:45 → 15:49). Prüfkette: 349 Tests grün. PR offen.

## Global Constraints

- Alle Regeln aus `CLAUDE.md` gelten weiter (kein `INTERNET`, keine exakten Alarme `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, jeder UI-Text in `values/strings.xml` **und** `values-de/strings.xml`, Personen 0–999, Valenz −2…+2 als Momentaufnahme, Hauptgefühl = Position 0).
- Neue Berechtigungen **nur** `android.permission.POST_NOTIFICATIONS` und `android.permission.RECEIVE_BOOT_COMPLETED`. `ManifestPolicyTest` prüft die vollständige Liste der eigenen `<uses-permission>`-Einträge.
- Erinnerungen: 1–4 pro Tag, Standard **3**; Standard-Fenster Vormittag **09:00–11:00**, Mittag **12:00–14:00**, Nachmittag **15:00–17:00**, Abend **19:00–21:00**; Standard **Zufall im Fenster**, optional **feste Uhrzeiten** (Standard 10:00, 13:00, 16:00, 20:00); Nachtruhe Standard **22:00–08:00**, dort nie eine Erinnerung (Spec 5.1).
- Nur ungenaue Alarme (`setAndAllowWhileIdle`, RTC_WAKEUP); ein paar Minuten Ungenauigkeit sind erlaubt (Spec 5.1).
- Tipp auf die Benachrichtigung öffnet direkt die Eingabe (Spec 5.1).
- Theme: **Dunkel (Standard)**, Hell, System folgen (Spec 5.5). Sprache: System / Deutsch / English über Per-App-Language (Spec 5.6).
- Android-Sicherung Standard **aus**; mit „aus“ verlässt kein Byte das Gerät, auch nicht per Gerätewechsel (D2D) (Spec 5.7).
- Grundliste-Gefühle: ausblendbar, nicht löschbar; eigene Gefühle: bearbeiten/ausblenden (Spec 5.4). Valenz-Änderungen wirken nicht rückwirkend, Farbe schon (bereits so gespeichert).
- Bauen mit `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot"`; `./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug` muss am Ende jedes Tasks grün sein.
- Arbeiten im Worktree `C:\Users\Admin\IdeaProjects\FeelingsDiary-phase3` auf Branch `feat/phase3-reminders-settings`; nur explizite Pfade stagen.

## Planer-Entscheidungen (von Wolfgang im PR übersteuerbar)

| # | Frage | Entscheidung im Plan |
|---|---|---|
| E1 | Alarm-Technik | `AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, …)` für **genau die nächste** Erinnerung; nach dem Auslösen wird die übernächste geplant. Kein WorkManager (unter Doze stundenlang verzögerbar). |
| E2 | Welche Fenster bei < 4 Erinnerungen | Feste Tabelle über Fenster-Index (0 = Vormittag … 3 = Abend): 4 → 0,1,2,3 · 3 → 0,2,3 · 2 → 0,3 · 1 → 2. Gilt gleich für feste Uhrzeiten. Alle 4 Fenster/Zeiten bleiben gespeichert; die Einstellungen zeigen nur die aktiven. |
| E3 | Zufall | Je Tag und Fenster eine Minute, deterministisch aus `Random(salt xor epochDay * 31 + index)`; `salt` wird einmalig zufällig erzeugt und gespeichert. Neu-Planen (Boot, Einstellungsänderung) ergibt dieselbe Zeit, ein Fenster feuert nie zweimal am Tag. |
| E4 | Nachtruhe vs. Fenster | Erlaubt sind nur Minuten außerhalb der Nachtruhe (die über Mitternacht laufen kann). Fenster wird darauf beschnitten; liegt es ganz in der Nachtruhe, entfällt es. Feste Zeit in der Nachtruhe entfällt. Einstellungen zeigen dann einen Hinweis. |
| E5 | Verpasste Erinnerung | Benachrichtigung bleibt stehen, bis die nächste sie ersetzt (gleiche ID). Speichern eines Eintrags entfernt sie. Eintrag bekommt den Zeitpunkt der Eingabe (Spec 7). |
| E6 | Sprache unter Android 13 | Zeile „Sprache“ nur ab API 33 (`LocaleManager`), darunter folgt die App dem System. Keine AppCompat-Abhängigkeit. |
| E7 | Gefühle verwalten | Grundliste: Farbe, Valenz, ausblenden (Name fest). Eigene: Name, Farbe, Valenz, ausblenden. **Kein Löschen** (Einträge verweisen darauf). Das letzte sichtbare Gefühl kann nicht ausgeblendet werden. Ausgeblendete erscheinen nicht in Eingabe/Vorschlägen, aber weiter im Verlauf, in der Legende und beim Bearbeiten eines alten Eintrags, der sie enthält. |
| E8 | Schnell-Blatt A | `ModalBottomSheet` über dem Verlauf: zuletzt genutzte Gefühle (max. 8; ohne Historie die ersten 8 der Grundliste-Reihenfolge), 🔍 klappt die Suche auf, Personen −/+ (0–999), Speichern. Startwert Personen = Personen des letzten Eintrags, sonst 0; gilt als gewählt. **Bearbeiten** läuft immer über Eingabe B. |
| E9 | Sicherung | `android:allowBackup="true"`, `android:fullBackupOnly="true"`, `android:backupAgent=".backup.DiaryBackupAgent"`; der Agent gibt `onFullBackup` nur bei `backupEnabled` an `super` weiter. Gesichert werden Datenbank und Einstellungen. Nach einer Wiederherstellung plant der normale App-Start die Erinnerungen neu. |
| E10 | Onboarding | Ein Bildschirm beim ersten Start (auch für Phase-2-Nutzer, einmalig): Zweck + „Daten bleiben auf dem Handy“, Erinnerungen an/aus + Anzahl 1–4, Knopf „Erinnerungen erlauben“ (Berechtigungsdialog ab API 33), „Los geht's“. Fenster werden in den Einstellungen angepasst. |
| E11 | Einstieg Einstellungen | ⚙-Symbol oben rechts neben dem Titel „Mein Verlauf“ (Phase-2-Entscheidung E6 wird damit eingelöst). |

## Review Focus

1. Nachtruhe über Mitternacht und Sommerzeit: Fenster 07:00–09:00 bei Nachtruhe 22–08 erzeugt nur Zeiten 08:00–08:59; Nachtruhe 13:00–14:00 mitten im Fenster 12–15 → nie 13:xx; am 25.10.2026 (Europe/Berlin, Umstellung) und 29.03.2026 liegen alle Zeiten in der lokalen Wandzeit des Fensters, keine liegt in der Vergangenheit; nach der letzten Zeit des Tages kommt die erste des Folgetags. → Tests in Task 3.
2. Geräte-Neustart, App beendet, Zeitzone/Uhrzeit geändert, Erinnerungen ausgeschaltet: Alarm wird neu geplant bzw. entfernt, eine stehende Benachrichtigung verschwindet beim Ausschalten. → Tests in Task 4.
3. Update von Phase 2 (DataStore leer) oder beschädigte Werte (z. B. `windows = "abc"`, Anzahl 9): Standardwerte, kein Absturz; ein Fenster mit Start ≥ Ende kann nicht gespeichert werden. → Tests in Task 2 und Task 6.
4. Gefühle verwalten: letztes sichtbares Gefühl ausblenden, eigenes Gefühl auf einen schon vorhandenen Namen umbenennen (auch übersetzter Grundlisten-Name, andere Groß-/Kleinschreibung), Eintrag mit ausgeblendetem Gefühl bearbeiten → Gefühl bleibt gewählt und sichtbar. → Tests in Task 7.
5. Sicherung aus (Standard) liefert trotz `allowBackup="true"` keine Daten; Sprachwechsel zur Laufzeit → Gefühlsnamen auf Verlauf und Eingabe folgen sofort (Phase-2-Deferral). → Tests in Task 9 und Task 5.

---

### Task 1: Phase-2-Deferrals – leerer Hinweis beim Kaltstart, Monatsmittel ab 7 Jahren

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/ui/home/HomeViewModel.kt`, `HomeScreen.kt`, `app/src/main/java/com/wolf/feelingsdiary/domain/GraphBuilder.kt`, `GraphGeometry.kt` (falls Bucket-Breite), `HomeViewModel.openBucket`
- Test: `HomeViewModelTest.kt`, `HomeScreenTest.kt`, `GraphBuilderTest.kt`

**Interfaces:**
- Produces: `HomeUiState.loading: Boolean` (true bis die erste Abfrage geliefert hat); `Aggregation.MONTHLY`.

- [x] **Step 1: Failing Tests** – `HomeScreenTest.loadingStateShowsNoEmptyHint`: `HomeScreen(state.copy(loading = true, rows = emptyList()))` → Text `history_empty` existiert nicht. `HomeViewModelTest.initialStateIsLoading`: erster Wert von `state` hat `loading == true`, nach Emission des Repos `false`. `GraphBuilderTest.rangeOverSevenYearsUsesMonthlyMeans`: Bereich 01.01.2020–31.12.2027 (2922 Tage) → `Aggregation.MONTHLY`, Punkte ≤ 96, ein Punkt je Monat mit Einträgen, Wert = Mittel der Eintragshöhen. `rangeOfExactlySevenYearsStaysWeekly`: 2557 Tage → `WEEKLY`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren** – Grenze: `period.days > 2557` → `MONTHLY` (Bucket = Monatserster, Mitte im sichtbaren Teil wie bei WEEKLY). Tipp auf Monats-Punkt öffnet die Monats-Ansicht dieses Monats. Leerer Hinweis nur bei `!loading && rows.isEmpty()`.
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `fix(home): no empty hint while loading, monthly means for ranges over seven years`.

---

### Task 2: Einstellungs-Modell und DataStore

**Files:**
- Modify: `gradle/libs.versions.toml` (+ `datastore = "1.1.7"`, Library `datastore-preferences`; WorkManager-/hilt-work-Einträge entfernen), `app/build.gradle.kts`
- Create: `app/src/main/java/com/wolf/feelingsdiary/domain/Settings.kt`, `app/src/main/java/com/wolf/feelingsdiary/data/settings/SettingsDataStore.kt`, `.../data/settings/SettingsRepository.kt`, `.../di/SettingsModule.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/data/SettingsRepositoryTest.kt`

**Interfaces:**
- Produces (in `domain/Settings.kt`):
  - `enum class ThemeMode { SYSTEM, DARK, LIGHT }`, `enum class InputMode { SCREEN, QUICK_SHEET }`, `enum class ReminderMode { RANDOM, FIXED }`
  - `data class TimeWindow(val start: LocalTime, val end: LocalTime)` – `init { require(start < end) }`
  - `data class ReminderSettings(val enabled: Boolean = true, val count: Int = 3, val mode: ReminderMode = ReminderMode.RANDOM, val windows: List<TimeWindow> = DEFAULT_WINDOWS, val fixedTimes: List<LocalTime> = DEFAULT_TIMES, val quietStart: LocalTime = LocalTime.of(22, 0), val quietEnd: LocalTime = LocalTime.of(8, 0))` – `init { require(count in 1..4); require(windows.size == 4 && fixedTimes.size == 4) }`; `DEFAULT_WINDOWS`/`DEFAULT_TIMES` im Companion mit den Werten aus den Global Constraints.
  - `data class AppSettings(val reminders: ReminderSettings = ReminderSettings(), val theme: ThemeMode = ThemeMode.DARK, val input: InputMode = InputMode.SCREEN, val backupEnabled: Boolean = false, val onboardingDone: Boolean = false)`
- Produces (data): `val Context.settingsDataStore: DataStore<Preferences>` (top-level `preferencesDataStore(name = "settings")`, Datei `datastore/settings.preferences_pb`); `class SettingsRepository @Inject constructor(store: DataStore<Preferences>)` mit `val settings: Flow<AppSettings>`, `suspend fun update(transform: (AppSettings) -> AppSettings)`, `suspend fun reminderSalt(): Long` (erzeugt beim ersten Aufruf `Random.nextLong()` und speichert ihn). Hilt: `SettingsModule` liefert `@Singleton DataStore<Preferences>` = `context.applicationContext.settingsDataStore`.

- [x] **Step 1: Failing Tests** (DataStore per `PreferenceDataStoreFactory.create(scope = testScope.backgroundScope) { tmpFolder.newFile("t.preferences_pb") }`): `emptyStoreYieldsDefaults` (== `AppSettings()`); `updateRoundTrips` (alle Felder geändert, inkl. Fenster 07:30–09:15 und feste Zeit 06:05 → identisch zurückgelesen); `corruptValuesFallBackPerField` (rohe Prefs `count=9`, `windows="abc"`, `theme="PURPLE"` → `count=3`, Standard-Fenster, `DARK`; übrige gültige Felder bleiben); `saltIsStable` (zweimal `reminderSalt()` → gleicher Wert).
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren** – Speicherformat: Zeiten als Minuten seit Mitternacht, Fenster als `"540-660,720-840,900-1020,1140-1260"`, feste Zeiten `"600,780,960,1200"`, Enums als `name`. Jedes Feld wird einzeln geparst; was nicht passt, fällt auf seinen Standard zurück.
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(settings): settings model persisted in preferences datastore`.

---

### Task 3: Erinnerungs-Planung (reine Logik)

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/domain/ReminderPlanner.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/domain/ReminderPlannerTest.kt`

**Interfaces:**
- Consumes: `ReminderSettings`, `TimeWindow`, `ReminderMode` (Task 2).
- Produces: `object ReminderPlanner` mit
  - `fun activeIndices(count: Int): List<Int>` (Tabelle E2),
  - `fun slotsFor(date: LocalDate, s: ReminderSettings, salt: Long): List<LocalTime>` (aufsteigend, Minuten-Auflösung),
  - `fun next(s: ReminderSettings, after: ZonedDateTime, salt: Long): ZonedDateTime?` – erste Erinnerung **strikt nach** `after`, in der Zone von `after`; `null` wenn `!s.enabled` oder kein Fenster außerhalb der Nachtruhe liegt (sucht höchstens heute + 2 Tage).

- [x] **Step 1: Failing Tests** – `activeIndicesFollowTable` (1→[2], 2→[0,3], 3→[0,2,3], 4→[0,1,2,3]); `randomSlotLiesInsideItsWindow` (1000 Tage, alle Slots im jeweiligen Fenster, `end` exklusiv); `sameDayAndSaltGiveSameSlots`, `differentDaysDiffer` (über 30 Tage mindestens 2 verschiedene Zeiten je Fenster); `quietHoursClipWindow` (Fenster[0] = 07:00–09:00, Nachtruhe 22–08 → alle Slots in 08:00–08:59); `quietHoursInsideWindow` (Fenster 12:00–15:00, Nachtruhe 13:00–14:00 → nie 13:xx); `windowFullyInQuietIsSkipped`; `fixedModeUsesActiveTimesAndSkipsQuiet` (feste Zeit 23:00 bei Nachtruhe 22–08 entfällt); `nextIsStrictlyAfter` (after = Slot-Zeit genau → nächster Slot); `nextRollsOverToTomorrow`; `disabledGivesNull`; `dstDaysKeepWallClock` (Europe/Berlin, 29.03.2026 und 25.10.2026: `next(...).toLocalTime()` == Slot aus `slotsFor`, Ergebnis > after); `allWindowsInQuietGivesNull`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren** – Zufalls-Slot: erlaubte Minuten = Minuten in `[start, end)` außerhalb der Nachtruhe (Nachtruhe `[quietStart, quietEnd)`, über Mitternacht wenn `quietStart > quietEnd`; `quietStart == quietEnd` = keine Nachtruhe); Slot = `allowed[Random(salt xor (epochDay * 31 + index)).nextInt(allowed.size)]`. `next` baut Kandidaten mit `ZonedDateTime.of(date, slot, zone)` (Lücke bei Umstellung → Java verschiebt vorwärts).
- [x] **Step 4: Laufen lassen** → PASS.
- [x] **Step 5: Commit** – `feat(reminders): pure reminder planner with random windows, fixed times and quiet hours`.

---

### Task 4: Erinnerungen zur Laufzeit – Alarm, Benachrichtigung, Boot, Tipp öffnet Eingabe

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/reminder/ReminderScheduler.kt`, `ReminderReceiver.kt`, `BootReceiver.kt`, `ReminderNotifier.kt`; `app/src/main/res/drawable/ic_notification.xml` (einfarbiges Vektor-Symbol)
- Modify: `AndroidManifest.xml` (2 Berechtigungen, 2 Receiver, `MainActivity` `launchMode="singleTop"`), `FeelingsDiaryApp.kt`, `MainActivity.kt`, `ui/navigation/AppNavHost.kt`, `ui/entry/EntryViewModel.kt` (Benachrichtigung beim Speichern entfernen), `di/RepositoryModule.kt` (App-Scope), Strings
- Test: `reminder/ReminderSchedulerTest.kt`, `reminder/ReminderReceiverTest.kt`, `ManifestPolicyTest.kt`, `ui/navigation/AppNavGraphTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository` (Task 2), `ReminderPlanner.next` (Task 3).
- Produces:
  - `@Singleton class ReminderScheduler @Inject constructor(@ApplicationContext ctx: Context, settings: SettingsRepository, clock: Clock)` mit `suspend fun reschedule()` (setzt oder entfernt den einen Alarm; bei `!enabled` auch `notifier.cancel()`).
  - `@Singleton class ReminderNotifier @Inject constructor(@ApplicationContext ctx: Context)` mit `fun show()`, `fun cancel()`; Kanal-ID `"reminders"`, Notification-ID `1`, `setAutoCancel(true)`, Content-Intent → `MainActivity` mit Action `MainActivity.ACTION_NEW_ENTRY = "com.wolf.feelingsdiary.NEW_ENTRY"`. `show()` tut nichts ohne Benachrichtigungs-Erlaubnis.
  - `@AndroidEntryPoint class ReminderReceiver : BroadcastReceiver` – `goAsync()`, `notifier.show()`, dann `scheduler.reschedule()`.
  - `@AndroidEntryPoint class BootReceiver` – Actions `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` → `scheduler.reschedule()`.
  - `@ApplicationScope CoroutineScope` (Qualifier in `di`); `FeelingsDiaryApp` sammelt `settings.settings.map { it.reminders }.distinctUntilChanged()` und ruft jeweils `reschedule()` (deckt App-Start und jede Einstellungsänderung ab).
  - `AppNavHost(newEntryRequests: Flow<Unit>)`: jede Emission öffnet die Eingabe (Task 8 ergänzt das Schnell-Blatt). `MainActivity` emittiert bei `ACTION_NEW_ENTRY` in `onCreate` (nur wenn `savedInstanceState == null`) und `onNewIntent`.
- Strings: Kanalname „Erinnerungen“/„Reminders“; Titel „Wie fühlst du dich?“/„How are you feeling?“ (vorhandenes `entry_question`), Text „Tippen, um es festzuhalten“/„Tap to note it down“.

- [x] **Step 1: Failing Tests** – Robolectric mit `ShadowAlarmManager`, fester `Clock`, Fake-DataStore: `schedulesNextSlotInexact` (ein Alarm, Zeit == `ReminderPlanner.next`, Typ `RTC_WAKEUP`, kein Exact-Alarm); `rescheduleReplacesPreviousAlarm` (zweimal aufrufen → genau ein Alarm); `disabledCancelsAlarmAndNotification`; `receiverShowsNotificationAndSchedulesNext` (`ShadowNotificationManager`: 1 Notification ID 1 im Kanal `reminders`; Content-Intent hat Action `ACTION_NEW_ENTRY`; neuer Alarm > jetzt); `bootReceiverReschedules`; `noNotificationWithoutPermission` (API 33, Berechtigung nicht erteilt); `EntryViewModelTest.saveCancelsReminderNotification` (MockK: `notifier.cancel()` nach erfolgreichem Speichern); `ManifestPolicyTest.permissionsAreExactlyTheAllowedSet` (eigene Einträge in `src/main/AndroidManifest.xml` == {POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED}; gemergtes Manifest weiterhin ohne INTERNET/EXACT_ALARM); `AppNavGraphTest.newEntryRequestOpensEntry` und `rotationDoesNotReopenEntry`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren** – PendingIntents mit `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, feste Request-Codes. Kanal wird in `ReminderNotifier.show()` angelegt (idempotent). Receiver `exported="false"` außer `BootReceiver` (`exported="true"` nur mit den System-Actions).
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(reminders): inexact alarm scheduling, notification and boot rescheduling`.

---

### Task 5: Einstellungs-Bildschirm (Gerüst), Theme, Sprache, Gefühlsnamen nach Sprachwechsel

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/settings/SettingsScreen.kt`, `SettingsViewModel.kt`, `app/src/main/java/com/wolf/feelingsdiary/ui/LocaleSource.kt`
- Modify: `MainActivity.kt` (Theme + System-Leisten aus Einstellungen), `ui/theme/Theme.kt`, `ui/navigation/AppNavHost.kt` (Route `settings`), `ui/home/HomeScreen.kt` (⚙), `ui/FeelingLabel.kt`, `HomeViewModel.kt`, `EntryViewModel.kt`, Strings
- Test: `ui/settings/SettingsScreenTest.kt`, `ui/settings/SettingsViewModelTest.kt`, `HomeViewModelTest.kt`, `EntryViewModelTest.kt`, `AppNavGraphTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository.settings/update` (Task 2).
- Produces:
  - Route `ROUTE_SETTINGS = "settings"`; `HomeScreen` bekommt `HomeActions.onSettings: () -> Unit`.
  - `@HiltViewModel class SettingsViewModel` mit `val state: StateFlow<AppSettings>`, `fun setTheme(ThemeMode)`, `fun setInput(InputMode)`, `fun setBackup(Boolean)`, `fun updateReminders((ReminderSettings) -> ReminderSettings)` (Task 6/8/9 nutzen sie).
  - `SettingsScreen(state: AppSettings, actions: SettingsActions, …)` mit Abschnitten in dieser Reihenfolge: **Erinnerungen** (Platzhalter, Task 6), **Eingabe** (Task 8), **Gefühle verwalten** (Zeile → Task 7), **Aussehen**, **Sprache** (nur API ≥ 33), **Datenschutz** (Task 9). Zurück-Pfeil oben links.
  - `FeelingsDiaryTheme(theme: ThemeMode, content)`; `MainActivity` liest `AppSettings.theme` und setzt `SystemBarStyle.dark/light` passend.
  - Sprache: `fun appLanguage(ctx): String?` / `fun setAppLanguage(ctx, tag: String?)` über `LocaleManager` (`null` = System, `"de"`, `"en"`).
  - `@Singleton class LocaleSource { val locale: StateFlow<Locale>; fun update(Locale) }`; `MainActivity.onCreate` ruft `update(resources.configuration.locales[0])`. `FeelingLabeler` benutzt einen `createConfigurationContext` mit dieser Locale; `HomeViewModel`/`EntryViewModel` kombinieren `LocaleSource.locale`, damit Labels sofort neu berechnet werden.
- Strings (EN/DE): `settings_title` „Settings“/„Einstellungen“, Abschnittstitel, `theme_dark` „Dark“/„Dunkel“, `theme_light` „Light“/„Hell“, `theme_system` „Follow system“/„System folgen“, `language_system` „System language“/„Systemsprache“, „Deutsch“, „English“ (Sprachnamen in beiden Dateien gleich), `settings_open_cd` „Settings“/„Einstellungen“.

- [x] **Step 1: Failing Tests** – `SettingsScreenTest.themeChoiceCallsAction` (Tipp „Light“ → `setTheme(LIGHT)`); `languageRowHiddenBelowApi33` (`@Config(sdk = [32])`); `languageRowShownOnApi33`; `SettingsViewModelTest.setThemePersists`; `HomeViewModelTest.labelsFollowLocaleChange` (Fake-Labeler abhängig von Locale; `localeSource.update(Locale.GERMAN)` → Zeilen enthalten das deutsche Label, ohne neue Repo-Emission); dasselbe für `EntryViewModelTest`; `AppNavGraphTest.gearOpensSettingsAndBackReturns`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren** – Theme-Auswahl als drei `RadioButton`-Zeilen (oder `SegmentedButton`), Sprache ebenso. Bis die Einstellungen geladen sind, zeigt `MainActivity` den bisherigen dunklen Stand (kein Hell-Aufblitzen bei Dunkel-Nutzern).
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(settings): settings screen with theme and per-app language; labels follow locale`.

---

### Task 6: Einstellungen – Abschnitt Erinnerungen

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/settings/ReminderSection.kt`, `ui/settings/TimePickerDialog.kt`
- Modify: `SettingsScreen.kt`, `SettingsViewModel.kt`, Strings
- Test: `ui/settings/ReminderSectionTest.kt`, `SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsViewModel.updateReminders`, `ReminderPlanner.activeIndices`, `ReminderPlanner.slotsFor` (für den Nachtruhe-Hinweis).
- Produces: `ReminderSection(s: ReminderSettings, notificationsAllowed: Boolean, onChange: ((ReminderSettings) -> ReminderSettings) -> Unit, onRequestPermission: () -> Unit, onOpenSystemSettings: () -> Unit)`.

Inhalt: Schalter „Erinnerungen“; Anzahl 1–4 (vier Knöpfe); Modus „Zufällig im Zeitfenster“ / „Feste Uhrzeiten“; je aktivem Index eine Zeile (Fenstername + „09:00–11:00“ bzw. „10:00“), Tipp öffnet Material-3-`TimePicker` (Fenster: erst Start, dann Ende); Nachtruhe-Zeile „22:00–08:00“. Hinweis „Liegt ganz in der Nachtruhe – wird übersprungen“ an betroffenen Zeilen. Ist die Benachrichtigungs-Erlaubnis nicht erteilt (API ≥ 33): Warnzeile „Benachrichtigungen sind aus“ mit Knopf „Erlauben“ (erste Anfrage per `rememberLauncherForActivityResult(RequestPermission)`, danach `Settings.ACTION_APP_NOTIFICATION_SETTINGS`). Uhrzeiten im Locale-Format (`DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)`).

- [x] **Step 1: Failing Tests** – `countTwoShowsMorningAndEvening` (zeigt genau die Fenster 0 und 3); `fixedModeShowsTimes`; `endBeforeStartIsRejected` (Ende 08:00 bei Start 09:00 → Fehlertext „Ende muss nach dem Start liegen“, `onChange` nicht aufgerufen); `windowInQuietShowsHint`; `permissionWarningOnlyWhenDenied`; `switchOffDisablesRows` (Zeilen `assertIsNotEnabled`).
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren** – Fenster-/Zeitnamen als Strings `reminder_window_morning/midday/afternoon/evening` („Morning“/„Vormittag“, „Midday“/„Mittag“, „Afternoon“/„Nachmittag“, „Evening“/„Abend“).
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(settings): reminder count, windows, fixed times and quiet hours`.

---

### Task 7: Gefühle verwalten

**Files:**
- Modify: `data/db/FeelingDao.kt` (`update`), `data/DiaryRepository.kt`, `ui/entry/NewFeelingDialog.kt` → generalisieren zu `FeelingEditDialog`, `ui/entry/EntryViewModel.kt` (Duplikat-Prüfung teilen), `AppNavHost.kt` (Route `feelings`), `SettingsScreen.kt`, Strings
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/feelings/FeelingsScreen.kt`, `FeelingsViewModel.kt`, `app/src/main/java/com/wolf/feelingsdiary/domain/FeelingNames.kt`
- Test: `DiaryRepositoryTest.kt`, `ui/feelings/FeelingsViewModelTest.kt`, `ui/feelings/FeelingsScreenTest.kt`, `EntryViewModelTest.kt`

**Interfaces:**
- Produces:
  - `DiaryRepository.updateFeeling(id: Long, name: String?, colorArgb: Int, valence: Int)` (`name` nur für eigene Gefühle, bei Grundliste `require(name == null)`; Valenz −2..2) und `setHidden(id: Long, hidden: Boolean)`.
  - `object FeelingNames { fun findDuplicate(name: String, feelings: List<Feeling>, label: (Feeling) -> String, exceptId: Long? = null): Feeling? }` (nutzt `FeelingSuggester.normalize`); `EntryViewModel.createFeeling` verwendet sie ebenfalls.
  - `FeelingEditDialog(initial: FeelingDraft?, nameEditable: Boolean, error: String?, onConfirm: (name, colorArgb, valence) -> Unit, onDismiss)`; `data class FeelingDraft(val name: String, val colorArgb: Int, val valence: Int)`. Liegt die Ausgangsfarbe nicht in der Palette, erscheint sie als zusätzliches erstes Farbfeld. Neuanlage im Eingabe-Bildschirm nutzt denselben Dialog (`initial = null`).
  - `FeelingsViewModel`: `state` = Liste in Grundliste-Reihenfolge (`FeelingSuggester.baseListOrder`) inkl. ausgeblendeter, `fun save(id, name, color, valence): Boolean` (false bei Duplikat), `fun toggleHidden(id)` (verweigert für das letzte sichtbare Gefühl), `fun add(name, color, valence)`.
- `FeelingsScreen`: je Zeile Farbpunkt, Name, Valenz-Text (`valence_good`…), Augen-Symbol ein/aus (Content-Description „Ausblenden“/„Einblenden“), Tipp öffnet Dialog; FAB/Knopf „Gefühl hinzufügen“.

- [x] **Step 1: Failing Tests** – Repo: `updateFeelingKeepsStoredEntryValences` (Valenz ändern → alter Eintrag behält Valenz, Farbe neu); `baseFeelingNameCannotChange`. VM: `renameToExistingNameIsRejected` („HAPPY“ und „fröhlich“ bei DE-Labeler → false, nichts gespeichert); `renameToOwnNameDifferentCaseIsAllowed`; `lastVisibleFeelingCannotBeHidden`; `hiddenFeelingsStayListed`. Entry: `editingEntryWithHiddenFeelingKeepsItSelectedAndVisible` (das ausgeblendete, gewählte Gefühl erscheint als Chip). Screen: `eyeIconTogglesHidden`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren** – Gewählte, aber ausgeblendete Gefühle im Eingabe-Bildschirm: oben bei „Zuletzt genutzt“ einreihen (Suggester bekommt `alwaysInclude: Set<Long>`).
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(feelings): manage feelings – edit colour and valence, rename custom, hide`.

---

### Task 8: Schnell-Blatt (Eingabe A) und Einstellung Eingabe

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/entry/QuickEntrySheet.kt`
- Modify: `EntryViewModel.kt` (Startwert Personen für A), `DiaryRepository.kt` (`suspend fun lastPeople(): Int?`), `HomeScreen.kt`/`HomeViewModel.kt` (Eingabe-Modus, Blatt öffnen), `AppNavHost.kt` (Benachrichtigung → Blatt im Modus A), `SettingsScreen.kt` (Abschnitt Eingabe), Strings
- Test: `ui/entry/QuickEntrySheetTest.kt`, `EntryViewModelTest.kt`, `HomeScreenTest.kt`, `AppNavGraphTest.kt`

**Interfaces:**
- Consumes: `EntryViewModel` (`toggle`, `setPeople`, `onQueryChange`, `save`, `state`), `InputMode`.
- Produces: `QuickEntrySheet(state: EntryUiState, actions: EntryActions, onDismiss: () -> Unit)`; `EntryViewModel.prefillPeopleFromLastEntry()` (setzt nur, wenn `people == null`); `HomeUiState.inputMode: InputMode`. `+`-Knopf und `newEntryRequests` öffnen im Modus `QUICK_SHEET` das Blatt (Home), sonst die Route `entry`.
- Inhalt Blatt: Titel „Wie fühlst du dich?“, Chips (E8), 🔍-Symbol klappt das Suchfeld mit Vorschlägen und „+ als neues Gefühl“ auf, Zeile „👥 Andere Personen“ mit −/Wert/+ (Wert 0 zeigt „allein“), Knopf „Speichern“ (aktiv ab einem Gefühl). Nach dem Speichern schließt das Blatt; ein frisches `EntryViewModel` je Öffnen (eigener `ViewModelStoreOwner`-Schlüssel), damit keine alte Auswahl stehen bleibt.

- [x] **Step 1: Failing Tests** – `minusAtZeroStaysZero`, `plusAt999StaysAt999`; `saveDisabledWithoutFeeling`; `searchToggleShowsField`; `prefillUsesLastEntryPeople` / `prefillDefaultsToZero`; `HomeScreenTest.addOpensSheetInQuickMode` / `addNavigatesInScreenMode`; `AppNavGraphTest.notificationOpensSheetInQuickMode`; `reopeningSheetStartsEmpty`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren** – Abschnitt „Eingabe“ in den Einstellungen: „Eigener Bildschirm (Standard)“ / „Schnell-Blatt von unten“.
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(entry): quick bottom sheet input mode`.

---

### Task 9: Steuerbare Android-Sicherung

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/backup/DiaryBackupAgent.kt`, `app/src/main/res/xml/backup_rules.xml` (API ≤ 30)
- Modify: `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml`, `SettingsScreen.kt` (Abschnitt Datenschutz), `SettingsViewModel.setBackup` (ruft `BackupManager(ctx).dataChanged()` beim Einschalten), `ManifestPolicyTest.kt`, `CLAUDE.md` (Regel `allowBackup="false"` ersetzen), Strings
- Test: `backup/DiaryBackupAgentTest.kt`, `ManifestPolicyTest.kt`, `SettingsScreenTest.kt`

**Interfaces:**
- Produces: `class DiaryBackupAgent : BackupAgent()` – liest `backupEnabled` direkt über `applicationContext.settingsDataStore` (`runBlocking { data.first() }`, kein Hilt: der Agent kann ohne Hilt-Graph laufen); `onFullBackup(data)` ruft nur bei `true` `super.onFullBackup(data)`; Key-Value-`onBackup`/`onRestore` leer. Testnaht: `internal open fun delegateFullBackup(data: FullBackupDataOutput)`.
- Regeln (cloud-backup **und** device-transfer gleich, sowie `backup_rules.xml`): `<include domain="database" path="." />` und `<include domain="file" path="datastore/settings.preferences_pb" />`; alles andere bleibt ausgeschlossen.
- Text (EN/DE): Schalter „Android-Sicherung“ / „Android backup“; Erklärung „Aus: Deine Einträge verlassen nie das Gerät. An: Android darf sie mit deiner Google-Sicherung und beim Gerätewechsel übertragen.“ / „Off: your entries never leave this device. On: Android may include them in your Google backup and when you move to a new phone.“

- [x] **Step 1: Failing Tests** – `DiaryBackupAgentTest.offDoesNotDelegate` / `onDelegates` (Robolectric `Robolectric.buildBackupAgent`, Spy auf `delegateFullBackup`); `defaultIsOff`; `ManifestPolicyTest`: `backupAgentDeclared` (`android:backupAgent=".backup.DiaryBackupAgent"`, `fullBackupOnly="true"`), `rulesIncludeOnlyDatabaseAndSettings` (beide Abschnitte + `backup_rules.xml`), alter Test `backupDisabled` entfällt; `SettingsScreenTest.backupSwitchCallsAction`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren**.
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün. Zusätzlich nach Installation auf dem Gerät: `adb shell bmgr backupnow com.wolf.feelingsdiary` bei „aus“ → Log zeigt 0 Bytes / keine Daten.
- [x] **Step 5: Commit** – `feat(backup): user-controlled android backup via gated backup agent`.

---

### Task 9b: Graph – Einträge über Zeitumstellung/Zeitzonenwechsel in echter Reihenfolge

(Zusatz-Task, von Wolfgang am 2026-11-08 gemeldet: bei der Umstellung auf Winterzeit rutschen neuere Einträge im Graphen vor ältere.)

**Problem:** `GraphBuilder` setzt bei `Aggregation.NONE` die x-Position nach lokaler Wandzeit und sortiert danach. Eintrag A 25.10.2026 02:50 MESZ (+02) und 20 Minuten später Eintrag B 02:10 MEZ (+01): B bekommt kleineres x als A, die Linie läuft rückwärts. Gleiches bei Reisen nach Westen.

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/domain/GraphBuilder.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/domain/GraphBuilderTest.kt`

**Interfaces / Regeln:** Bei `Aggregation.NONE` gilt x nach **echter Zeit**:
- `startEpoch` = Mitternacht von `period.start` im Offset des frühesten Eintrags des Zeitraums, `endEpoch` = Mitternacht von `period.endInclusive + 1 Tag` im Offset des spätesten Eintrags; `x = (epochMinute - startEpoch) / (endEpoch - startEpoch)`, auf 0..1 begrenzt; Reihenfolge der Punkte = (epochMinute, id).
- Gleicher Offset für alle Einträge ergibt exakt die bisherigen Positionen.
- Fallback bei `endEpoch - startEpoch < period.days * 1440 / 2`: bisherige Wandzeit-Position, aber monoton gemacht.
- Aggregierte Ansichten (DAILY/WEEKLY/MONTHLY) bleiben unverändert.

- [x] **Step 1: Failing Tests** – `fallBackKeepsRealOrder` (25.10.2026, 02:50 @ +120 vor 02:10 @ +60, x-Abstand ≈ 20/1500), `springForwardKeepsRealOrder`, `westwardTravelKeepsRealOrder`, `sameOffsetPositionsUnchanged`, `allXWithinZeroToOne`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren** (nur `GraphBuilder`).
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `fix(graph): order entries by real time across DST and time zone changes`.

---

### Task 9c: Zeitzonen-Hinweis „UTC+1“ in den Eintrag-Details

(Zusatz-Task, von Wolfgang am 2026-11-08 gewünscht, zusammen mit Task 9b.)

**Ziel:** Nach Zeitumstellung oder Reise steht in der Liste z. B. 02:50 über 02:10. Damit das verständlich ist, zeigen die Eintrag-Details hinter der Uhrzeit den UTC-Offset, **wenn sich der Offset gegenüber dem chronologisch vorigen Eintrag geändert hat** (vorige = nächstkleinere (epochMinute, id) in der ganzen Datenbank). Ohne vorigen Eintrag kein Hinweis.

**Files:**
- Modify: `data/db/EntryDao.kt`, `data/DiaryRepository.kt`, `ui/home/HomeViewModel.kt` (Zeilen-Modell), `ui/home/EntryDetailSheet.kt`, `ui/home/TimeFormat.kt`, `values/strings.xml`, `values-de/strings.xml`
- Test: `DiaryRepositoryTest`, `TimeFormatTest`, `HomeViewModelTest`, `HomeScreenTest`

**Interfaces / Regeln:**
- `DiaryRepository.previousOffsetMinutes(epochMinute: Int, id: Long): Int?` – Offset des Eintrags mit größtem (epochMinute, id) davor; null wenn keiner.
- Zeilen-Modell bekommt `offsetLabel: String?`; innerhalb der geladenen Zeilen aus dem Vorgänger ableiten, nur für den frühesten Eintrag des Zeitraums die Repo-Abfrage (eine Abfrage pro Zeitraum-Emission).
- `fun formatUtcOffset(minutes: Int): String`: `UTC+1`, `UTC+0`, `UTC-3`, `UTC+5:30`, `UTC-9:30`, `UTC+14`. Klammer-Komposition aus String-Ressource `detail_time_with_offset` = `%1$s (%2$s)` (EN und DE).
- Anzeige nur im Detail-Sheet, z. B. „Sonntag, 25. Okt. · 02:10 (UTC+1)“; die Liste bleibt unverändert.

- [x] **Step 1: Failing Tests** – `formatUtcOffset`-Tabelle; `previousOffsetFindsChronologicalPredecessor`; `offsetLabelOnlyWhenOffsetChanged`; `firstRowOfPeriodComparesWithEntryBeforePeriod`; Detail-Sheet zeigt „(UTC+1)“ nur bei gesetztem `offsetLabel`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren.**
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(home): show utc offset in entry details after an offset change`.

---

### Task 9d: Cloud-Sicherung nur Ende-zu-Ende verschlüsselt, Gerätewechsel weiter erlaubt

(Zusatz-Task, von Wolfgang am 2026-11-08 gewünscht: Cloud-Backup nur E2E-verschlüsselt; beim Handy-Umzug (D2D) sollen die Daten weiter mitgenommen werden.)

**Files:**
- Modify: `backup/DiaryBackupAgent.kt`, `res/xml/data_extraction_rules.xml`, Datenschutz-Erklärtext in `values/strings.xml` + `values-de/strings.xml`, `CLAUDE.md` (Regel Sicherung um „Cloud nur E2E“)
- Test: `backup/DiaryBackupAgentTest.kt`, `ManifestPolicyTest.kt`

**Interfaces / Regeln:**
- Der Agent gibt zusätzlich zur Einstellung nur weiter, wenn (API ≥ 28, `transportFlags`) `FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED` **oder** `FLAG_DEVICE_TO_DEVICE_TRANSFER` gesetzt ist; unter API 28 sicher geschlossen. Reine Funktion `internal fun mayLeaveDevice(enabled: Boolean, sdkInt: Int, transportFlags: Int): Boolean`.
- `data_extraction_rules.xml`: `<cloud-backup disableIfNoEncryptionCapabilities="true">` (API 31+), device-transfer unverändert; `backup_rules.xml` bleibt unverändert.
- Erklärtext (EN/DE) nennt Umzug, Ende-zu-Ende-Verschlüsselung und die nötige Bildschirmsperre.

- [x] **Step 1: Failing Tests** – `mayLeaveDevice`-Tabelle; `onWithoutEncryptionDoesNotDelegate` / `onWithEncryptionDelegates`; `ManifestPolicyTest.cloudBackupRequiresEncryption`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren.**
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(backup): cloud backup only when end-to-end encrypted, device transfer allowed`.

---

### Task 10: Onboarding

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/onboarding/OnboardingScreen.kt`, `OnboardingViewModel.kt`
- Modify: `AppNavHost.kt` (Route `onboarding`, Startziel abhängig von `onboardingDone`), Strings
- Test: `ui/onboarding/OnboardingScreenTest.kt`, `OnboardingViewModelTest.kt`, `AppNavGraphTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository.update`, `ReminderSection`-Bausteine für die Anzahl (nur Anzahl + Schalter), Berechtigungs-Launcher wie Task 6.
- Produces: `OnboardingViewModel.finish(enabled: Boolean, count: Int)` setzt `reminders.enabled/count` und `onboardingDone = true`. Navigation: bis die Einstellungen geladen sind, leere Hintergrundfläche; dann Start `onboarding` oder `home`; nach „Los geht's“ `popUpTo(onboarding) { inclusive = true }`.
- Texte (EN/DE): Titel „Willkommen“/„Welcome“; „Halte ein paar Mal am Tag fest, wie du dich fühlst und wie viele Menschen um dich sind. Deine Daten bleiben auf diesem Handy.“ / „A few times a day, note how you feel and how many people are around you. Your data stays on this phone.“; „Erinnerungen erlauben“/„Allow reminders“; „Los geht's“/„Let's start“; „Zeitfenster und Nachtruhe kannst du in den Einstellungen anpassen.“ / „You can adjust time windows and quiet hours in settings.“

- [x] **Step 1: Failing Tests** – `firstStartShowsOnboarding`, `doneStartsAtHome`; `finishStoresChoicesAndNavigatesHome` (Zurück danach verlässt die App, kein Onboarding im Back-Stack); `allowButtonOnlyOnApi33` (`@Config(sdk=[32])` → kein Knopf); `newEntryRequestDuringOnboardingIsIgnored`.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren**.
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(onboarding): first-start welcome with reminder setup and permission`.

---

### Task 10b: Erinnerung als Popup mit Standardton, Alarmton optional

(Zusatz-Task, von Wolfgang am 2026-11-08 gewünscht: „Die Erinnerungen sollen nur optional ein Alarm sein, ein Popup mit dem Default-Pling des Handys reicht.“)

**Files:**
- Modify: `domain/Settings.kt` (`ReminderSettings.alarmSound: Boolean = false`), `data/settings/SettingsRepository.kt` (Key `reminder_alarm_sound`, Fallback false), `reminder/ReminderNotifier.kt`, `ui/settings/ReminderSection.kt`, Strings EN/DE
- Test: `SettingsRepositoryTest`, `ReminderNotifierTest`, `ReminderSectionTest`

**Interfaces / Regeln:**
- Standard-Kanal `reminders`: `IMPORTANCE_HIGH` (Heads-up-Popup), Standard-Benachrichtigungston; Notification `CATEGORY_REMINDER`, `PRIORITY_HIGH`.
- Alarm-Kanal `reminders_alarm`: `IMPORTANCE_HIGH`, Ton = Standard-Alarmton mit `AudioAttributes` `USAGE_ALARM`. Kein Full-Screen-Intent, keine neue Berechtigung, kein Umgehen von „Nicht stören“.
- `show()` wählt den Kanal nach `alarmSound`.
- Schalter „Mit Alarmton“ / „Alarm sound“ in der Erinnerungs-Sektion (direkt nach dem Ein/Aus-Schalter); deaktiviert, wenn Erinnerungen aus sind.

- [x] **Step 1: Failing Tests** – Repo: `alarmSoundDefaultsOffAndRoundTrips`, korrupter Wert → false; Notifier: Standardkanal HIGH mit Standardton, Alarmkanal mit Alarm-URI/`USAGE_ALARM`, Kategorie REMINDER; Section: Schalter ruft Änderung, deaktiviert bei „aus“.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren.**
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(reminders): heads-up reminder with default sound, optional alarm tone`.

---

### Task 10c: Erinnerungs-Verspätung begrenzen, einstellbar

(Zusatz-Task, von Wolfgang am 2026-11-08 gewünscht: „ggf. sollte man das Fenster verkleinern, für den Test auf ein Minimum, ggf. in den Optionen einstellbar“.)

**Problem:** `setAndAllowWhileIdle` liefert den Alarm in einem Fenster von ≈ 0,75 × Abstand zwischen Planen und Termin aus, höchstens 1 h (auf dem A54 gemessen). Da die nächste Erinnerung direkt nach der vorigen geplant wird, kommen Erinnerungen bis zu 1 h zu spät.

**Files:**
- Modify: `domain/Settings.kt` (`ReminderSettings.maxDelayMinutes: Int = 15`, erlaubt {5, 15, 60}), `data/settings/SettingsRepository.kt` (Key `reminder_max_delay`, ungültig → 15), `domain/ReminderPlanner.kt` (`nextWake`), `reminder/ReminderScheduler.kt`, `ui/settings/ReminderSection.kt`, Strings EN/DE
- Test: `ReminderPlannerTest`, `ReminderSchedulerTest`, `SettingsRepositoryTest`, `ReminderSectionTest`

**Interfaces / Regeln (Etappen-Planung, keine neue Berechtigung, weiterhin nur `setAndAllowWhileIdle`):**
- `fun nextWake(target: Instant, now: Instant, maxDelayMinutes: Int): Instant` – liefert den Zeitpunkt, für den der Alarm jetzt gestellt wird: das Ziel selbst, wenn es nah genug ist, sonst ein Zwischen-Alarm (Etappe), sodass der finale Alarm mit Abstand ≤ 4/3 · maxDelay gestellt wird und nie nach dem Ziel liegt.
- `reschedule()` stellt den Alarm auf `nextWake(...)`; das Intent trägt das Ziel (`EXTRA_TARGET_EPOCH_MILLIS`). `onAlarm`: liegt jetzt mehr als 1 min vor dem Ziel → nur neu planen, keine Benachrichtigung; sonst bisheriges Verhalten.
- Einstellung „Genauigkeit“ mit drei Optionen (5/15/60 Min.), Untertitel „Höchstens so viel später als geplant“; deaktiviert, wenn Erinnerungen aus sind.

- [x] **Step 1: Failing Tests** – `nextWake` (nahes Ziel, fernes Ziel, Simulation: letzter Alarm ≤ Ziel + maxDelay, ≤ 6 Etappen für 24 h); Scheduler: Etappe zeigt nichts und plant neu, finaler Alarm zeigt; Repo-Roundtrip + Fallback; Section-UI.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren.**
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(reminders): staged scheduling bounds reminder delay, configurable accuracy`.

---

### Task 10d: Hinweis „Akku nicht einschränken“, damit Erinnerungen pünktlich kommen

(Zusatz-Task, von Wolfgang am 2026-11-08 gewünscht nach Gerätetest.)

**Befund (Galaxy A54, One UI):** Samsung „Freecess“ friert die App 1–2 min nach dem Wechsel in den Hintergrund ein; Alarme eingefrorener Apps werden zurückgehalten (`AlarmManager: Deferring alarm … due to freecess`). Abhilfe: App in den Akku-Einstellungen auf „Nicht eingeschränkt“.

**Files:**
- Create: `reminder/BatteryOptimization.kt` (+ Hilt-Bindung)
- Modify: `ui/settings/ReminderSection.kt`, `ui/settings/SettingsScreen.kt`, `ui/onboarding/OnboardingScreen.kt`, Strings EN/DE
- Test: `BatteryOptimizationTest`, `ReminderSectionTest`, `OnboardingScreenTest`

**Interfaces / Regeln:**
- Keine neue Berechtigung (kein `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, Play-Policy-Risiko); die App **erkennt** den Zustand und **führt** zur Einstellung.
- Injizierbare Naht `BatteryOptimization` (`isUnrestricted()` über `PowerManager.isIgnoringBatteryOptimizations`, `settingsIntent()` auf `ACTION_APPLICATION_DETAILS_SETTINGS`, Fallback `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`).
- Hinweiszeile („Erinnerungen können sich verspäten“ mit Knopf „Akku-Einstellungen öffnen“) nur, wenn Erinnerungen an **und** nicht ausgenommen; Zustand bei ON_RESUME neu prüfen. In der Erinnerungs-Sektion und im Onboarding unter „Erinnerungen erlauben“. Kein Banner im Verlauf.

- [x] **Step 1: Failing Tests** – `BatteryOptimizationTest` (beide Zustände, Intent), Hinweis sichtbar/unsichtbar, Knopf ruft Aktion, Onboarding-Zeile.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren.**
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `feat(reminders): guide users to unrestricted battery so reminders arrive on time`.

---

### Task 10e: Nacharbeit 10c – ehrliche Beschriftung, keine doppelte Erinnerung

(Zusatz-Task aus dem Review von Task 10c; Beschriftung von Wolfgang bestätigt.)

**Files:**
- Modify: `data/settings/SettingsRepository.kt` (Key `reminder_last_shown`, Long, tolerant parsen), `reminder/ReminderScheduler.kt`, Strings EN/DE
- Test: `SettingsRepositoryTest`, `ReminderSchedulerTest`, `ReminderSectionTest`

**Interfaces / Regeln:**
- Beschriftung Genauigkeit: „Bis 5 Min. später“, „Bis 15 Min. später“, „Bis 60 Min. später (spart Akku)“ / „Up to 5 min late“, „Up to 15 min late“, „Up to 60 min late (saves battery)“ (nur die Verspätung ist begrenzt, „±“ wäre irreführend).
- Doppelte Erinnerung verhindern: kommt ein Etappen-Alarm in [Ziel − 1 min, Ziel) an, zählt er als Erinnerung; ein anderer `reschedule()`-Aufrufer (Kaltstart, BootReceiver, Einstellungsänderung) plante danach dasselbe Ziel erneut. Fix: zuletzt gezeigtes Ziel in DataStore persistieren, beim Anzeigen setzen; `reschedule()` plant immer nach `max(now, lastShown)`.

- [x] **Step 1: Failing Tests** – Etappe 30 s vor Ziel wird angezeigt; danach `reschedule()` ohne Argument plant den Folge-Slot, nicht dasselbe Ziel; Repo-Roundtrip für `reminder_last_shown`; alte „±“-Tests angepasst.
- [x] **Step 2: Laufen lassen** → FAIL.
- [x] **Step 3: Implementieren.**
- [x] **Step 4: Laufen lassen** → PASS, volle Prüfkette grün.
- [x] **Step 5: Commit** – `fix(reminders): honest accuracy labels, never remind twice for the same slot`.

---

### Task 11: Gerätetest, Doku, PR

**Files:**
- Modify: `README.md` (Stand Phase 3), `CLAUDE.md` (Pakete `reminder`, `backup`, `data/settings`, `ui/settings`, `ui/feelings`, `ui/onboarding`; Regel Sicherung; Berechtigungs-Liste)

- [ ] **Step 1: Gerätetest** (Galaxy A54, `adb -s RZCW81045KL`, adb unter `C:/Users/Admin/AppData/Local/Android/Sdk/platform-tools/adb.exe`; Screenshots aus Git Bash `adb exec-out screencap -p > f.png`; Wolfgang fasst das Handy währenddessen nicht an): App-Daten löschen → Onboarding → Berechtigung erlauben; Einstellungen: Fenster so legen, dass in ~3 min eine Erinnerung kommt → Benachrichtigung erscheint, Tipp öffnet Eingabe; `adb shell dumpsys alarm | grep feelingsdiary` zeigt genau einen Alarm; `adb reboot` → Alarm wieder da; Theme Hell/System/Dunkel; Sprache EN↔DE → Gefühlsnamen im Verlauf sofort gewechselt; Schnell-Blatt-Modus + Benachrichtigung → Blatt; Gefühl umbenennen/ausblenden; Sicherung aus → `bmgr backupnow` liefert nichts.
- [x] **Step 2: Doku committen** – `docs: readme and claude guide for phase 3`.
- [ ] **Step 3: Push + PR** – `git remote get-url origin` und `git branch --show-current` prüfen; `git push -u origin feat/phase3-reminders-settings`; `gh pr create --base main` (Body: Zusammenfassung, Planer-Entscheidungen E1–E11, Gerätetest mit Screenshots, endet mit `🤖 Generated with [Claude Code](https://claude.com/claude-code)`); `gh pr checks --watch` → `build` grün. Nicht mergen.

---

## Nachträge während der Umsetzung

Die auf Wunsch von Wolfgang ergänzten Zusatz-Tasks stehen als vollständige Abschnitte hinter ihrem Haupt-Task: **9b, 9c, 9d** (nach Task 9) und **10b, 10c, 10d, 10e** (nach Task 10). Ihr Stand steht in der Tabelle „Bearbeitungsstand“ oben.

### Fix-Runde nach Gesamt-Review

- Beschädigte oder unlesbare Einstellungsdatei: Rückfall auf Standardwerte.
- Sommerzeit-Lücke (Vorstellen der Uhr): kein Termin geht verloren.
- Nachtruhe: kein verspäteter Alarm in der Nachtruhe.
- Neuplanung im `finally`, sodass die Alarmkette bei Fehlern erhalten bleibt.
- Theme-Startwert bei Neuerstellung der Activity (kein Aufblitzen).
- Erinnerungen erst nach abgeschlossenem Onboarding.
- Benachrichtigungs-Berechtigung bei „Los geht's“ anfragen.
- Sicherungsschalter unter API 28 ausgeblendet.

### Gerätetest-Befunde

- **Samsung Freecess (Galaxy A54):** Die Erinnerungen von 15:10 und 15:25 kamen nicht. Ursache laut Log: `AlarmManager: Deferring alarm … due to freecess` – Samsung friert die App 1–2 min nach dem Wechsel in den Hintergrund ein und hält ihre Alarme zurück. Mit Akku „Nicht eingeschränkt“ kam die Erinnerung von 15:45 um 15:49 (Benachrichtigung angezeigt, nächste um 20:00). Kein Code-Fehler → Task 10d (Hinweis + Knopf zu den Akku-Einstellungen).
- **Verspätung durch das `setAndAllowWhileIdle`-Fenster:** Android liefert in einem Fenster von ≈ 0,75 × Abstand aus (höchstens 1 h); gemessen 2 min 19 s bei 3 min Vorlauf → Task 10c (Etappen-Planung, Genauigkeit einstellbar).
- Ohne Befund: Onboarding, Berechtigungsdialog, Rücksicherung der Datenbank, Einstellungen/Zeitwähler, sofortiges Neuplanen, Heads-up-Popup (Wichtigkeit 4, Kategorie Erinnerung).
