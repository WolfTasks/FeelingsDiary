# Phase 4 – Export (Teilen) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Teilen-Knopf im Verlauf exportiert den eingestellten Zeitraum wahlweise als CSV und/oder Graph-PNG ins Android-Teilen-Menü.

**Architecture:** Reine CSV-Erzeugung in `domain/CsvExporter`; Graph-Zeichencode aus `HistoryGraph` in eine gemeinsame `DrawScope`-Funktion herausgelöst, die der Export über `CanvasDrawScope` in ein Bitmap rendert; `export/DiaryExporter` schreibt die Dateien nach `cacheDir/export/` und liefert FileProvider-URIs; `HomeViewModel` stößt den Export an, `HomeRoute` startet den Chooser.

**Tech Stack:** Kotlin, Compose (Canvas/DrawScope, TextMeasurer), androidx.core FileProvider, Hilt, JUnit4/Robolectric/MockK.

**Spec:** `docs/superpowers/specs/2026-11-19-phase4-export-design.md`

## Global Constraints

- Berechtigungen genau `POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED`; keine neuen.
- Backup-Regeln unverändert (nur Datenbank und `datastore/settings.preferences_pb`).
- Jeder UI-Text (inkl. CSV-Überschriften, Betreff, Dateinamen-Präfix) in `values/strings.xml` und `values-de/strings.xml`.
- Stimmung = `GraphBuilder.weightedValence` (nicht Durchschnitt).
- `ktlintCheck testDebugUnitTest` grün vor jedem Commit; `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot"`.

## Review Focus

- Eigenes Gefühl mit `;`, `"`, Zeilenumbruch oder führendem `=` im Namen → CSV bleibt einspaltig korrekt, Excel führt nichts aus (Test in Task 1).
- Eintrag mit Offset UTC+5:30 bzw. negativ → Datum/Uhrzeit in Ortszeit des Eintrags, Zeitzone „UTC+5:30“ (Task 1).
- Zeitraum ohne Einträge → Teilen-Knopf deaktiviert (Task 5).
- Zwei Exporte hintereinander → alte Dateien gelöscht, nur die neuen liegen in `cache/export` (Task 4).
- Graph-Bild bei dunklem App-Theme → trotzdem heller Hintergrund (Task 3, Renderer nimmt fest `LightColors`).

---

### Task 1: CSV-Erzeugung

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/domain/CsvExporter.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/domain/CsvExporterTest.kt`

**Interfaces:**
- Produces:
  - `data class CsvFormat(val delimiter: Char, val decimalSeparator: Char)` mit `companion fun forLocale(locale: Locale): CsvFormat` (Dezimalzeichen aus `DecimalFormatSymbols`; `,` → Trenner `;`, sonst `,`).
  - `object CsvExporter { fun build(entries: List<Entry>, feelingLabel: (Long) -> String?, headers: List<String>, format: CsvFormat): String }` – 7 Überschriften (Datum, Uhrzeit, Zeitzone, Hauptgefühl, Gefühle, Stimmung, Personen), Zeilen sortiert nach `epochMinute`, CRLF, ohne BOM (BOM schreibt Task 4). Unbekannte Gefühls-IDs (`null`) werden übersprungen.
  - `const val UTF8_BOM = "\uFEFF"` in derselben Datei.

Zellformat: Datum `yyyy-MM-dd`, Uhrzeit `HH:mm` aus `EntryTime.localDateTime()`, Zeitzone über eine aus `ui/home/TimeFormat.formatUtcOffset` nach `domain` verschobene Funktion (`domain/UtcOffset.kt`, `fun formatUtcOffset(minutes: Int): String`; `TimeFormat.kt` importiert sie). Gefühle `"Name (+2), Name (-1), Name (0)"`. Stimmung `%.2f` mit `Locale.ROOT`, Punkt durch `decimalSeparator` ersetzt. Textzellen (Hauptgefühl, Gefühle), die mit `=`, `+`, `-`, `@` beginnen, bekommen `'` vorangestellt; jede Zelle mit Trenner, `"`, `\r`, `\n` wird in `"` gesetzt, `"` verdoppelt.

- [ ] **Step 1: Failing tests** in `CsvExporterTest`:
  - `germanFormatUsesSemicolonAndComma`: `CsvFormat.forLocale(Locale.GERMANY) == CsvFormat(';', ',')`, `forLocale(Locale.US) == CsvFormat(',', '.')`.
  - `rowHasLocalTimeOffsetFeelingsMoodPeople`: Eintrag `EntryTime(epochMinute = 29_000_000, utcOffsetMinutes = 330)`, Gefühle [1,2] Valenzen [-1,2], Personen 3, Labels {1:"traurig",2:"froh"}, DE-Format → Zeile 2 = `<Datum>;<Uhrzeit>;UTC+5:30;traurig;traurig (-1), froh (+2);0,00;3` (Datum/Uhrzeit aus `LocalDateTime.ofEpochSecond(29_000_000*60L,0,ZoneOffset.ofTotalSeconds(330*60))`; Stimmung −1 + 2·½ = 0).
  - `headerFirstAndCrlf`: Ergebnis beginnt mit den 7 Überschriften durch `;` und `\r\n`, endet mit `\r\n`.
  - `sortsByEpochMinute`: zwei Einträge in falscher Reihenfolge → spätere Zeile hinten.
  - `quotesDelimiterQuoteAndNewline`: Label `a;"b"\nc` → Zelle `"a;""b""\nc"`.
  - `escapesFormulaStart`: Label `=SUM(A1)` → Hauptgefühl-Zelle `'=SUM(A1)`; Stimmung `-1,50` bleibt ohne `'`.
  - `skipsUnknownFeeling`: Label-Funktion liefert `null` für id 9 → id 9 fehlt in Gefühle-Zelle.
  - `negativeOffsetAndEnglish`: Offset −570, US-Format → `UTC-9:30`, Trenner `,`, Stimmung mit Punkt; Gefühle-Zelle (enthält `,`) ist gequotet.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests '*CsvExporterTest*'` → FAIL (unresolved).
- [ ] **Step 3:** Implementieren; `formatUtcOffset` nach `domain/UtcOffset.kt` verschieben (TimeFormatTest bleibt grün).
- [ ] **Step 4:** Tests + `TimeFormatTest` → PASS.
- [ ] **Step 5:** ktlintCheck, Commit `feat: build CSV export of diary entries`.

### Task 2: Graph-Zeichnen herauslösen

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/home/GraphDrawing.kt`
- Modify: `app/src/main/java/com/wolf/feelingsdiary/ui/home/HistoryGraph.kt`
- Test: bestehender `HistoryGraphTest` (unverändert grün)

**Interfaces:**
- Produces:
  - `data class GraphColors(val neutral: Color, val people: Color, val fallback: Color)`
  - `fun DrawScope.drawHistoryGraph(data: GraphData, plot: Rect, colorOf: (Long) -> Color, colors: GraphColors, measurer: TextMeasurer, labelStyle: TextStyle, valenceTexts: List<String>, xLabels: List<AxisLabel>)` – enthält Neutrallinie, Personenfläche, Liniensegmente, Punkte, Achsenbeschriftung (bisheriger Canvas-Inhalt; `Color.Unspecified` → `colors.fallback`). Die privaten Helfer (`drawNeutralLine`, `drawPeopleArea`, `drawLineSegments`, `drawAxisLabels`, `dashEffect`) und Größen-Konstanten wandern mit.
  - `const val GRAPH_LABEL_FONT_SP = 10f`; `internal val LABEL_GAP` bleibt nutzbar für `rememberGraphMargins`.

- [ ] **Step 1:** Verschieben; `HistoryGraph` ruft `drawHistoryGraph(..., GraphColors(onSurfaceVariant, primary, onSurfaceVariant), ...)`.
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests '*HistoryGraphTest*' --tests '*GraphGeometryTest*'` → PASS.
- [ ] **Step 3:** ktlintCheck, Commit `refactor: share graph drawing with export`.

### Task 3: Graph als PNG

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/export/GraphImageRenderer.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/export/GraphImageRendererTest.kt`

**Interfaces:**
- Consumes: `drawHistoryGraph`, `GraphColors` (Task 2), `graphMargins`, `plotRect` (`GraphGeometry`), `LightColors`.
- Produces:
  - `data class GraphImage(val title: String, val data: GraphData, val colorOf: (Long) -> Color, val xLabels: List<AxisLabel>, val valenceTexts: List<String>, val legend: List<Pair<String, Color>>, val peopleLabel: String)`
  - `object GraphImageRenderer { const val WIDTH = 1600; const val HEIGHT = 900; const val DENSITY = 2.5f; fun render(context: Context, image: GraphImage): android.graphics.Bitmap }`

Aufbau (px bei Dichte 2.5): weißer Hintergrund (`LightColors.surface`); Titel 20 sp `LightColors.onSurface` oben links mit 16 dp Rand; Graph-Bereich darunter bis Legende, Plot über `plotRect` mit `graphMargins` aus gemessenen Labels; „👥 Personen“-Label (peopleLabel) rechts über dem Plot; Legende unten in Zeilen umbrechend (Punkt 10 dp, 6 dp Abstand, 12 dp zwischen Einträgen), letzter Eintrag `peopleLabel` mit `LightColors.primary` α 0.4 wie `GraphLegend`. Text über `TextMeasurer(createFontFamilyResolver(context), Density(DENSITY), LayoutDirection.Ltr)`, Zeichnen über `CanvasDrawScope().draw(density, Ltr, Canvas(imageBitmap), size) { ... }`, Ergebnis `imageBitmap.asAndroidBitmap()`.

- [ ] **Step 1: Failing test** (`@RunWith(RobolectricTestRunner::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`): `rendersLightImageWithLine` – zwei Punkte rot (x 0.2 / 0.8, valence 2) → Bitmap 1600×900, Pixel (5,5) == `LightColors.surface.toArgb()`, und mindestens ein Pixel ist rot (`Color.Red.toArgb()`) bzw. ein Pixel mit Rot-Anteil > 200 und Grün/Blau < 80.
- [ ] **Step 2:** FAIL. **Step 3:** Implementieren. **Step 4:** PASS.
- [ ] **Step 5:** ktlintCheck, Commit `feat: render history graph as PNG`.

### Task 4: Exportdateien und FileProvider

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/export/DiaryExporter.kt`, `app/src/main/res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml`, `FeelingsDiaryApp.kt`, `values/strings.xml`, `values-de/strings.xml`, `ManifestPolicyTest.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/export/DiaryExporterTest.kt`

**Interfaces:**
- Consumes: `CsvExporter`, `CsvFormat`, `UTF8_BOM` (Task 1), `GraphImageRenderer`, `GraphImage` (Task 3), `GraphBuilder.build`, `xAxisLabels`, `formatPeriod`, `FeelingSuggester.baseListOrder`, `FeelingLabeler`, `LocaleSource`, `DiaryRepository.observeEntriesBetween(...).first()`, `observeFeelings().first()`.
- Produces:
  - `data class ExportResult(val uris: List<Uri>, val subject: String, val mimeType: String)` (`text/csv`, `image/png`, beide → `*/*`)
  - `@Singleton class DiaryExporter @Inject constructor(@ApplicationContext context, repo, labeler, localeSource)` mit `suspend fun export(period: GraphPeriod, firstDayOfWeek: DayOfWeek, csv: Boolean, graph: Boolean): ExportResult` (läuft auf `Dispatchers.IO`, `require(csv || graph)`) und `fun clearCache()`.
  - Authority `"${context.packageName}.export"`; Dateien `cacheDir/export/<prefix>_<start>_<end>.csv|png` mit `prefix` aus `R.string.export_file_prefix` (`feelings` / `gefuehle`).
  - Strings: `export_file_prefix`, `export_subject` (`Feelings diary %1$s` / `Gefühls-Tagebuch %1$s`, Argument = `formatPeriod`), `export_csv_date`, `export_csv_time`, `export_csv_zone`, `export_csv_main`, `export_csv_feelings`, `export_csv_mood`, `export_csv_people`. Strings aus lokalisiertem Kontext (`createConfigurationContext` mit `localeSource.locale.value`).
- Manifest: `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.export" android:exported="false" android:grantUriPermissions="true">` mit `meta-data android.support.FILE_PROVIDER_PATHS` → `@xml/file_paths` = `<paths><cache-path name="export" path="export/" /></paths>`.
- `FeelingsDiaryApp.onCreate`: `exporter.clearCache()` (injiziert, im `scope` launch).

- [ ] **Step 1: Failing tests** (Robolectric, `@GraphicsMode(NATIVE)`, MockK-Repo):
  - `csvOnlyWritesBomCsv`: ein Eintrag → 1 URI, Datei `cache/export/feelings_2026-11-01_2026-11-30.csv` beginnt mit `UTF8_BOM`, mimeType `text/csv`, subject enthält „Feelings diary“.
  - `bothWritesTwoFiles`: 2 URIs, mimeType `*/*`, PNG-Datei dekodierbar 1600×900.
  - `secondExportRemovesOldFiles`: Export Monat Nov, dann Monat Dez → in `cache/export` nur die Dez-Dateien.
  - `clearCacheEmptiesExportDir`.
  - `ManifestPolicyTest.exportProviderIsPrivateAndCacheOnly`: Manifest enthält genau einen `<provider`, `exported="false"`, `grantUriPermissions="true"`; `file_paths.xml` enthält genau ein Pfad-Element `<cache-path name="export" path="export/" />`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementieren. **Step 4:** PASS inkl. `ManifestPolicyTest`, `StringsParityTest`.
- [ ] **Step 5:** ktlintCheck, Commit `feat: write export files and share them via FileProvider`.

### Task 5: Teilen-Knopf, Dialog, Chooser

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/home/ExportDialog.kt`
- Modify: `HomeScreen.kt`, `HomeViewModel.kt`, `values/strings.xml`, `values-de/strings.xml`
- Test: `HomeScreenTest.kt`, `HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `DiaryExporter.export`, `ExportResult` (Task 4).
- Produces:
  - `HomeActions.onExport: (csv: Boolean, graph: Boolean) -> Unit` (neues Feld, Standardwert `{ _, _ -> }`).
  - `HomeViewModel(..., exporter: DiaryExporter)`; `fun export(csv: Boolean, graph: Boolean)`; `val exportEvents: Flow<ExportEvent>` (`Channel(BUFFERED).receiveAsFlow()`), `sealed interface ExportEvent { data class Share(val result: ExportResult) : ExportEvent; data object Failed : ExportEvent }` – Exceptions aus `export` → `Failed`.
  - `@Composable fun ExportDialog(periodText: String, onDismiss: () -> Unit, onShare: (csv: Boolean, graph: Boolean) -> Unit)`: Titel, Zeitraum, Checkboxen (beide an), „Teilen“ disabled ohne Auswahl, „Abbrechen“.
  - Strings: `export_open_cd` (Share / Teilen), `export_title` (Export / Exportieren), `export_option_csv` (Table (CSV) / Tabelle (CSV)), `export_option_graph` (Graph (image) / Graph (Bild)), `export_share` (Share / Teilen), `export_cancel` (Cancel / Abbrechen), `export_chooser` (Share export / Export teilen), `export_failed` (Export failed / Export fehlgeschlagen).
- `HomeScreen`: `IconButton(Icons.Filled.Share)` vor dem Zahnrad, `enabled = state.rows.isNotEmpty()`; öffnet Dialog (`rememberSaveable`).
- `HomeRoute`: sammelt `exportEvents` in `LaunchedEffect`; `Share` → `Intent(ACTION_SEND | ACTION_SEND_MULTIPLE)` mit `EXTRA_STREAM`, `EXTRA_SUBJECT`, `type = mimeType`, `ClipData` aller URIs, `FLAG_GRANT_READ_URI_PERMISSION`, `Intent.createChooser(..., export_chooser)`; `ActivityNotFoundException` und `Failed` → `Toast` `export_failed`.

- [ ] **Step 1: Failing tests:**
  - `HomeScreenTest.shareDisabledWithoutEntries`: leere Rows → Knoten `export_open_cd` `assertIsNotEnabled()`.
  - `HomeScreenTest.exportDialogPassesChoice`: mit Rows → Teilen-Symbol klicken, „Graph (image)“-Checkbox abwählen, „Share“ klicken → `onExport(true, false)` aufgerufen; beide abwählen → „Share“ `assertIsNotEnabled()`.
  - `HomeViewModelTest.exportEmitsShare`: `coEvery { exporter.export(any(), any(), true, true) } returns result` → `exportEvents.first() == ExportEvent.Share(result)`, aufgerufen mit `vm.state.value.period`.
  - `HomeViewModelTest.exportFailureEmitsFailed`: `throws IOException()` → `ExportEvent.Failed`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementieren (bestehende `vm(...)`-Helfer um `exporter` ergänzen). **Step 4:** Alle Tests PASS.
- [ ] **Step 5:** `./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug` grün; Spec 10 „Phase 4 (Export): ✅“ und 5.8 Hinweis auf Phase-4-Design; CLAUDE.md Paketliste um `export` ergänzen. Commit `feat: share export from history`.
