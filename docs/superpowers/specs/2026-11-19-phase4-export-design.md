# Phase 4 – Export (Teilen) – Design

Stand: 2026-11-19 · Autor: Wolfgang Kozian (mit Claude) · Status: zur Freigabe

## Absicht und Warum

Wolfgang will seinem **Therapeuten** zeigen, wie es ihm in einem Zeitraum ging, und schickt das **per Mail**. Der Export ist also ein Dokument für eine fremde Person ohne die App: Er muss ohne Erklärung lesbar sein, in Excel sofort aufgehen und als Bild für sich allein verständlich sein.

## Ziele

- Teilen-Knopf im Verlauf; der Export umfasst den **gerade eingestellten Zeitraum** (Jahr/Monat/Woche/Tag/Tagesbereich).
- Beim Export frei wählbar: **CSV**, **Graph (PNG)** oder beides (mindestens eins).
- Übergabe an das Android-Teilen-Menü (z. B. Gmail mit beiden Anhängen).

## Nicht-Ziele

- Kein Speichern über den Dateidialog (Teilen-Menü bietet das bei Bedarf selbst an), kein PDF, kein Re-Import.
- Kein eigener Zeitraum-Dialog (der Verlauf kann Tagesbereiche schon frei wählen).

## Anti-Ziele

- Daten verlassen das Gerät ohne ausdrückliche Aktion (nur nach Tipp auf „Teilen“ und Auswahl einer App).
- Exportdateien bleiben dauerhaft im Speicher liegen oder geraten in die Android-Sicherung.
- Neue Berechtigungen (insb. INTERNET, Speicher).

## Ansätze

- **A – Teilen-Knopf → Auswahldialog → Android-Teilen-Menü (gewählt).** Passt direkt zu „per Mail an den Therapeuten“, kleinster Eingriff; braucht einen FileProvider.
- B – Nur Speichern über Dateidialog (SAF): kein FileProvider, aber Anhängen an die Mail händisch und bei zwei Dateien zwei Dialoge.
- C – A plus Speichern-Knopf: mehr Aufwand ohne Nutzen für das Ziel.

Wolfgang hat A gewählt.

## Entschieden

### Bedienung
- Im Verlauf neben dem Einstellungen-Zahnrad ein Teilen-Symbol (`Icons.Filled.Share`), deaktiviert, wenn der Zeitraum keine Einträge hat.
- Dialog „Exportieren“: Zeitraum als Text, zwei Checkboxen „Tabelle (CSV)“ und „Graph (Bild)“ (beide standardmäßig an), Knopf „Teilen“ (deaktiviert, wenn keine gewählt), „Abbrechen“.
- „Teilen“ erzeugt die Dateien und startet `Intent.createChooser` mit `ACTION_SEND` (eine Datei) bzw. `ACTION_SEND_MULTIPLE` (zwei), `FLAG_GRANT_READ_URI_PERMISSION`, `EXTRA_SUBJECT` = „Gefühls-Tagebuch 01.11.–30.11.2026“ (lokalisiert).

### CSV (Claude-Entscheidung, auf Excel beim Therapeuten ausgerichtet)
- UTF-8 **mit BOM**, Zeilenende CRLF, Felder nach RFC 4180 gequotet (Anführungszeichen verdoppeln).
- Trennzeichen sprachabhängig: Deutsch `;` und Dezimalkomma, Englisch `,` und Dezimalpunkt (so öffnet Excel in der jeweiligen Region ohne Import-Assistent).
- Eine Zeile pro Eintrag, chronologisch nach `epochMinute`. Spalten (Überschriften in UI-Sprache):
  1. Datum (`yyyy-MM-dd`, Ortszeit des Eintrags)
  2. Uhrzeit (`HH:mm`, Ortszeit des Eintrags)
  3. Zeitzone (`UTC+1`, `UTC+5:30`)
  4. Hauptgefühl
  5. Gefühle (alle, Auswahlreihenfolge, jeweils mit gespeicherter Valenz-Momentaufnahme, z. B. „traurig (-1), müde (-1)“; so kann Excel die Valenzen nicht als Zahl oder Formel missdeuten)
  6. Stimmung (gewichtete Valenz-Summe wie im Graphen, `GraphBuilder.weightedValence`, 2 Nachkommastellen) – ersetzt den „Valenz-Durchschnitt“ aus Spec 5.8, weil die Punkthöhe seit PR #12 die gewichtete Summe ist
  7. Personen (andere Menschen, 0 = allein)
- Gefühlsnamen über den bestehenden `FeelingLabeler` (UI-Sprache; eigene Gefühle wie eingegeben), auch ausgeblendete Gefühle.
- Formel-Injektion: Zellen, die mit `=`, `+`, `-`, `@` beginnen, bekommen ein vorangestelltes `'` – betrifft nur Textspalten (eigene Gefühlsnamen); Zahlenspalten bleiben Zahlen.

### Graph-PNG (Claude-Entscheidung)
- Immer **helles Theme** (weißer Hintergrund, druck- und mailtauglich), unabhängig vom App-Theme.
- Feste Größe 1600 × 900 px, Inhalt:
  - Überschrift: App-Name und Zeitraum (wie in der PeriodBar formatiert).
  - Der Graph exakt wie in der App (gleiche Mittelung, gleiche Zeichenroutinen), mit y-Beschriftung gut/neutral/schlecht, Personen-Skala und x-Beschriftung; zusätzlich „👥 Personen“-Achsentitel.
  - Darunter die Legende (Farbpunkt + Name) der im Zeitraum vorkommenden Gefühle.
- Umsetzung: Zeichencode aus `HistoryGraph` in eine `DrawScope`-Funktion herauslösen, die Composable und Export gemeinsam nutzen; der Export rendert über `CanvasDrawScope` in ein `ImageBitmap` und speichert per `Bitmap.compress(PNG)`. Kein zweiter Graph-Nachbau.

### Dateien und Datenschutz
- Ablage in `cacheDir/export/`, Freigabe über `androidx.core.content.FileProvider` (`exported=false`, `grantUriPermissions=true`, `file_paths.xml` nur `cache-path export/`).
- Dateinamen: `gefuehle_2026-11-01_2026-11-30.csv` / `.png` (Englisch `feelings_…`).
- Vor jedem Export und beim App-Start wird `cacheDir/export/` geleert. Cache liegt nicht in den Backup-Regeln (die nur DB + Settings enthalten).
- `ManifestPolicyTest` erweitern: genau ein Provider, nicht exportiert, Pfade nur `cache-path export/`; Berechtigungen unverändert.

### Strings
- Alle neuen Texte in `values/strings.xml` und `values-de/strings.xml` (CSV-Spaltenüberschriften, Dialog, Betreff, Dateinamen-Präfix, Chooser-Titel, Content-Descriptions).

## Dem Builder überlassen

- Schnitt der Klassen (z. B. `domain/CsvExporter` rein und JVM-testbar, `export/ExportFileWriter`, `export/GraphImageRenderer`, ViewModel-Anbindung).
- Wie Zeitraum-Text und x-Beschriftungen aus `PeriodBar`/`TimeFormat` wiederverwendet werden.
- Fehlerbehandlung (z. B. Snackbar bei Schreibfehler, keine App zum Teilen vorhanden).
- Tests: CSV-Format (Quoting, BOM, Trennzeichen, Injektion, Zeitzonen), Manifest-Policy, Dialog-Logik per Compose-UI-Test, PNG-Erzeugung per Robolectric (Größe, nicht leer).
