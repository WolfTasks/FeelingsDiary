# FeelingsDiary (Gefühls-Tagebuch)

Android-App: erinnert 1–4× täglich daran festzuhalten, wie man sich fühlt und wie viele Menschen um einen herum sind, und zeigt beides als Verlauf (Jahr/Monat/Woche/Tag). Daten bleiben auf dem Handy. UI Deutsch/Englisch.

## Stand: Phase 3 (Erinnerungen und Einstellungen)

Umgesetzt (zusätzlich zu Phase 1: Fundament + Eingabe und Phase 2: Verlauf):

- Erinnerungen 1–4× täglich: zufällig in Zeitfenstern oder zu festen Uhrzeiten, mit Nachtruhe; ungenaue Alarme (keine exakten Alarme), Neuplanung nach Neustart
- Heads-up-Benachrichtigung mit Standardton, optional mit Alarmton; Tipp öffnet die Eingabe
- Einstellungen: Theme (System/Hell/Dunkel), Sprache, Eingabemodus (Vollbild oder Schnell-Blatt), Gefühle verwalten (Farbe, Valenz, eigene umbenennen, ausblenden), Android-Sicherung (Standard aus; Cloud nur Ende-zu-Ende-verschlüsselt, Gerätewechsel erlaubt)
- Onboarding beim ersten Start mit Erinnerungsauswahl und Benachrichtigungs-Berechtigung
- Graph nach echter Zeit, korrekt über Sommerzeitwechsel und Zeitzonen; Details zeigen bei abweichendem Offset einen Hinweis „(UTC+1)“
- Berechtigungen nur `POST_NOTIFICATIONS` und `RECEIVE_BOOT_COMPLETED`; keine `INTERNET`-Berechtigung
- Verlauf-Graph, Eintragsliste, Bearbeiten und Löschen (Phase 2); Eingabe mit Mehrfachauswahl, Vorschlägen, eigenen Gefühlen und Personenzahl (Anzahl der anderen Menschen, 0–999)
- Platzsparende lokale Speicherung (Room), Valenz −2…+2 je Gefühl als Momentaufnahme pro Eintrag
- CI (Build, Tests, ktlint, Lint, Security-Scan)

Noch nicht enthalten: Export und Release (Phase 4).

## Bauen und Testen

Voraussetzungen: JDK 21 und Android SDK.

- `local.properties` im Projektroot mit `sdk.dir=...` anlegen (nicht eingecheckt).
- `JAVA_HOME` auf JDK 21 setzen, sonst nimmt der Gradle-Daemon die IntelliJ-JBR ohne jlink:

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot"
./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug
```

Optional: `scripts/install-hooks.sh` installiert einen Pre-Commit-Hook (ktlint, Unit-Tests; wählt bei gestagten `.kt`-Dateien selbst ein passendes JDK).

## Dokumentation

- [Design/Spec](docs/superpowers/specs/2026-11-07-feelings-diary-design.md) (Mockups unter `docs/superpowers/specs/mockups/`)
- [Plan Phase 1](docs/superpowers/plans/2026-11-07-feelings-diary-phase1-foundation-entry.md) – enthält den Phasen-Fahrplan (Phase 2: Verlauf, 3: Erinnerungen und Einstellungen, beide erledigt; 4: Export und Release)
- [Plan Phase 3](docs/superpowers/plans/2026-11-08-feelings-diary-phase3-reminders-settings.md)
