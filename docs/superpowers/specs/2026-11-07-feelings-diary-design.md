# Gefühls-Tagebuch (Arbeitstitel) – Design

Stand: 2026-11-07 · Autor: Wolfgang Kozian (mit Claude) · Status: Entwurf zur Freigabe (nach Builder-Check überarbeitet)

## 1. Absicht und Warum

Eine Android-App, die mehrmals täglich daran erinnert festzuhalten, **wie man sich fühlt** und **wie viele Menschen gerade um einen herum sind**. Der Verlauf zeigt beides parallel, damit man über Tage, Monate und Jahre erkennt, wie die Gefühle waren und ob sie mit der Zahl der Menschen in der Umgebung zusammenhängen.

Die Benachrichtigung ist eine **Erinnerung zur Eingabe**, kein Selbstzweck. Die App ist ein persönliches Werkzeug: Die Daten bleiben auf dem Handy.

## 2. Ziele

- 1–4 Erinnerungen pro Tag, Eingabe in wenigen Sekunden.
- Gefühle aus einer festen Grundliste wählen, **Mehrfachauswahl**, eigene Gefühle ergänzbar.
- Personenzahl pro Eintrag.
- Übersichtlicher Verlaufsgraph (Variante „B“, siehe 5.3) mit Filtern Jahr / Monat / Woche bzw. Tagesbereich / Tag.
- Eingabehilfen: zuletzt genutzte Gefühle oben, Vorschläge beim Tippen, neues Gefühl direkt aus dem Suchtext anlegen.
- Platzsparende Speicherung.
- UI auf Deutsch und Englisch.
- Dunkles Theme als Standard (Palette wie MusicPlayer), helles Theme wählbar.
- Optionaler Export (CSV + Graphen).

## 3. Nicht-Ziele (Version 1)

- Kein Konto, keine Cloud, kein Sync, keine Server-Komponente, **keine INTERNET-Berechtigung**.
- Kein Re-Import der CSV (später möglich, siehe 9).
- Keine Freitext-Notizen pro Eintrag, keine Fotos, kein Ort/GPS.
- Keine Widgets, keine Wear-OS-App.
- Keine Statistik-Auswertungen über den Graphen hinaus (keine Korrelationskoeffizienten, keine KI).

## 4. Anti-Ziele (wäre ein Fehlschlag, selbst wenn es technisch läuft)

- Gefühlsdaten verlassen das Gerät ohne ausdrückliche Aktion des Nutzers (Export/Teilen bzw. eingeschaltete Android-Sicherung).
- Die Eingabe ist umständlich und dauert so lange, dass man sie wegklickt.
- Erinnerungen kommen nachts oder so vorhersehbar, dass sie immer die gleiche Situation abfragen.
- Der Graph wird bei vielen Einträgen (Jahresansicht) unlesbar oder träge.

## 5. Was entschieden ist

### 5.1 Erinnerungen
- Anzahl pro Tag: 1–4 (Einstellung, Standard 3).
- **Standard: zufälliger Zeitpunkt innerhalb von Zeitfenstern.** Vorschlag Standardfenster (Claude-Vorschlag, einstellbar): Vormittag 09–11, Mittag 12–14, Nachmittag 15–17, Abend 19–21. Bei weniger als 4 Erinnerungen werden entsprechend weniger Fenster genutzt; Fenster sind editierbar.
- **Optional (Einstellung): feste Uhrzeiten** statt Zufall.
- Nachtruhe-Bereich, in dem nie erinnert wird (Claude-Vorschlag, Standard 22–08).
- Tipp auf die Benachrichtigung öffnet direkt die Eingabe.
- Einträge sind jederzeit auch ohne Erinnerung möglich (Knopf „+ Wie fühlst du dich?“ im Verlauf).

- Feste Uhrzeiten dürfen ein paar Minuten ungenau sein: **keine** Sonderberechtigung für exakte Alarme (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`), damit die App Play-Store-tauglich bleibt.

### 5.2 Eingabe
- **Standard: eigener Eingabe-Bildschirm (Variante B)**: Suchfeld oben mit Vorschlägen beim Tippen; darunter „Zuletzt genutzt“, dann „Alle“ Gefühle als Farb-Chips; Personen per Schnellknöpfe `allein, 1, 2, 3, 4, 5, 6+` (6+ öffnet ein Zahlenfeld); Speichern.
- **Bedeutung der Personenzahl:** gezählt werden die *anderen* Menschen um einen herum, man selbst zählt nicht. „allein“ = 0, „1“ = eine weitere Person. Keine Obergrenze (6+ → Zahlenfeld). Variante A (−/+) speichert dieselben Werte.
- **Optional (Einstellung): Schnell-Blatt von unten (Variante A)**: zuletzt genutzte Gefühle als Chips, Suche bei Bedarf, Personen per −/+; Speichern.
- **Mehrfachauswahl** der Gefühle. Die Reihenfolge der Auswahl wird gemerkt; das zuerst gewählte ist das **Hauptgefühl**.
- Eingabehilfen: zuletzt genutzte Gefühle oben (nach letzter Nutzung sortiert), Vorschläge beim Tippen (Präfix- und Teilwortsuche, sprachabhängig), unbekanntes Wort → „als neues Gefühl anlegen“.
- Mockups: siehe Anhang (Bilder aus der Brainstorming-Sitzung).

### 5.3 Verlauf / Graph (Variante B)
- X-Achse: Zeit (links → rechts). Y-Achse: oben **gut**, Mitte **neutral**, unten **schlecht**.
- Jeder Eintrag ist ein Punkt der Linie. Höhe = **Durchschnitt der Valenz** aller gewählten Gefühle. Die Linie hat an jedem Eintrag die **Farbe des Hauptgefühls** und läuft zwischen zwei Einträgen als Farbverlauf über.
- Weitere gewählte Gefühle erscheinen als kleine Farbpunkte am Eintrag (Claude-Vorschlag).
- **Personenzahl** als halbtransparente Fläche mit gestrichelter Kante **im selben Feld dahinter**, eigene Skala (Beschriftung „👥 Personen“).
- Filter-Chips **Jahr / Monat / Woche / Tag** plus Blättern ‹ › zum vorigen/nächsten Zeitraum. Der Wunsch „Tage“ wird als **frei wählbarer Tagesbereich** umgesetzt (Datumsbereich-Auswahl, zusätzlich zu „Woche“).
- Tipp auf einen Punkt zeigt den Eintrag (Zeit, alle Gefühle, Personen) mit Bearbeiten/Löschen.
- Legende der Gefühlsfarben unter dem Graphen.

### 5.4 Gefühle
- **Feste Grundliste**, auf Deutsch und Englisch übersetzt; jedes Gefühl hat eine **Farbe** und eine **Valenz** (Wert von −2 = schlecht bis +2 = gut).
- **Eigene Gefühle** ergänzbar: Name (wie eingegeben, nicht übersetzt), Farbe, Valenz (Schieberegler gut↔schlecht).
- **Änderungen an Gefühlen wirken nicht rückwirkend auf die Höhe:** Jeder Eintrag speichert die Valenz der gewählten Gefühle zum Zeitpunkt der Eingabe, der alte Verlauf bleibt unverändert. Die **Farbe** folgt dagegen der aktuellen Einstellung des Gefühls (Legende bleibt stimmig).
- Eigene Gefühle lassen sich ausblenden/bearbeiten; Grundliste-Gefühle lassen sich ausblenden, aber nicht löschen.

### 5.5 Aussehen
- **Standard dunkel** in der Palette des MusicPlayers (Navy `#0D1830`, Surface `#1A2744`, Variant `#243559`, Akzent Silber `#C8D8E8`, Sekundärtext `#9BB0CC`).
- **Helles Theme** in den Einstellungen wählbar (Hintergrund `#F4F7FB`, Akzent Navy). Option „System folgen“ zusätzlich (Claude-Vorschlag).
- Gefühlsfarben sind in beiden Themes identisch und müssen auf beiden Hintergründen erkennbar sein.

### 5.6 Sprache
- UI vollständig Deutsch und Englisch über Android-Ressourcen; Sprache folgt dem System, in den Einstellungen übersteuerbar (Per-App-Language, Android 13+).

### 5.7 Daten und Datenschutz
- Daten liegen **nur auf dem Handy**. Keine INTERNET-Berechtigung.
- **Android-Sicherung in den Einstellungen steuerbar** (Standard: **aus**, Claude-Vorschlag). Hinweis: Android legt Auto-Backup im Manifest fest, ein reiner Laufzeit-Schalter genügt nicht. Umsetzung z. B. über einen eigenen `BackupAgent`, der nur bei eingeschalteter Einstellung Daten liefert, oder einen gleichwertigen Mechanismus. Gerätewechsel (D2D) und Cloud-Backup verhalten sich gleich.
- **Platzsparende Speicherung** (Room/SQLite):
  - Eintrag: Zeitstempel (Sekunden seit Epoch bzw. Minuten-Auflösung, Integer), Personenzahl (kleine Ganzzahl), Zeitzonen-Offset (für korrekte Anzeige nach Reisen; Claude-Vorschlag).
  - Gefühle je Eintrag als Verweise auf die Gefühlstabelle inkl. Auswahlreihenfolge (Hauptgefühl = Position 0) und der Valenz zum Eingabezeitpunkt.
  - Gefühlstabelle: ID, Schlüssel (Grundliste) oder Name (eigene), Farbe, Valenz, ausgeblendet, zuletzt genutzt.
  - Größenordnung: wenige Dutzend Bytes pro Eintrag → bei 4 Einträgen/Tag über Jahre im niedrigen MB-Bereich.

### 5.8 Export (optional)
- Zeitraum wählbar.
- **CSV** (ausführlich): Datum, Uhrzeit, Zeitzone, Gefühle ausgeschrieben (in UI-Sprache), Hauptgefühl, Valenz-Durchschnitt, Personenzahl.
- **Graphen als PNG** für den gewählten Zeitraum.
- Version 1 = CSV + PNG. Der **PDF-Bericht** kommt später (siehe 9).
- Speichern über den Android-Dateidialog (Storage Access Framework) oder „Teilen“; der Nutzer bestimmt den Ort.

### 5.9 Technik (Claude-Entscheidung)
- Kotlin, Jetpack Compose (Material 3), Room, Hilt, WorkManager/AlarmManager für Erinnerungen, DataStore für Einstellungen – gleicher Unterbau wie MusicPlayer, damit Werkzeuge (ktlint, Lint, Robolectric-Tests, CI, Nightly-Security-Scan, Dependabot, Ruleset) übernommen werden können.
- Eigenes **öffentliches Repo unter github.com/WolfTasks**.

### 5.10 Verteilung
- **Ziel: Google Play Store.** Ein Play-Developer-Account fehlt aktuell.
- **Bis dahin: signierte APK über GitHub Releases** (Release-CI wie bei speedreader, Tag → Build → Release-Asset).
- Von Anfang an Play-tauglich bauen: aktuelles Target-SDK, keine eingeschränkten Berechtigungen (keine exakten Alarme, kein INTERNET), AAB-Build vorbereitbar, Datenschutzerklärung (z. B. GitHub Pages) und Data-Safety-Angaben („keine Daten erhoben/geteilt“) vorbereitet. Upload-Key so verwalten, dass später Play App Signing möglich ist.

## 6. Einschränkungen

- Erinnerungen müssen Doze, App-Standby und das Beenden der App überleben und nach Neustart des Geräts neu geplant werden. Benachrichtigungs-Berechtigung (Android 13+) muss erfragt werden; exakte Alarme brauchen ggf. eine Sonderberechtigung, ungefähre Zeitpunkte reichen für Zufallsfenster.
- Graph muss auch in der Jahresansicht (bis ~1.500 Einträge) flüssig bleiben → Aggregation pro Zeitraum (z. B. Tagesmittel in Jahr/Monat) ist dem Builder überlassen, solange Variante B erkennbar bleibt.
- Keine Netzwerkzugriffe, keine Analytics/Crash-Reporter mit Upload.

## 7. Dem Builder überlassen

- Konkrete Grundliste der Gefühle (Vorschlag ~10–14: z. B. fröhlich, dankbar, entspannt, neutral, müde, gelangweilt, gestresst, ängstlich, traurig, wütend, einsam, verliebt) mit Farben und Valenzen.
- Charting-Umsetzung (eigenes Compose-Canvas oder Bibliothek), Aggregationsstrategie für große Zeiträume.
- Minimale Android-Version, solange Per-App-Language und moderne Benachrichtigungen sauber funktionieren.
- App-Icon, endgültiger App-Name und Paketname (Arbeitstitel „Gefühls-Tagebuch“ / „Feelings Diary“).
- Einstellungsbildschirm-Aufbau, Onboarding beim ersten Start (Berechtigungen, Erinnerungsfenster).
- Verhalten bei verpassten Erinnerungen (Claude-Vorschlag: Benachrichtigung bleibt stehen, bis die nächste kommt; Eintrag bekommt den Zeitpunkt der Eingabe).

## 8. Anhang

- Mockups (HTML, Brainstorming-Sitzung): `graph-styles.html` (Varianten A/B/C, B gewählt), `graph-b-themes.html` (dunkel/hell), `entry-screen.html` (Eingabe A/B, B Standard). Werden im Repo unter `docs/superpowers/specs/mockups/` abgelegt. Sie zeigen Richtung, Farben und Anordnung verbindlich; Abstände, Größen und Feinheiten sind Builder-Sache.

## 9. Später (nicht Version 1)

- PDF-Bericht mit Graphen.
- Veröffentlichung im Play Store (sobald Account vorhanden).
- CSV-Re-Import für Gerätewechsel ohne Android-Sicherung.
- Widget für Schnelleingabe.
- Einfache Auswertungen (z. B. Durchschnitt nach Personenzahl).
