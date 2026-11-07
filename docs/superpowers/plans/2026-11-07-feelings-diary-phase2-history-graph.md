# FeelingsDiary – Phase 2: Verlauf-Graph – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Startseite wird zum Verlauf „Variante B“: Graph (Valenz-Linie in Hauptgefühl-Farben + Personen-Fläche) mit Filtern Jahr/Monat/Woche/Tag/Tagesbereich, Blättern, Aggregation, Legende und Eintrag-Details mit Bearbeiten/Löschen; dazu die drei aus Phase 1 verschobenen Punkte (R8).

**Architecture:** Reine Graph-Logik in `domain` (`GraphPeriod` = Zeitraum + Blättern, `GraphBuilder` = Einträge → Punkte/Aggregation/Skalen), testbar ohne Android. `DiaryRepository` bekommt Zeitraum-Abfrage, Laden, Ändern und Löschen eines Eintrags (Room-Schema bleibt Version 1, keine Migration). Gezeichnet wird mit eigenem Compose-`Canvas` (keine Chart-Bibliothek); Geometrie und Tipp-Erkennung liegen in einer reinen Kotlin-Datei, damit sie ohne Rendering testbar sind.

**Tech Stack:** wie Phase 1 (Kotlin 2.4.21, Compose BOM 2026.09.00 / Material 3 inkl. `DateRangePicker` und `ModalBottomSheet`, Room 2.8.5, Hilt 2.60.1, Navigation-Compose 2.10.2, JUnit4 + MockK + Robolectric + Compose UI-Test). Keine neuen Abhängigkeiten.

**Spec:** `docs/superpowers/specs/2026-11-07-feelings-diary-design.md` (v. a. 5.3, 5.4, 6; Mockups `docs/superpowers/specs/mockups/graph-b-themes.html` und `graph-styles.html`, Variante B). Vorgänger: `docs/superpowers/plans/2026-11-07-feelings-diary-phase1-foundation-entry.md`.

## Global Constraints

- Alle Regeln aus `CLAUDE.md` und den Global Constraints von Phase 1 gelten weiter (kein `INTERNET`, `allowBackup="false"`, keine exakten Alarme, jeder UI-Text in `values/strings.xml` **und** `values-de/strings.xml`, Personen 0–999, Valenz −2…+2 als Momentaufnahme, Hauptgefühl = Position 0).
- Y-Achse: oben **gut** (+2), Mitte **neutral** (0), unten **schlecht** (−2); Beschriftung mit den vorhandenen Strings `valence_good`/`valence_neutral`/`valence_bad` (Spec 5.3).
- Höhe eines Eintrags = **Durchschnitt der gespeicherten Valenzen** seiner Gefühle (Snapshot, nicht die aktuelle Valenz des Gefühls); **Farbe** = **aktuelle** Farbe des Hauptgefühls (Spec 5.4).
- Linie läuft zwischen zwei Punkten als Farbverlauf von Hauptgefühl-Farbe zu Hauptgefühl-Farbe; weitere Gefühle als kleine Farbpunkte am Eintrag (Spec 5.3).
- Personen als halbtransparente Fläche (Alpha 0.16) mit gestrichelter Kante (Dash 3/2) **hinter** der Linie, eigene Skala rechts, Beschriftung `👥 Personen`/`👥 People` (Mockup).
- Zeitraum-Grenzen und Tageszuordnung immer nach der **lokalen Zeit des Eintrags** (`EntryTime.localDateTime()`, gespeicherter Offset), nie nach der aktuellen Zone.
- Wochenbeginn nach Locale (`WeekFields.of(locale).firstDayOfWeek`; DE Montag, EN-US Sonntag).
- Jahresansicht bis ~1.500 Einträge flüssig: höchstens 366 gezeichnete Punkte je Ansicht (Aggregation, siehe Task 4) (Spec 6).
- Bauen mit `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot"`; `./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug` muss am Ende jedes Tasks grün sein.

## Planer-Entscheidungen (von Wolfgang im PR übersteuerbar)

| # | Frage | Entscheidung im Plan |
|---|---|---|
| E1 | Bleibt die Phase-1-Eintragsliste? | Ja, **unter Graph + Legende**, gefiltert auf den gewählten Zeitraum, neueste zuerst. Tipp auf eine Zeile öffnet dieselben Details wie ein Tipp auf den Punkt. Grund: TalkBack-Zugang zu jedem Eintrag, ohne Punkte im Canvas antippen zu müssen. |
| E2 | Was ist beim Bearbeiten änderbar? | Gefühle (inkl. Reihenfolge/Hauptgefühl) und Personenzahl. **Zeitpunkt nicht** (bleibt wie gespeichert). |
| E3 | „Tag“ vs. „Tagesbereich“ | Chips **Jahr · Monat · Woche · Tag** wie im Mockup; Tagesbereich über ein Kalender-Symbol rechts neben den Chips (Material `DateRangePicker`). Ein gewählter Bereich hebt keinen Chip hervor. |
| E4 | Aggregation | Tag/Woche/Monat und Bereich ≤ 31 Tage: jeder Eintrag ein Punkt. Jahr und Bereich 32–366 Tage: **Tagesmittel**. Bereich > 366 Tage: **Wochenmittel**. Tipp auf einen aggregierten Punkt springt in die Tag- bzw. Wochen-Ansicht dieses Zeitraums. |
| E5 | Löschen | Bestätigungsdialog (kein Rückgängig-Snackbar). |
| E6 | ⚙ im Kopf (Mockup) | Entfällt bis Phase 3 (Einstellungen gibt es noch nicht). |
| E7 | Lücken in der Linie | Linie verbindet alle Punkte des Zeitraums (wie Mockup), auch über Tage ohne Eintrag. |
| E8 | Start-Zeitraum | Beim App-Start: **Woche**, die heute enthält. Gewählter Zeitraum überlebt Drehen/Prozess-Tod (SavedStateHandle), nicht aber einen Neustart. |

## Review Focus

1. Eintrag 00:30 Ortszeit in UTC+2 (also 22:30 UTC am Vortag) erscheint im Tag/der Woche seines **lokalen** Datums, auch wenn das Gerät jetzt in UTC steht; Einträge am Rand (+14 h/−12 h Offset) gehen nicht verloren. → Test in Task 3.
2. Bearbeiten eines Eintrags behält Zeitpunkt und die **alte** Valenz unveränderter Gefühle; neu hinzugefügte Gefühle bekommen die aktuelle Valenz; ein Bearbeiten, das nur die Reihenfolge ändert, ändert nur das Hauptgefühl. → Test in Task 3.
3. Leerer Zeitraum, genau ein Eintrag, mehrere Einträge in derselben Minute: kein Absturz, keine Division durch 0; leerer Zeitraum zeigt Hinweistext, ein Eintrag = Punkt ohne Linie. → Tests in Task 4 und Task 5.
4. Zeitraum mit Sommerzeit-Umstellung (Woche 19.–25.10.2026, Umstellung am 25.10. in DE) und Schaltjahr (2028, 366 Tage, 29. Feb): x-Positionen streng monoton, Blättern trifft immer lückenlos den Nachbarzeitraum (31. Jan → Feb → März; Woche über Jahreswechsel). → Test in Task 4 (GraphPeriod).
5. Eintrag wird gelöscht, während seine Details offen sind, oder ein gelöschtes Gefühl/fehlende Farbe: Sheet schließt, Graph aktualisiert sich ohne Absturz; Sprache/Locale wechselt zur Laufzeit → Datums- und Achsentexte folgen sofort (Deferral R8a). → Tests in Task 6.

---

### Task 1: Pre-Commit-Hook – JDK-Auswahl und Test-Task (Deferral R8b)

**Files:**
- Create: `scripts/select-jdk.sh` (nur die JDK-Auswahl, wird von `pre-commit` per `source` eingebunden)
- Modify: `scripts/pre-commit`, `scripts/install-hooks.sh`
- Modify: `CLAUDE.md` (Abschnitt Befehle: Hook-Verhalten in einer Zeile)

**Interfaces:**
- Produces: Hook wählt ein JDK, das `bin/jlink` (bzw. `jlink.exe`) besitzt, und führt `./gradlew ktlintCheck testDebugUnitTest` aus.

- [ ] **Step 1: JDK-Auswahl nach `scripts/select-jdk.sh` auslagern** (`pre-commit` macht nach dem `cd "$REPO_ROOT"` ein `source scripts/select-jdk.sh`) – Ein gesetztes `JAVA_HOME` wird nur behalten, wenn `"$JAVA_HOME/bin/jlink"` oder `"$JAVA_HOME/bin/jlink.exe"` existiert. Sonst Kandidaten in dieser Reihenfolge, erster mit `jlink` gewinnt: `/c/Program Files/Eclipse Adoptium/jdk-21*`, `$HOME/.jdks/temurin-21*`, `$HOME/.jdks/*21*`, `/c/Program Files/Android/Android Studio/jbr`, `/Applications/Android Studio.app/Contents/jbr/Contents/Home`. Kein Treffer → Meldung `✗ Kein JDK mit jlink gefunden – JAVA_HOME auf JDK 21 setzen` und `exit 1`. Gewähltes JDK ausgeben (`echo "JDK: $JAVA_HOME"`).
- [ ] **Step 2: Test-Task** – `./gradlew test` → `./gradlew testDebugUnitTest` (Release-Unit-Tests verdoppeln nur die Laufzeit; CI nutzt ebenfalls `testDebugUnitTest`).
- [ ] **Step 3: Worktree-fähige Installation** – `install-hooks.sh` schreibt nach `"$(git rev-parse --git-common-dir)/hooks/pre-commit"` statt `.git/hooks` (in Worktrees ist `.git` eine Datei).
- [ ] **Step 4: Prüfen** – `bash -n scripts/pre-commit scripts/install-hooks.sh` → keine Ausgabe. Im Worktree `bash scripts/install-hooks.sh` → Erfolgsmeldung. `env -u JAVA_HOME bash -c '. scripts/select-jdk.sh && echo $JAVA_HOME'` → Adoptium-JDK-21-Pfad. Mit `JAVA_HOME` auf eine IntelliJ-JBR ohne `jlink` gesetzt → ebenfalls Adoptium-Pfad.
- [ ] **Step 5: Commit** – `git add scripts/select-jdk.sh scripts/pre-commit scripts/install-hooks.sh CLAUDE.md`; `git commit -m "build(hooks): pick a JDK with jlink and run debug unit tests only"`.

---

### Task 2: Personen-Knöpfe ≥ 48 dp auf schmalen Geräten (Deferral R8c)

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/ui/entry/PeopleSelector.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/ui/entry/PeopleSelectorTest.kt`

**Interfaces:**
- Consumes/Produces: Signatur `PeopleSelector(people: Int?, onPeople: (Int) -> Unit)` bleibt unverändert.

- [ ] **Step 1: Failing Test** – in `PeopleSelectorTest` (vorhandenes `show()` mit 288 dp und `LARGE_FONT_SCALE`): `everyButtonIsAtLeast48dpWide` – für die Knoten mit Content-Description `"Alone, no other people"`, `"1 other people"` … `"5 other people"` und `"6+ opens a number field"` gilt `getBoundsInRoot().width >= 48.dp` und `height >= 48.dp`. Zusätzlich `numbersStayOnOneRowWhenTheyFit`: bei `Density(density, 1f)` und 360 dp Breite haben alle sieben Knöpfe dasselbe `top`.
- [ ] **Step 2: Laufen lassen** – `./gradlew testDebugUnitTest --tests "*PeopleSelectorTest*"` → FAIL (Zahlknöpfe < 48 dp bei großer Schrift).
- [ ] **Step 3: Implementieren** – `BoxWithConstraints`; Breite des „allein“-Labels mit `rememberTextMeasurer()` + `MaterialTheme.typography.labelLarge` messen (+ `2 * ALONE_LABEL_PADDING`). Passt `aloneWidth + 6 * 48.dp + 6 * BUTTON_GAP` in `maxWidth` → bisherige einzeilige Anordnung. Sonst zwei Zeilen: Zeile 1 = allein, 1, 2, 3; Zeile 2 = 4, 5, 6+; Zahlknöpfe je Zeile mit `weight(1f)`.
- [ ] **Step 4: Laufen lassen** → PASS (inkl. der bestehenden Ein-Zeilen-Tests für die Labels).
- [ ] **Step 5: Commit** – `git commit -m "fix(entry): wrap people buttons to keep 48dp targets on narrow screens"`.

---

### Task 3: Repository – Zeitraum, Laden, Ändern, Löschen

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/data/db/EntryDao.kt`, `app/src/main/java/com/wolf/feelingsdiary/data/DiaryRepository.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/data/DiaryRepositoryTest.kt`, `app/src/test/java/com/wolf/feelingsdiary/data/AppDatabaseTest.kt`

**Interfaces:**
- Consumes: Phase-1-Entities/DAOs, `Entry`, `EntryTime`.
- Produces:
  - `EntryDao`: `@Transaction @Query("SELECT * FROM entries WHERE epochMinute BETWEEN :fromMinute AND :toMinute ORDER BY epochMinute, id") fun observeBetween(fromMinute: Int, toMinute: Int): Flow<List<EntryWithFeelings>>`; `@Transaction @Query(... WHERE id = :id) suspend fun getById(id: Long): EntryWithFeelings?`; `@Query("UPDATE entries SET people = :people WHERE id = :id") suspend fun updatePeople(id: Long, people: Int): Int`; `@Query("DELETE FROM entry_feelings WHERE entryId = :id") suspend fun deleteFeelingsOf(id: Long)`; `@Transaction suspend fun replace(id: Long, people: Int, feelings: List<EntryFeelingEntity>)` (updatePeople, deleteFeelingsOf, insertEntryFeelings).
  - `DiaryRepository`:
    - `fun observeEntriesBetween(from: LocalDate, toInclusive: LocalDate): Flow<List<Entry>>` – DB-Fenster `from.atStartOfDay(UTC) − 14 h` bis `toInclusive+1 .atStartOfDay(UTC) + 12 h` (in epochMinute), danach Filter `time.localDateTime().toLocalDate() in from..toInclusive`; aufsteigend nach Zeit.
    - `suspend fun getEntry(id: Long): Entry?`
    - `suspend fun updateEntry(id: Long, people: Int, feelingIds: List<Long>)` – gleiche `require`s wie `addEntry`; zusätzlich `require(Eintrag existiert)`. Valenz: für Gefühle, die schon im Eintrag waren, die **gespeicherte** Valenz; für neue die aktuelle des Gefühls. Zeit unverändert. `touch` nur für neu hinzugekommene Gefühle.
    - `suspend fun deleteEntry(id: Long)`
  - Room-Schema bleibt `version = 1` (nur neue Queries).

- [ ] **Step 1: Failing Tests** (`DiaryRepositoryTest`, In-Memory-DB, fester Clock wie Phase 1):
  - `rangeUsesEntryLocalDate`: Clock `2026-07-01T22:30:00Z` in `Europe/Berlin` (lokal 2. Juli 00:30) → `addEntry`; `observeEntriesBetween(2026-07-02, 2026-07-02).first()` enthält ihn, `observeEntriesBetween(2026-07-01, 2026-07-01).first()` ist leer (Review Focus 1).
  - `rangeKeepsExtremeOffsets`: Einträge mit Zone `Pacific/Kiritimati` (+14) lokal 2026-07-02 00:10 und `Etc/GMT+12` (−12) lokal 2026-07-02 23:50 → beide in `observeEntriesBetween(2026-07-02, 2026-07-02)`, aufsteigend sortiert.
  - `updateKeepsTimeAndOldValences`: Eintrag [stressed, tired] anlegen; Valenz von tired in der DB auf +1 ändern; `updateEntry(id, 4, listOf(tired, happy))` → `getEntry(id)`: `time` unverändert, `people == 4`, `feelingIds == listOf(tired, happy)`, `valences == listOf(-1, 2)` (Review Focus 2).
  - `updateRejectsUnknownEntryAndInvalidInput`: `assertThrows<IllegalArgumentException>` für unbekannte id, leere Liste, `people = 1000`.
  - `deleteEntryRemovesIt`: nach `deleteEntry(id)` → `getEntry(id) == null`.
- [ ] **Step 2: Laufen lassen** – `./gradlew testDebugUnitTest --tests "*DiaryRepositoryTest*"` → FAIL.
- [ ] **Step 3: DAO-Methoden und Repository-Funktionen implementieren** (Mapping wie Phase 1 über das vorhandene `EntryWithFeelings.toDomain()`).
- [ ] **Step 4: Laufen lassen** – `./gradlew testDebugUnitTest` → PASS; `app/schemas/.../1.json` unverändert (`git diff --exit-code app/schemas`).
- [ ] **Step 5: Commit** – `git commit -m "feat(data): entry range query, update and delete"`.

---

### Task 4: Domain – Zeitraum und Graph-Daten

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/domain/GraphPeriod.kt`, `domain/GraphBuilder.kt`
- Modify: `domain/FeelingSuggester.kt` (privates `listOrder` als öffentliche `fun baseListOrder(feelings: List<Feeling>, label: (Feeling) -> String): List<Feeling>` herausziehen, Verhalten unverändert)
- Test: `app/src/test/java/com/wolf/feelingsdiary/domain/GraphPeriodTest.kt`, `domain/GraphBuilderTest.kt`

**Interfaces:**
- Consumes: `Entry`, `EntryTime`, `Feeling`.
- Produces:
  - `enum class PeriodKind { YEAR, MONTH, WEEK, DAY, RANGE }`
  - `data class GraphPeriod(val kind: PeriodKind, val start: LocalDate, val endInclusive: LocalDate)` mit `val days: Int`, `fun shift(steps: Int, firstDayOfWeek: DayOfWeek): GraphPeriod` (YEAR/MONTH/WEEK/DAY: Nachbar-Kalendereinheit; RANGE: um `days` verschoben), `companion object { fun containing(kind: PeriodKind, date: LocalDate, firstDayOfWeek: DayOfWeek): GraphPeriod; fun range(a: LocalDate, b: LocalDate): GraphPeriod }` (`range` sortiert a/b; `containing(RANGE, …)` wirft `IllegalArgumentException`).
  - `enum class Aggregation { NONE, DAILY, WEEKLY }`
  - `data class GraphPoint(val x: Float, val valence: Float, val mainFeelingId: Long, val otherFeelingIds: List<Long>, val people: Float, val entryId: Long?, val bucketStart: LocalDate)` – `x` in 0..1; `entryId` nur bei `NONE`.
  - `data class GraphData(val points: List<GraphPoint>, val aggregation: Aggregation, val peopleScaleMax: Int, val feelingIds: Set<Long>)`
  - `object GraphBuilder { fun build(entries: List<Entry>, period: GraphPeriod, firstDayOfWeek: DayOfWeek): GraphData }`
- Regeln (`GraphBuilder`):
  - Aggregation nach E4: `NONE` für DAY/WEEK/MONTH und RANGE ≤ 31 Tage; `DAILY` für YEAR und RANGE ≤ 366 Tage; sonst `WEEKLY` (Wochen ab `firstDayOfWeek`).
  - `x` = Minuten von `start.atStartOfDay()` bis zur lokalen Zeit (Wanduhr, `ChronoUnit.MINUTES` auf `LocalDateTime`) / `days * 1440`; aggregierte Punkte bei der Mitte ihres Buckets. Einträge außerhalb des Zeitraums werden ignoriert.
  - Eintrag: `valence = valences.average()`; aggregiert: Mittel der Eintrags-Mittel; `people` = Mittel der Personen.
  - Aggregiertes Hauptgefühl = häufigstes Hauptgefühl im Bucket, bei Gleichstand das des späteren Eintrags; `otherFeelingIds` = übrige Gefühle des Buckets nach Häufigkeit, höchstens 3 (bei `NONE`: die übrigen Gefühle des Eintrags in Positionsreihenfolge, ohne Limit).
  - `peopleScaleMax` = kleinster Wert aus `5, 10, 20, 50, 100, 200, 500, 1000`, der ≥ maximaler `people` ist.
  - `feelingIds` = alle Gefühle (Haupt + weitere) der Einträge im Zeitraum (Legende).

- [ ] **Step 1: Failing Tests `GraphPeriodTest`**
  - `weekStartsByLocale`: `containing(WEEK, 2026-11-07, MONDAY)` → 2026-11-02…2026-11-08; mit `SUNDAY` → 2026-11-01…2026-11-07.
  - `shiftIsGapless`: MONTH 2026-01 `shift(1)` → 2026-02-01…2026-02-28, nochmal → März; YEAR 2028 hat `days == 366`; WEEK mit Start 2026-12-28 `shift(1)` → 2027-01-04…; RANGE 2026-11-01…2026-11-10 `shift(-1)` → 2026-10-22…2026-10-31 (Review Focus 4).
  - `rangeSortsEnds`: `range(2026-11-10, 2026-11-01).start == 2026-11-01`.
- [ ] **Step 2: Failing Tests `GraphBuilderTest`** (Einträge per Helfer `entry(id, "2026-11-03T08:00", offsetMin = 60, people, feelings = listOf(a to 2, b to -1))`)
  - `entryHeightIsMeanOfStoredValences`: Gefühle mit Valenz +2 und −1 → `valence == 0.5f`, `mainFeelingId == a`, `otherFeelingIds == listOf(b)`.
  - `xIsPositionInPeriod`: Woche ab Mo 2026-11-02, Eintrag Mi 12:00 → `x == (2*1440 + 720) / (7*1440f)`.
  - `emptyAndSingle`: leere Liste → `points.isEmpty()`, `peopleScaleMax == 5`; ein Eintrag → genau ein Punkt (Review Focus 3).
  - `sameMinuteEntriesBothKept`: zwei Einträge gleiche Minute → zwei Punkte, gleiches `x`, keine Exception.
  - `dstWeekIsMonotonic`: Woche 2026-10-19…25 (`Europe/Berlin`-Offsets +120/+60 je Eintrag), Einträge stündlich am 25.10. → `x` streng steigend (Review Focus 4).
  - `yearAggregatesDaily`: 1.500 Einträge über 2026 (4/Tag) → `aggregation == DAILY`, `points.size <= 366`, Tag mit Hauptgefühlen [a, b, b] → `mainFeelingId == b`; Gleichstand [a, b] → Hauptgefühl des späteren Eintrags.
  - `longRangeAggregatesWeekly`: RANGE 2026-01-01…2027-06-30 → `aggregation == WEEKLY`.
  - `peopleScale`: max people 0 → 5; 7 → 10; 999 → 1000.
- [ ] **Step 3: Laufen lassen** – `./gradlew testDebugUnitTest --tests "*GraphPeriodTest*" --tests "*GraphBuilderTest*"` → FAIL.
- [ ] **Step 4: Implementieren** (inkl. `baseListOrder`-Refactoring; `FeelingSuggesterTest` muss unverändert grün bleiben).
- [ ] **Step 5: Laufen lassen** → PASS.
- [ ] **Step 6: Commit** – `git commit -m "feat(domain): graph periods and graph data with aggregation"`.

---

### Task 5: Graph-Composable + Legende

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/home/GraphGeometry.kt`, `ui/home/HistoryGraph.kt`, `ui/home/GraphLegend.kt`
- Modify: `values/strings.xml`, `values-de/strings.xml`
- Test: `app/src/test/java/com/wolf/feelingsdiary/ui/home/GraphGeometryTest.kt`, `ui/home/HistoryGraphTest.kt`

**Interfaces:**
- Consumes: `GraphData`, `GraphPoint` (Task 4).
- Produces:
  - `GraphGeometry.kt` (reines Kotlin, `androidx.compose.ui.geometry.Offset`/`Rect` erlaubt): `fun plotRect(width: Float, height: Float, density: Float): Rect` (Ränder links 48 dp, rechts 28 dp, oben 8 dp, unten 20 dp), `fun valenceToY(valence: Float, plot: Rect): Float` (+2 → `plot.top`, −2 → `plot.bottom`), `fun peopleToY(people: Float, max: Int, plot: Rect): Float`, `fun pointOffsets(points: List<GraphPoint>, plot: Rect): List<Offset>`, `fun hitTest(offsets: List<Offset>, tap: Offset, maxDistancePx: Float): Int?` (nächster Punkt innerhalb des Radius, sonst `null`).
  - `data class AxisLabel(val x: Float, val text: String)`
  - `@Composable fun HistoryGraph(data: GraphData, colorOf: (Long) -> Color, xLabels: List<AxisLabel>, onPointTap: (GraphPoint) -> Unit, modifier: Modifier = Modifier)` – Höhe 200 dp; links Y-Texte gut/neutral/schlecht, rechts `0` und `peopleScaleMax`, unten `xLabels`; neutral-Linie gestrichelt in `onSurfaceVariant`; Personen-Fläche (Polygon über alle Punkte, Füllung `primary` Alpha 0.16, Kante gestrichelt) zuerst; dann je Segment `Brush.horizontalGradient(colorOf(a.main), colorOf(b.main), startX, endX)`, Strichbreite 2.5 dp; Punkte Radius 4 dp in Hauptgefühl-Farbe; weitere Gefühle als Punkte Radius 2 dp im Abstand 6 dp darunter. Tipp-Radius 24 dp. Unbekannte Gefühls-id → `onSurfaceVariant`.
  - Semantik: `contentDescription` = `history_graph_cd` (Plural: „Graph, %d entries“ / „Graph, %d Einträge“).
  - `@Composable fun GraphLegend(feelings: List<Pair<String, Color>>)` – `FlowRow`, Farbpunkt + Label, Reihenfolge = `FeelingSuggester.baseListOrder`.
  - Neue Strings: `history_people_axis` („👥 People“/„👥 Personen“), Plural `history_graph_cd`.

- [ ] **Step 1: Failing Tests `GraphGeometryTest`**: `valenceToY` mit `Rect(0,0,100,200)`: +2 → 0, 0 → 100, −2 → 200; `peopleToY(5f, 10, rect) == 100f`; `hitTest` trifft den nächsten von zwei Punkten innerhalb 24 px und liefert `null` bei 30 px Abstand; leere Liste → `null`.
- [ ] **Step 2: Failing Tests `HistoryGraphTest`** (Robolectric + `createComposeRule`): `tapOnPointReportsIt` – zwei Punkte bei x = 0.25/0.75, `performTouchInput { click(pointOffsets(points, plotRect(width, height, density))[0]) }` → `onPointTap` mit dem ersten Punkt; `emptyDataDrawsWithoutCrash` und `singlePointDrawsWithoutCrash` (Review Focus 3); `hasGraphDescription` – Knoten mit `"Graph, 2 entries"`.
- [ ] **Step 3: Laufen lassen** → FAIL.
- [ ] **Step 4: Implementieren.**
- [ ] **Step 5: Laufen lassen** – `./gradlew testDebugUnitTest` → PASS (inkl. `StringsParityTest`).
- [ ] **Step 6: Commit** – `git commit -m "feat(home): canvas history graph with people area and legend"`.

---

### Task 6: Verlauf-Startseite (Filter, Blättern, Liste, Details, Löschen, Zeitformat)

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/home/PeriodBar.kt`, `ui/home/EntryDetailSheet.kt`, `ui/home/TimeFormat.kt`
- Modify: `values/strings.xml`, `values-de/strings.xml` (`home_placeholder` entfernen)
- Test: `app/src/test/java/com/wolf/feelingsdiary/ui/home/HomeViewModelTest.kt`, `ui/home/HomeScreenTest.kt`, `ui/home/TimeFormatTest.kt`

**Interfaces:**
- Consumes: `DiaryRepository.observeEntriesBetween/deleteEntry`, `GraphPeriod`, `GraphBuilder`, `HistoryGraph`, `GraphLegend`, `FeelingLabeler`.
- Produces:
  - `data class HomeEntryRow(val id: Long, val time: LocalDateTime, val feelings: List<Pair<String, Int>>, val people: Int)` – **Zeit als `LocalDateTime`**, formatiert wird erst in der UI (Deferral R8a: bisher wurde der Formatter einmal im ViewModel mit der Start-Locale gebaut).
  - `TimeFormat.kt`: `fun formatEntryTime(t: LocalDateTime, locale: Locale): String` (`ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)`), `fun formatPeriod(p: GraphPeriod, locale: Locale): String` (YEAR „2026“; MONTH `LLLL yyyy`; DAY `ofLocalizedDate(FULL)`; WEEK/RANGE „start – end“ mit `ofLocalizedDate(MEDIUM)`), `fun xAxisLabels(p: GraphPeriod, locale: Locale): List<AxisLabel>` (DAY 0/6/12/18 Uhr; WEEK Wochentag kurz `TextStyle.SHORT`; MONTH Tage 1/8/15/22/29; YEAR Monatskürzel; RANGE 5 gleichverteilte Daten `d.M.`). Locale in Composables aus `LocalConfiguration.current.locales[0]`.
  - `data class HomeUiState(val period: GraphPeriod, val graph: GraphData, val rows: List<HomeEntryRow>, val colors: Map<Long, Int>, val legend: List<Pair<String, Int>>)`
  - `@HiltViewModel class HomeViewModel @Inject constructor(repo: DiaryRepository, labeler: FeelingLabeler, savedState: SavedStateHandle, clock: Clock)`: `val state: StateFlow<HomeUiState>`, `fun selectKind(kind: PeriodKind)` (Zeitraum gleicher Art, der den bisherigen Start enthält), `fun selectRange(start: LocalDate, end: LocalDate)`, `fun shift(steps: Int)`, `fun openBucket(point: GraphPoint)` (DAILY → DAY dieses Tages, WEEKLY → WEEK), `fun delete(id: Long)`. Zeitraum im `SavedStateHandle` (Keys `"kind"`, `"start"`, `"end"` als ISO-Strings); Start nach E8 mit `LocalDate.now(clock)`; `firstDayOfWeek` aus `Locale.getDefault()` (das ViewModel überlebt einen Sprachwechsel; der neue Wochenbeginn gilt ab dem nächsten Wechsel der Periode bzw. App-Start – Texte folgen dagegen sofort, weil die UI formatiert).
  - `HomeScreen(state: HomeUiState, actions: HomeActions)` mit `data class HomeActions(val onAdd: () -> Unit, val onKind: (PeriodKind) -> Unit, val onRange: (LocalDate, LocalDate) -> Unit, val onShift: (Int) -> Unit, val onPoint: (GraphPoint) -> Unit, val onRow: (Long) -> Unit, val onEdit: (Long) -> Unit, val onDelete: (Long) -> Unit)`; `HomeRoute(onAdd: () -> Unit, onEdit: (Long) -> Unit, vm: HomeViewModel = hiltViewModel())`.
  - Aufbau (Mockup `graph-b-themes.html`): Titel „Mein Verlauf“; `PeriodBar` (FilterChips Jahr/Monat/Woche/Tag, Kalender-`IconButton` → `DateRangePicker`-Dialog, darunter ‹ Zeitraumtext ›); `HistoryGraph`; `GraphLegend`; Eintragsliste (E1) bzw. Hinweis `history_empty` bei leerem Zeitraum; Knopf „+ Wie fühlst du dich?“ unten (bestehend). `onPoint` bei `entryId != null` und `onRow` öffnen `EntryDetailSheet` (ModalBottomSheet: Zeit, alle Gefühle mit Farbpunkt, Personen, Knöpfe Bearbeiten/Löschen); Löschen → Bestätigungsdialog (E5). Die Sheet-id liegt in `rememberSaveable`; verschwindet der Eintrag aus `rows`, schließt das Sheet.
  - Neue Strings (EN/DE): `history_title`, `period_year`, `period_month`, `period_week`, `period_day`, `period_range_cd`, `period_prev_cd`, `period_next_cd`, `history_empty`, `detail_edit`, `detail_delete`, `delete_title`, `delete_message`, `delete_confirm`.

- [ ] **Step 1: Failing Tests `TimeFormatTest`** – `formatEntryTime(2026-11-07T19:22, Locale.GERMANY) == "07.11.26, 19:22"` und mit `Locale.US` ≠ deutscher Text (enthält „PM“); `formatPeriod(WEEK 2.–8.11.2026, GERMANY)` enthält „02.11.2026“ und „08.11.2026“; `xAxisLabels(WEEK ab Mo, GERMANY).first().text == DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, Locale.GERMANY)`.
- [ ] **Step 2: Failing Tests `HomeViewModelTest`** (MockK-Repository, `observeEntriesBetween` liefert `MutableStateFlow`, fester Clock 2026-11-07 Berlin, Default-Locale GERMANY): `startsWithCurrentWeek` (Periode 2.–8.11.); `shiftLoadsNeighbour` (`shift(-1)` → `coVerify`/`verify { repo.observeEntriesBetween(2026-10-26, 2026-11-01) }`); `selectKindKeepsAnchor` (Woche → `selectKind(MONTH)` → 2026-11-01…30); `openBucketOfDailyPointShowsDay`; `periodSurvivesProcessDeath` (`SavedStateHandle(mapOf("kind" to "MONTH", "start" to "2026-03-01", "end" to "2026-03-31"))`); `deleteDelegates` (`coVerify { repo.deleteEntry(7) }`).
- [ ] **Step 3: Failing Tests `HomeScreenTest`** (bestehende Tests an `HomeActions` anpassen; neu): `chipsAndArrowsCallActions` (Klick „Month“ → `onKind(MONTH)`, Klick ‹ → `onShift(-1)`); `emptyPeriodShowsHint`; `rowOpensDetailsAndDeleteAsksFirst` (Klick Zeile → Sheet mit „Edit“/„Delete“; „Delete“ → Dialog; „Cancel“ → kein `onDelete`; erneut + Bestätigen → `onDelete(id)`); `sheetClosesWhenEntryDisappears` (State ohne die Zeile neu setzen → „Edit“ existiert nicht mehr, Review Focus 5); `timeFollowsLocaleChange` (`CompositionLocalProvider(LocalConfiguration provides Configuration().apply { setLocale(Locale.US) })` → US-Format, danach GERMANY → deutsches Format, ohne neuen State; Review Focus 5 / R8a).
- [ ] **Step 4: Laufen lassen** → FAIL.
- [ ] **Step 5: Implementieren** (Navigation-Anpassung `HomeRoute(onAdd, onEdit)` in `AppNavHost` vorerst mit `onEdit = {}`; Task 7 verdrahtet es).
- [ ] **Step 6: Vollbuild** – `./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL.
- [ ] **Step 7: Commit** – `git commit -m "feat(home): history screen with period filters, graph, entry list and delete"`.

---

### Task 7: Eintrag bearbeiten (Navigation + EntryViewModel)

**Files:**
- Modify: `app/src/main/java/com/wolf/feelingsdiary/ui/entry/EntryViewModel.kt`, `ui/entry/EntryUiState.kt`, `ui/entry/EntryScreen.kt`, `ui/navigation/AppNavHost.kt`
- Modify: `values/strings.xml`, `values-de/strings.xml` (`entry_title_edit` „Edit entry · %1$s“ / „Eintrag bearbeiten · %1$s“)
- Test: `app/src/test/java/com/wolf/feelingsdiary/ui/entry/EntryViewModelTest.kt`, `ui/navigation/AppNavGraphTest.kt`, `ui/entry/EntryScreenTest.kt`

**Interfaces:**
- Consumes: `DiaryRepository.getEntry/updateEntry` (Task 3), `HomeRoute(onAdd, onEdit)` (Task 6).
- Produces:
  - Route `"entry?entryId={entryId}"` (`NavType.LongType`, Default `-1L`); `ROUTE_ENTRY` bleibt für Neu-Anlage; Helfer `fun entryRoute(id: Long? = null): String`.
  - `AppNavGraph(nav, home: @Composable (onAdd: () -> Unit, onEdit: (Long) -> Unit) -> Unit, entry: @Composable (onDone: () -> Unit) -> Unit)` – beide Callbacks mit `dropUnlessResumed` wie bisher.
  - `EntryUiState` + `val editingTime: LocalDateTime?` (null = neuer Eintrag).
  - `EntryViewModel`: liest `savedState.get<Long>("entryId")`; bei `> 0` und noch ohne `KEY_SELECTED` im SavedState einmalig `repo.getEntry(id)` → `selected`, `people`, `editingTime` setzen; `save()` ruft dann `repo.updateEntry(id, people, selectedIds)` statt `addEntry`. Existiert der Eintrag nicht (mehr) → `saved = true` (Bildschirm schließt).
  - `EntryScreen`-Kopf: bei `editingTime != null` `entry_title_edit` mit `formatEntryTime(editingTime, locale)`; `EntryRoute` reicht den Wert durch.

- [ ] **Step 1: Failing Tests `EntryViewModelTest`**: `editLoadsEntryInOrder` (`SavedStateHandle(mapOf("entryId" to 5L))`, `getEntry(5)` liefert [b, a], people 2 → `selectedIds == listOf(b, a)`, `people == 2`, `editingTime != null`); `editSaveUpdatesInsteadOfAdding` (`coVerify { repo.updateEntry(5, 2, listOf(b, a)) }`, `coVerify(exactly = 0) { repo.addEntry(any(), any()) }`); `editDoesNotOverwriteRestoredSelection` (SavedState hat `entryId` **und** `"selected"` → `getEntry` wird nicht aufgerufen); `editOfMissingEntryCloses` (`getEntry` → null → `saved == true`).
- [ ] **Step 2: Failing Tests `AppNavGraphTest`**: `editNavigatesWithId` – `home`-Lambda ruft `onEdit(5)` → aktuelle Route `entry?entryId={entryId}` mit Argument 5; `addStillOpensNewEntry` (bestehender Test angepasst).
- [ ] **Step 3: Failing Test `EntryScreenTest`**: `editTitleShown` – State mit `editingTime = 2026-11-07T19:22` → Text „Edit entry · …“ sichtbar.
- [ ] **Step 4: Laufen lassen** → FAIL.
- [ ] **Step 5: Implementieren.**
- [ ] **Step 6: Vollbuild** – `./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL.
- [ ] **Step 7: Commit** – `git commit -m "feat(entry): edit existing entries from history details"`.

---

### Task 8: Gerätetest, Doku, PR

**Files:**
- Modify: `README.md` (Stand Phase 2), `CLAUDE.md` (Pakete: `ui/home` = Verlauf; `domain` um `GraphPeriod`, `GraphBuilder`)

- [ ] **Step 1: Gerätetest** (Galaxy A54, `adb -s RZCW81045KL`, `./gradlew installDebug`; Screenshots aus Git Bash mit `adb exec-out screencap -p > f.png`): drei Einträge anlegen (verschiedene Hauptgefühle, Personen 0/2/7) → Woche zeigt Linie mit Farbverlauf, Personen-Fläche, Legende; Chips Monat/Jahr/Tag und ‹ › durchklicken; Tagesbereich wählen; Punkt antippen → Details → Bearbeiten (Hauptgefühl tauschen) → Linienfarbe ändert sich, Zeit gleich; Löschen mit Bestätigung; helles Theme ist erst in Phase 3 schaltbar, daher nur dunkel prüfen; Sprache EN/DE umschalten → Datums- und Achsentexte wechseln ohne App-Neustart; Entry-Screen bei Schriftgröße max. → Personen-Knöpfe zweizeilig, ≥ 48 dp.
- [ ] **Step 2: Doku committen** – `git commit -m "docs: readme and claude guide for phase 2"`.
- [ ] **Step 3: Push + PR** – `git remote get-url origin` und `git branch --show-current` prüfen; Branch `feat/phase2-history-graph`, `gh pr create --base main` (Body: Zusammenfassung, Planer-Entscheidungen E1–E8, Gerätetest-Ergebnis mit Screenshots, endet mit `🤖 Generated with [Claude Code](https://claude.com/claude-code)`); `gh pr checks --watch` → `build` grün. Nicht mergen.
