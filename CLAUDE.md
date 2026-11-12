# FeelingsDiary – Kurzreferenz

Android (Kotlin, Compose, Room, Hilt), Single-Module `:app`, Paket `com.wolf.feelingsdiary`. Spec und Phasenplan: `docs/superpowers/`.

## Pakete (`app/src/main/java/com/wolf/feelingsdiary`)

- `data/db` – Room: Entities, DAOs, `AppDatabase`, `BaseFeelings` (Grundliste, Seed)
- `data` – `DiaryRepository`; `data/settings` – DataStore-Einstellungen (`SettingsRepository`)
- `reminder` – AlarmManager-Planung (`ReminderScheduler`), Receiver (Erinnerung, Boot), Benachrichtigung (`ReminderNotifier`)
- `backup` – `DiaryBackupAgent` (gesteuerte Android-Sicherung)
- `domain` – reine Logik: `Entry`, `EntryTime`, `Feeling`, `FeelingSuggester`, `GraphPeriod`, `GraphBuilder`, `Settings`, `ReminderPlanner`
- `di` – Hilt-Module, `SystemZoneClock` (Systemzone bei jedem Aufruf)
- `ui/entry`, `ui/home`, `ui/navigation`, `ui/theme`, `ui/settings`, `ui/feelings` (Gefühle verwalten), `ui/onboarding` – Compose-Screens, ViewModels, Nav, Theme; `ui/home` = Verlauf (Graph, Zeitraum, Liste, Details)
- Tests: `app/src/test/...` (JUnit4, MockK, Robolectric, Compose UI-Test)

## Befehle

```bash
export JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot"
./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug
```

- `ktlintCheck` ist vor jedem Commit Pflicht (Pre-Commit-Hook: `scripts/install-hooks.sh`).
- Hook (`scripts/pre-commit`) wählt via `scripts/select-jdk.sh` ein JDK mit `jlink` und führt `ktlintCheck testDebugUnitTest` aus; `install-hooks.sh` funktioniert auch im Worktree.
- JAVA_HOME-Gotcha (manuelle Gradle-Läufe; der Hook wählt selbst): ohne JDK 21 nimmt der Daemon die IntelliJ-JBR ohne jlink und der Build scheitert. `local.properties` (`sdk.dir`) fehlt im Worktree, ggf. aus dem Hauptcheckout kopieren.
- Bei Pipes `set -o pipefail`.

## Regeln

- Berechtigungen genau `POST_NOTIFICATIONS` + `RECEIVE_BOOT_COMPLETED`, keine `INTERNET`; keine exakten Alarme (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`). Sicherung nur über `DiaryBackupAgent` (`fullBackupOnly`), der nur bei Einstellung „Android-Sicherung“ = an (Standard aus) und nur bei Ende-zu-Ende-verschlüsseltem Transport (Cloud nur E2E) oder Gerätewechsel (D2D) an Auto Backup übergibt; Regeln (`backup_rules.xml`, `data_extraction_rules.xml`) enthalten nur Datenbank und `datastore/settings.preferences_pb`. Wird von `ManifestPolicyTest` geprüft.
- Erinnerungen nur ungenau (`setAndAllowWhileIdle`), nie in der Nachtruhe; Graph-x nach echter Zeit (`epochMinute`), nicht nach Ortszeit.
- Jeder UI-Text als String-Ressource: `values/strings.xml` (EN, Default) und `values-de/strings.xml`; `StringsParityTest` erzwingt Parität. Keine hartkodierten UI-Texte.
- Personenzahl = Anzahl der ANDEREN Menschen, 0 = allein, Eingabe 0–999.
- Valenz −2…+2, als Momentaufnahme je Gefühl pro Eintrag gespeichert.
- Hauptgefühl = zuerst gewähltes Gefühl (Position 0). Punkthöhe im Graphen = gewichtete Valenz (1, ½, ¼ … nach Position).
- Neue Grundgefühle nur hinten an `BaseFeelings.all` anhängen und per Room-Migration (`Migrations`) für Bestandsinstallationen nachziehen.
- Grundliste-Reihenfolge: Basisgefühle nach id, danach eigene alphabetisch nach Label.

## Git

- Arbeiten im Worktree auf Feature-Branch; nie direkt auf `main` pushen.
- Nur explizite Pfade stagen (kein `git add -A` / `git add .`).
- Vor jedem Push `git remote get-url origin` und Branch prüfen.
- Commit-Messages: Conventional Commits, Co-Authored-By-Zeile am Ende.
