# FeelingsDiary (Gefühls-Tagebuch)

Android-App: erinnert 1–4× täglich daran festzuhalten, wie man sich fühlt und wie viele Menschen um einen herum sind, und zeigt beides als Verlauf (Jahr/Monat/Woche/Tag). Daten bleiben auf dem Handy. UI Deutsch/Englisch.

## Stand: Phase 1 (Fundament + Eingabe)

Umgesetzt:

- Eingabe-Bildschirm B: Mehrfachauswahl von Gefühlen (erstes gewähltes = Hauptgefühl), Vorschläge, eigene Gefühle, Personenzahl (Anzahl der anderen Menschen, 0–999)
- Platzsparende lokale Speicherung (Room), Valenz −2…+2 je Gefühl als Momentaufnahme pro Eintrag
- Einfache Startseite mit Eintragsliste
- Dunkles/helles Theme, UI auf Deutsch und Englisch
- Keine INTERNET-Berechtigung, keine exakten Alarme, `allowBackup=false`
- CI (Build, Tests, ktlint, Lint, Security-Scan)

Noch nicht enthalten: Verlauf-Graph (Phase 2), Erinnerungen und Einstellungen (Phase 3), Export und Release (Phase 4).

## Bauen und Testen

Voraussetzungen: JDK 21 und Android SDK.

- `local.properties` im Projektroot mit `sdk.dir=...` anlegen (nicht eingecheckt).
- `JAVA_HOME` auf JDK 21 setzen, sonst nimmt der Gradle-Daemon die IntelliJ-JBR ohne jlink:

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot"
./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug
```

Optional: `scripts/install-hooks.sh` installiert einen Pre-Commit-Hook (ktlint).

## Dokumentation

- [Design/Spec](docs/superpowers/specs/2026-11-07-feelings-diary-design.md) (Mockups unter `docs/superpowers/specs/mockups/`)
- [Plan Phase 1](docs/superpowers/plans/2026-11-07-feelings-diary-phase1-foundation-entry.md) – enthält den Phasen-Fahrplan (Phase 2: Verlauf, 3: Erinnerungen und Einstellungen, 4: Export und Release)
