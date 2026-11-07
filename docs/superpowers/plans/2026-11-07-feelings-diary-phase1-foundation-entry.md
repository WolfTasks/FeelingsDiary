# FeelingsDiary – Phase 1: Fundament + Eingabe – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lauffähige Android-App, in der man Einträge (Gefühle mit Mehrfachauswahl + Personenzahl) über den Eingabe-Bildschirm B erfassen kann, mit platzsparender Speicherung, DE/EN-UI, dunklem/hellem Theme und grüner CI.

**Architecture:** Single-Module-App (`:app`), Paket `com.wolf.feelingsdiary`, Schichten `data` (Room-Entities/DAO/Repository), `domain` (reine Kotlin-Logik: Vorschläge, Zeitmodell), `ui` (Compose-Screens + ViewModels, Hilt). Toolchain, Gradle-Setup, CI und Security-Floors werden 1:1 aus dem MusicPlayer übernommen und nur umbenannt.

**Tech Stack:** Kotlin 2.4.21 (AGP 9.5.1 built-in Kotlin), Gradle 9.8.2, Compose BOM 2026.09.00 / Material 3, Room 2.8.5 (KSP 2.3.13), Hilt 2.60.1, Navigation-Compose 2.10.2, JUnit4 + MockK 1.14.11 + Robolectric 4.17 + Compose UI-Test, ktlint (Plugin 14.2.0, Engine 1.8.0).

**Spec:** `docs/superpowers/specs/2026-11-07-feelings-diary-design.md` (Mockups: `docs/superpowers/specs/mockups/`)

**Referenz-Projekt:** `C:\Users\Admin\IdeaProjects\MusicPlayer\MusicPlayer` (Vorlage für Gradle-Dateien, CI, Floors, Theme-Farben).

## Phasen-Fahrplan (je Phase ein eigener Plan, je Phase eine Session)

| Phase | Inhalt | Ergebnis |
|---|---|---|
| **1 (dieser Plan)** | Gerüst, CI, Theme, i18n, Datenmodell, Grundliste, Vorschläge, Eingabe B, eigene Gefühle, einfache Startseite | Einträge erfassen und als Liste sehen |
| 2 | Verlauf-Graph Variante B, Filter Jahr/Monat/Woche/Tagesbereich/Tag, Blättern, Aggregation, Eintrag-Details bearbeiten/löschen | Verlauf wie im Mockup |
| 3 | Erinnerungen (Zufallsfenster, feste Zeiten, Nachtruhe, Boot, Benachrichtigungs-Berechtigung), Einstellungen (Theme inkl. System, Sprache, Eingabe A/B inkl. Schnell-Blatt A, Gefühle verwalten, Android-Sicherung via BackupAgent), Onboarding | Vollständige App-Logik |
| 4 | Export CSV + PNG (SAF/Teilen), Release-CI mit signierter APK auf GitHub Releases, Datenschutzerklärung (Pages), Play-Vorbereitung (AAB, Data-Safety-Text) | v1.0 |

## Global Constraints

- Keine `android.permission.INTERNET` im gemergten Manifest (Spec 3, 5.7).
- `android:allowBackup="false"` in Phase 1 (die steuerbare Sicherung kommt in Phase 3; Standard laut Spec: aus).
- Keine eingeschränkten Berechtigungen (kein `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`) (Spec 5.1, 5.10).
- `applicationId`/`namespace` = `com.wolf.feelingsdiary`; `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37` (wie MusicPlayer).
- App-Name: EN „Feelings Diary“, DE „Gefühls-Tagebuch“ (Arbeitstitel).
- Jeder UI-Text als String-Ressource in `values/strings.xml` (Englisch, Default) **und** `values-de/strings.xml`; keine hartkodierten UI-Texte.
- Personenzahl = Anzahl der **anderen** Menschen; „allein“ = 0; keine fachliche Obergrenze, Eingabefeld akzeptiert 0–999 (Plausibilitätsgrenze, Planer-Entscheidung).
- Valenz-Skala: Ganzzahl −2 (schlecht) … +2 (gut); Eintrag speichert die Valenz je Gefühl zum Eingabezeitpunkt (Spec 5.4).
- Hauptgefühl = zuerst gewähltes Gefühl (Position 0) (Spec 5.2).
- Theme-Farben dunkel: bg `#0D1830`, surface `#1A2744`, variant `#243559`, primary `#C8D8E8`, onSurfaceVariant `#9BB0CC`, onPrimary `#0D1830`; hell: bg `#F4F7FB`, surface `#FFFFFF`, variant `#E3EAF3`, primary `#243559`, onSurfaceVariant `#4F6488`, onPrimary `#FFFFFF` (Spec 5.5, Mockup `graph-b-themes.html`).
- Lokal bauen mit `JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.13.9-hotspot` (Gradle-Daemon nimmt sonst die IntelliJ-JBR ohne jlink); `local.properties` (sdk.dir) aus dem MusicPlayer kopieren; bei Pipes `set -o pipefail`.
- Arbeiten im Worktree; nur explizite Pfade stagen (kein `git add -A`); nie direkt auf `main` pushen; vor jedem Push `git -C <repo> remote get-url origin` und Branch prüfen.

## Review Focus

1. Suche mit Groß-/Kleinschreibung und Umlauten („FRÖH“, „frÖhlich“) muss „fröhlich“ finden; englische UI findet „happy“. → Test in Task 4.
2. Hauptgefühl abwählen: Das nächste noch gewählte Gefühl rückt auf Position 0, keine Lücken/Duplikate in den Positionen. → Test in Task 5.
3. Eigenes Gefühl mit Namen, der schon existiert (auch als übersetzter Grundlisten-Name, egal welche Groß-/Kleinschreibung) → kein Duplikat, das bestehende Gefühl wird ausgewählt. → Test in Task 5.
4. Bildschirm drehen / Prozess-Tod mitten in der Eingabe → Auswahl, Reihenfolge und Personenzahl bleiben erhalten (SavedStateHandle). → Test in Task 5.
5. Zeitzone/Sommerzeit: Ein um 23:30 in UTC+2 gespeicherter Eintrag zeigt nach Wechsel nach UTC+0 weiterhin 23:30 am ursprünglichen Datum. → Test in Task 3.

---

### Task 1: Projekt-Gerüst, Toolchain, CI, Theme, i18n-Basis

**Files:**
- Create (Kopie aus MusicPlayer, angepasst): `settings.gradle.kts` (rootProject.name = "FeelingsDiary"), `build.gradle.kts` (Floors-buildscript + Plugins, ohne Änderung der Floor-Liste), `gradle.properties`, `gradle/libs.versions.toml` (ohne media3/retrofit/okhttp/coil/guava-Einträge; mit workmanager/hilt-work schon drin für Phase 3), `gradle/tooling-security-floors.gradle.kts`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat` (Modus 100755 für `gradlew`: `git update-index --chmod=+x gradlew`), `.github/workflows/ci.yml` (Artefaktname `feelingsdiary-debug-apk`), `.github/workflows/nightly-security.yml` + `.github/scripts/*` (aus MusicPlayer), `.github/dependabot.yml`, `scripts/pre-commit`, `scripts/install-hooks.sh`
- Create: `app/build.gradle.kts` (wie MusicPlayer ohne media3/coil/guava-Constraint; `namespace`/`applicationId` `com.wolf.feelingsdiary`; KSP `room.schemaLocation`; Schemas als `debug`/`androidTest`-Assets; `unitTests.isIncludeAndroidResources = true` + jvmArgs wie MusicPlayer)
- Create: `app/src/main/AndroidManifest.xml` (Application `.FeelingsDiaryApp`, `android:allowBackup="false"`, Activity `.MainActivity` exported Launcher)
- Create: `app/src/main/java/com/wolf/feelingsdiary/FeelingsDiaryApp.kt` (`@HiltAndroidApp`), `MainActivity.kt` (`@AndroidEntryPoint`, `setContent { FeelingsDiaryTheme { … } }`)
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/theme/Color.kt`, `Theme.kt`, `Type.kt`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml` (`app_name`), `app/src/main/res/xml/locales_config.xml` (en, de) + `android:localeConfig` im Manifest
- Test: `app/src/test/java/com/wolf/feelingsdiary/ManifestPolicyTest.kt`, `StringsParityTest.kt`

**Interfaces:**
- Produces: `@Composable fun FeelingsDiaryTheme(darkTheme: Boolean = true, content: @Composable () -> Unit)`; `val DarkColors: ColorScheme`, `val LightColors: ColorScheme` (Werte aus Global Constraints).

- [ ] **Step 1: Gerüst anlegen** – Dateien aus dem MusicPlayer kopieren und wie oben anpassen; `local.properties` kopieren (nicht committen, steht in `.gitignore`).
- [ ] **Step 2: Failing Tests schreiben**

```kotlin
@RunWith(RobolectricTestRunner::class)
class ManifestPolicyTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    @Test fun noInternetPermission() {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
        assertFalse(info.requestedPermissions.orEmpty().any { it.endsWith("EXACT_ALARM") })
    }
    @Test fun backupDisabled() {
        assertEquals(0, ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
```

`StringsParityTest` (reines JUnit, liest `src/main/res/values/strings.xml` und `values-de/strings.xml` per `DocumentBuilderFactory` relativ zum Modulverzeichnis): `assertEquals(enKeys, deKeys)` für `<string>`- und `<plurals>`-Namen.

- [ ] **Step 3: Tests laufen lassen** – `./gradlew testDebugUnitTest --tests "*ManifestPolicyTest*" --tests "*StringsParityTest*"` → zuerst FAIL (Klassen/Ressourcen fehlen), nach Gerüst PASS.
- [ ] **Step 4: Theme implementieren** – `DarkColors`/`LightColors` mit den Werten aus Global Constraints (`background`, `surface`, `surfaceVariant`, `primary`, `onPrimary`, `onSurfaceVariant`, `onBackground`/`onSurface` = `#FFFFFF` dunkel bzw. `#0D1830` hell).
- [ ] **Step 5: Vollbuild** – `./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** – `git add` der oben genannten Pfade; `git commit -m "build: scaffold FeelingsDiary app with toolchain, CI and theme"`.

---

### Task 2: Datenmodell (Room) + Grundliste

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/data/db/FeelingEntity.kt`, `EntryEntity.kt`, `EntryFeelingEntity.kt`, `FeelingDao.kt`, `EntryDao.kt`, `AppDatabase.kt`, `BaseFeelings.kt`
- Create: `app/src/main/java/com/wolf/feelingsdiary/di/DatabaseModule.kt`
- Create: `app/schemas/com.wolf.feelingsdiary.data.db.AppDatabase/1.json` (von KSP erzeugt, committen)
- Modify: `values/strings.xml`, `values-de/strings.xml` (Labels der Grundliste)
- Test: `app/src/test/java/com/wolf/feelingsdiary/data/AppDatabaseTest.kt`

**Interfaces:**
- Produces:
  - `@Entity(tableName = "feelings", indices = [Index(value = ["baseKey"], unique = true)]) data class FeelingEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val baseKey: String?, val customName: String?, val colorArgb: Int, val valence: Int, val hidden: Boolean = false, val lastUsedAt: Long? = null)` – genau eins von `baseKey`/`customName` ist gesetzt.
  - `@Entity(tableName = "entries", indices = [Index("epochMinute")]) data class EntryEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val epochMinute: Int, val utcOffsetMinutes: Int, val people: Int)`
  - `@Entity(tableName = "entry_feelings", primaryKeys = ["entryId", "position"], foreignKeys = [ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = CASCADE), ForeignKey(FeelingEntity::class, ["id"], ["feelingId"], onDelete = RESTRICT)], indices = [Index("feelingId")]) data class EntryFeelingEntity(val entryId: Long, val position: Int, val feelingId: Long, val valence: Int)`
  - `data class EntryWithFeelings(@Embedded val entry: EntryEntity, @Relation(parentColumn = "id", entityColumn = "entryId") val feelings: List<EntryFeelingEntity>)`
  - `FeelingDao`: `fun observeAll(): Flow<List<FeelingEntity>>`, `suspend fun insert(f: FeelingEntity): Long`, `suspend fun insertAll(fs: List<FeelingEntity>)`, `suspend fun count(): Int`, `suspend fun touch(ids: List<Long>, at: Long)`
  - `EntryDao`: `@Transaction suspend fun insertWithFeelings(entry: EntryEntity, feelings: List<EntryFeelingEntity>): Long` (setzt `entryId`), `@Transaction fun observeRecent(limit: Int): Flow<List<EntryWithFeelings>>` (neueste zuerst), `suspend fun delete(id: Long)`
  - `AppDatabase` (version = 1, exportSchema = true), Seed der Grundliste per `RoomDatabase.Callback.onCreate` aus `BaseFeelings.all`.
  - `object BaseFeelings { val all: List<BaseFeeling> }`, `data class BaseFeeling(val key: String, @StringRes val label: Int, val colorArgb: Int, val valence: Int)` mit genau dieser Liste (Planer-Entscheidung, Spec 7 überlässt sie dem Builder):

| key | EN | DE | Farbe | Valenz |
|---|---|---|---|---|
| happy | happy | fröhlich | #F2B705 | +2 |
| grateful | grateful | dankbar | #2FB8A8 | +2 |
| in_love | in love | verliebt | #E86AA6 | +2 |
| relaxed | relaxed | entspannt | #3FAE5A | +1 |
| neutral | neutral | neutral | #9AA0A6 | 0 |
| tired | tired | müde | #8A7FD0 | −1 |
| bored | bored | gelangweilt | #A68A64 | −1 |
| stressed | stressed | gestresst | #F07B1D | −1 |
| anxious | anxious | ängstlich | #C77DDB | −1 |
| lonely | lonely | einsam | #5F7FA8 | −2 |
| sad | sad | traurig | #4A84F0 | −2 |
| angry | angry | wütend | #E04848 | −2 |

  String-Ressourcen: `feeling_<key>`.

- [ ] **Step 1: Failing Tests schreiben** (Robolectric, `Room.inMemoryDatabaseBuilder` mit demselben Seed-Callback):
  - `seedCreatesTwelveBaseFeelings` → `count() == 12`, alle `baseKey` aus der Tabelle vorhanden.
  - `insertWithFeelingsKeepsOrderAndValenceSnapshot` → Eintrag mit Gefühlen [stressed, tired] → `observeRecent(10).first()[0].feelings.sortedBy { it.position }.map { it.feelingId to it.valence } == listOf(stressedId to -1, tiredId to -1)`.
  - `deletingEntryCascadesFeelings` → nach `delete(id)` keine `entry_feelings`-Zeilen mehr.
  - `deletingUsedFeelingIsRejected` → Löschen einer referenzierten `FeelingEntity` wirft `SQLiteConstraintException`.
- [ ] **Step 2: Laufen lassen** – `./gradlew testDebugUnitTest --tests "*AppDatabaseTest*"` → FAIL.
- [ ] **Step 3: Entities, DAOs, `AppDatabase`, `BaseFeelings`, `DatabaseModule` (Singleton, Name `feelings.db`) implementieren.**
- [ ] **Step 4: Tests + Strings-Parität** – `./gradlew testDebugUnitTest` → PASS; `app/schemas/.../1.json` existiert.
- [ ] **Step 5: Commit** – `git commit -m "feat(data): room model with base feeling list"`.

---

### Task 3: Repository + Zeitmodell

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/domain/EntryTime.kt`, `domain/Feeling.kt`, `domain/Entry.kt`
- Create: `app/src/main/java/com/wolf/feelingsdiary/data/DiaryRepository.kt`, `di/RepositoryModule.kt` (bindet `Clock`)
- Test: `app/src/test/java/com/wolf/feelingsdiary/domain/EntryTimeTest.kt`, `data/DiaryRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 2 DAOs/Entities.
- Produces:
  - `data class Feeling(val id: Long, val baseKey: String?, val customName: String?, val colorArgb: Int, val valence: Int, val hidden: Boolean, val lastUsedAt: Long?)`
  - `data class Entry(val id: Long, val time: EntryTime, val people: Int, val feelingIds: List<Long>, val valences: List<Int>)` (Listen nach Position sortiert)
  - `data class EntryTime(val epochMinute: Int, val utcOffsetMinutes: Int) { fun localDateTime(): LocalDateTime; companion object { fun now(clock: Clock): EntryTime } }` – `localDateTime()` nutzt den **gespeicherten** Offset, nicht die aktuelle Zone.
  - `class DiaryRepository @Inject constructor(feelingDao, entryDao, clock: Clock)`: `fun observeFeelings(): Flow<List<Feeling>>`, `fun observeRecentEntries(limit: Int = 50): Flow<List<Entry>>`, `suspend fun addEntry(people: Int, feelingIds: List<Long>): Long`, `suspend fun addCustomFeeling(name: String, colorArgb: Int, valence: Int): Long`
  - `addEntry`: Zeit = `EntryTime.now(clock)`; Valenz je Gefühl aus der aktuellen `FeelingEntity`; danach `touch(feelingIds, clock.millis())`. Wirft `IllegalArgumentException` bei leerer Gefühlsliste, doppelten IDs oder `people !in 0..999`.

- [ ] **Step 1: Failing Tests**
  - `EntryTimeTest.keepsOriginalLocalTimeAcrossZoneChange`: `EntryTime.now(Clock.fixed(Instant.parse("2026-07-01T21:30:00Z"), ZoneId.of("Europe/Berlin")))` → `localDateTime() == LocalDateTime.of(2026, 7, 1, 23, 30)`; dasselbe Objekt liefert bei Default-Zone UTC weiterhin `23:30` (Review Focus 5).
  - `EntryTimeTest.epochMinuteRoundTrip`: `epochMinute == 29_715_690` für `2026-07-01T21:30Z` (`Instant.epochSecond / 60`).
  - `DiaryRepositoryTest` (Robolectric, In-Memory-DB, fester Clock): `addEntryStoresValenceSnapshotAndTouchesFeelings` (danach Valenz des Gefühls ändern → Eintrag behält alte Valenz; `lastUsedAt == clock.millis()`), `addEntryRejectsEmptyDuplicateOrOutOfRangePeople` (3 × `assertThrows<IllegalArgumentException>`).
- [ ] **Step 2: Laufen lassen** → FAIL.
- [ ] **Step 3: Implementieren** (Mapping Entity↔Domain im Repository).
- [ ] **Step 4: Laufen lassen** → PASS.
- [ ] **Step 5: Commit** – `git commit -m "feat(data): diary repository with time-zone-stable entry time"`.

---

### Task 4: Eingabehilfe – Vorschläge

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/domain/FeelingSuggester.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/domain/FeelingSuggesterTest.kt`

**Interfaces:**
- Consumes: `Feeling` (Task 3).
- Produces:
  - `data class Suggestions(val recent: List<Feeling>, val others: List<Feeling>, val canCreate: Boolean)`
  - `object FeelingSuggester { fun suggest(query: String, feelings: List<Feeling>, label: (Feeling) -> String, recentCount: Int = 5): Suggestions }`
  - Leere/Blank-Query: nur `hidden == false`; `recent` = Gefühle mit `lastUsedAt != null`, absteigend nach `lastUsedAt`, max. `recentCount`; `others` = Rest in Grundlisten-Reihenfolge (Reihenfolge von `BaseFeelings.all`, eigene danach alphabetisch); `canCreate = false`.
  - Nicht-leere Query: Vergleich nach `normalize()` = `lowercase(Locale.ROOT)` + Unicode-NFD ohne Kombinationszeichen (ö→o, ä→a, ü→u, ß bleibt); Präfix-Treffer vor Teilwort-Treffern, innerhalb nach `lastUsedAt` absteigend; Ergebnis komplett in `others`, `recent` leer; `canCreate = true`, wenn kein Label exakt (normalisiert) der Query entspricht.

- [ ] **Step 1: Failing Tests**
  - `emptyQueryPutsRecentFirst`: drei Gefühle mit `lastUsedAt` 3, 1, 2 → `recent.map { it.id } == listOf(a, c, b)`.
  - `matchesCaseAndUmlautInsensitive`: Query „FRÖH“ und „froh“ finden „fröhlich“; Query „happ“ mit EN-Label findet „happy“ (Review Focus 1).
  - `prefixBeforeContains`: Query „ge“ bei [„angespannt“(eigenes), „gestresst“, „gelangweilt“] → „gestresst“ und „gelangweilt“ vor „angespannt“.
  - `canCreateOnlyWithoutExactMatch`: „Gestresst“ → `false`; „genervt“ → `true`.
  - `hiddenFeelingsNeverSuggested`: ausgeblendetes Gefühl „müde“ fehlt bei leerer Query und bei Query „mü“.
- [ ] **Step 2: Laufen lassen** → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Laufen lassen** → PASS.
- [ ] **Step 5: Commit** – `git commit -m "feat(domain): feeling suggestions with recent-first and fuzzy prefix search"`.

---

### Task 5: EntryViewModel

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/entry/EntryViewModel.kt`, `ui/entry/EntryUiState.kt`, `ui/FeelingLabel.kt`
- Test: `app/src/test/java/com/wolf/feelingsdiary/ui/entry/EntryViewModelTest.kt`

**Interfaces:**
- Consumes: `DiaryRepository` (Task 3), `FeelingSuggester` (Task 4).
- Produces:
  - `fun interface FeelingLabeler { fun label(f: Feeling): String }` + Hilt-Binding, das für `baseKey` `context.getString(BaseFeelings.labelFor(key))` liefert, sonst `customName` (in `ui/FeelingLabel.kt`).
  - `data class EntryUiState(val query: String, val suggestions: Suggestions, val selectedIds: List<Long>, val people: Int?, val canSave: Boolean, val saved: Boolean)`
  - `@HiltViewModel class EntryViewModel @Inject constructor(repo: DiaryRepository, labeler: FeelingLabeler, savedState: SavedStateHandle)`:
    `val state: StateFlow<EntryUiState>`, `fun onQueryChange(q: String)`, `fun toggle(feelingId: Long)`, `fun setPeople(n: Int)`, `fun createFeeling(name: String, colorArgb: Int, valence: Int)`, `fun save()`
  - `selectedIds` und `people` liegen im `SavedStateHandle` (Keys `"selected"` als `LongArray`, `"people"`); `query` ebenfalls (`"query"`).
  - `canSave = selectedIds.isNotEmpty() && people != null`.
  - `createFeeling`: Wenn ein (auch ausgeblendetes) Gefühl mit normalisiert gleichem Label existiert → kein Insert, dieses Gefühl wird ausgewählt (und `hidden` bleibt unverändert); sonst `repo.addCustomFeeling` und neues Gefühl ans Ende der Auswahl; `query` wird geleert.

- [ ] **Step 1: Failing Tests** (MockK für `DiaryRepository` mit `MutableStateFlow` als Gefühlsquelle, `StandardTestDispatcher` + `Dispatchers.setMain`):
  - `toggleKeepsSelectionOrderAndPromotesNextPrimary`: toggle(a), toggle(b), toggle(c), toggle(a) → `selectedIds == listOf(b, c)` (Review Focus 2).
  - `saveRequiresFeelingAndPeople`: anfangs `canSave == false`; nach toggle(a) weiterhin false; nach `setPeople(0)` true; `save()` → `coVerify { repo.addEntry(0, listOf(a)) }` und `saved == true`.
  - `createFeelingWithExistingNameSelectsExisting`: Label „Gestresst“ existiert als Grundliste → `createFeeling("gestresst", …)` → `coVerify(exactly = 0) { repo.addCustomFeeling(any(), any(), any()) }`, `selectedIds` enthält stressed (Review Focus 3).
  - `stateSurvivesProcessDeath`: VM mit `SavedStateHandle(mapOf("selected" to longArrayOf(b, a), "people" to 3))` → `selectedIds == listOf(b, a)`, `people == 3` (Review Focus 4).
  - `setPeopleRejectsOutOfRange`: `setPeople(1000)` → `people` unverändert.
- [ ] **Step 2: Laufen lassen** → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Laufen lassen** → PASS.
- [ ] **Step 5: Commit** – `git commit -m "feat(entry): entry view model with ordered multi-select and saved state"`.

---

### Task 6: Eingabe-Bildschirm B + Dialoge

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/entry/EntryScreen.kt`, `ui/entry/FeelingChip.kt`, `ui/entry/PeopleSelector.kt`, `ui/entry/NewFeelingDialog.kt`, `ui/entry/PeopleNumberDialog.kt`
- Modify: `values/strings.xml`, `values-de/strings.xml`
- Test: `app/src/test/java/com/wolf/feelingsdiary/ui/entry/EntryScreenTest.kt`

**Interfaces:**
- Consumes: `EntryViewModel`/`EntryUiState` (Task 5), `FeelingsDiaryTheme` (Task 1).
- Produces: `@Composable fun EntryScreen(state: EntryUiState, actions: EntryActions, onClose: () -> Unit)` (zustandslos, testbar) + `@Composable fun EntryRoute(onDone: () -> Unit, vm: EntryViewModel = hiltViewModel())`; `data class EntryActions(val onQuery: (String) -> Unit, val onToggle: (Long) -> Unit, val onPeople: (Int) -> Unit, val onCreate: (String, Int, Int) -> Unit, val onSave: () -> Unit)`.
- Layout gemäß Mockup `entry-screen.html` Variante B: Kopfzeile ✕ + „Neuer Eintrag · HH:mm“; Überschrift „Wie fühlst du dich?“; Suchfeld; bei Query: Vorschlags-Chips + „+ „<query>“ als neues Gefühl“ (wenn `canCreate`); sonst Abschnitte „Zuletzt genutzt“ (falls nicht leer) und „Alle“; Chips mit Farbpunkt, ausgewählt = gefüllt mit Gefühlsfarbe, Hauptgefühl zusätzlich mit Marker „1“; Personen-Schnellknöpfe `allein, 1, 2, 3, 4, 5, 6+` (6+ → `PeopleNumberDialog`, Zahlenfeld, Werte 6–999, sonst Fehlertext); „Speichern“ (disabled wenn `!canSave`).
- `NewFeelingDialog`: Name (vorbelegt mit Query), Farbauswahl aus 12 festen Zusatzfarben (`#7E57C2, #26A69A, #EC407A, #8D6E63, #42A5F5, #66BB6A, #FFA726, #78909C, #D4E157, #AB47BC, #29B6F6, #FF7043`), Valenz-Slider −2…+2 (Schritte 1, Beschriftung schlecht/neutral/gut).
- Content-Descriptions für Chips/Knöpfe als String-Ressourcen (TalkBack).

- [ ] **Step 1: Failing Compose-Tests** (Robolectric + `createComposeRule`, zustandsloser `EntryScreen` mit Fake-State und aufgezeichneten Aktionen):
  - `selectingChipsAndPeopleEnablesSave`: Klick auf Chip „stressed“ und „tired“, Klick auf „3“ → Aktionen `onToggle(stressed)`, `onToggle(tired)`, `onPeople(3)` in dieser Reihenfolge.
  - `saveDisabledWithoutSelection`: State mit leerer Auswahl → Knoten „Save“ `assertIsNotEnabled()`.
  - `sixPlusOpensNumberDialogAndRejectsInvalid`: Klick „6+“, Eingabe „3“ → Fehlertext sichtbar, kein `onPeople`; Eingabe „12“ + OK → `onPeople(12)`.
  - `createOfferShownOnlyWhenAllowed`: State mit `canCreate = true`, Query „genervt“ → Text „+ “genervt” as new feeling“ sichtbar; Klick öffnet Dialog mit vorbelegtem Namen.
- [ ] **Step 2: Laufen lassen** → FAIL.
- [ ] **Step 3: Implementieren** (EN-Strings als Default, DE in `values-de`).
- [ ] **Step 4: Laufen lassen** – `./gradlew testDebugUnitTest` → PASS (inkl. `StringsParityTest`).
- [ ] **Step 5: Commit** – `git commit -m "feat(entry): entry screen B with suggestions, people selector and custom feelings"`.

---

### Task 7: Startseite (Phase-1-Liste) + Navigation

**Files:**
- Create: `app/src/main/java/com/wolf/feelingsdiary/ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/navigation/AppNavHost.kt`
- Modify: `MainActivity.kt`, `values/strings.xml`, `values-de/strings.xml`
- Test: `app/src/test/java/com/wolf/feelingsdiary/ui/home/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `DiaryRepository.observeRecentEntries`, `FeelingLabeler`, `EntryRoute`.
- Produces: Routen `"home"` (Start) und `"entry"`; `HomeScreen(entries: List<HomeEntryRow>, onAdd: () -> Unit)`; `data class HomeEntryRow(val id: Long, val time: String, val feelings: List<Pair<String, Int>>, val people: Int)` (Label + Farbe; Zeit lokal formatiert über `DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)` aus `EntryTime.localDateTime()`).
- Platzhalter-Text „Der Verlauf kommt in Phase 2“ als String-Ressource; Knopf „+ Wie fühlst du dich?“ (Mockup-Stil, `primary`) navigiert zu `"entry"`; nach Speichern zurück zu `"home"`.

- [ ] **Step 1: Failing Compose-Tests**: `showsEntriesNewestFirst` (zwei Zeilen, erste = neuere Zeit), `addButtonNavigates` (`onAdd` aufgerufen), `emptyStateShowsHint`.
- [ ] **Step 2: Laufen lassen** → FAIL.
- [ ] **Step 3: Implementieren.**
- [ ] **Step 4: Vollbuild** – `./gradlew ktlintCheck testDebugUnitTest assembleDebug lintDebug` → BUILD SUCCESSFUL; App im Emulator/Gerät starten (`./gradlew installDebug`), einen Eintrag mit zwei Gefühlen und „allein“ speichern, er erscheint oben in der Liste; Sprache auf Deutsch stellen → Texte deutsch.
- [ ] **Step 5: Commit** – `git commit -m "feat(home): phase-1 entry list and navigation"`.

---

### Task 8: Doku, PR, Repo-Einstellungen

**Files:**
- Modify: `README.md` (Stand Phase 1, Build-Hinweise JDK 21/`local.properties`, Phasen-Fahrplan-Link)
- Create: `CLAUDE.md` (Kurzreferenz: Paketstruktur, Testbefehle, JAVA_HOME-Gotcha, Regeln aus Global Constraints)

- [ ] **Step 1: Doku schreiben und committen** – `git commit -m "docs: readme and claude guide for phase 1"`.
- [ ] **Step 2: Push + PR** – Branch `feat/phase1-foundation-entry`, `gh pr create --base main` (Body endet mit `🤖 Generated with [Claude Code](https://claude.com/claude-code)`), `gh pr checks --watch` → CI `build` grün.
- [ ] **Step 3: Repo-Einstellungen vorbereiten (nach Merge, Wolfgang führt aus):** Befehle analog MusicPlayer auflisten – Dependabot-Alerts/-Updates, Private Vulnerability Reporting, Ruleset (deletion, non_fast_forward, required check `build`, Admin-Bypass). Nicht selbst ausführen.
