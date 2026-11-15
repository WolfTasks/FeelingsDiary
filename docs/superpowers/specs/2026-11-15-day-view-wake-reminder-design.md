# Tagesansicht ab erstem Eintrag + Tagesbeginn-Erinnerung – Design

Stand: 2026-11-15. Kleine Änderung, zwei unabhängige Teile.

## 1. Tagesansicht beginnt mit dem ersten Eintrag

**Warum:** Wegen der Nachtruhe ist in der Tagesansicht (0–24 Uhr) ein großer Teil der Breite leer.

**Ziel:** In der Tagesansicht (`PeriodKind.DAY`) beginnt die x-Achse mit der vollen Stunde des ersten Eintrags
des Tages (abgerundet, Ortszeit des Eintrags) und endet um 24 Uhr.

- Tag ohne Einträge: weiter 0–24 Uhr.
- Woche, Monat, Jahr, Bereich: unverändert.
- Positionen bleiben nach echter Zeit (`epochMinute`), nur auf den kürzeren Bereich skaliert.
- Beschriftung passt sich an (eigene Wahl: volle Stunden in einem Raster von 1/2/3/6 h, je nach Spannweite,
  sodass etwa 4–6 Labels entstehen; Überlappung verhindert weiterhin `visibleLabelIndices`).
- Umsetzung (eigene Wahl): `GraphBuilder` liefert für DAY die Startminute des sichtbaren Bereichs in `GraphData`
  mit; `xAxisLabels` nutzt sie, damit Punkte und Labels dieselbe Skala haben.

## 2. Tagesbeginn-Erinnerung

**Warum:** Die Stimmung direkt nach dem Aufwachen festhalten. Die bisherigen Slots kommen dafür zu spät.

**Ziele:**
- Eigene Erinnerung „Tagesbeginn“ zu einer einstellbaren Aufwachzeit, unabhängig von Slots und Nachtruhe.
- Standard: an, 7:00.
- Immer lautlos: Popup (heads-up), kein Ton, keine Vibration – unabhängig von der Alarmton-Einstellung.
  Sie darf niemanden wecken, der noch schläft.
- Ungenau wie alle Erinnerungen (`setAndAllowWhileIdle`, gleiche max. Verspätung).

**Nicht-Ziele:** keine Zufallsfenster für den Tagesbeginn; keine Änderung an den bestehenden Slots.

**Anti-Ziel:** Ein Ton oder eine Vibration beim Tagesbeginn.

### Ansätze

- **A (gewählt): Sonderzeit im bestehenden Planer.** `ReminderPlanner.next` berücksichtigt die Aufwachzeit
  zusätzlich zu den Slots; der Alarm trägt ein Extra „Tagesbeginn“. Stufenplanung, max. Verspätung, Boot und
  Tests der Kette werden wiederverwendet.
- B: eigene zweite Alarmkette – sauber getrennt, aber Stufenplanung/Boot/Tests doppelt, zwei Wecker ohne
  gegenseitiges Wissen. Verworfen.
- Ersten Slot vorverlegen – nicht unabhängig von den Slots, nicht lautlos. Verworfen.

### Festgelegt

- `ReminderSettings` bekommt `wakeEnabled: Boolean = true` und `wakeTime: LocalTime = 07:00`
  (DataStore, mit Standard für Bestandsinstallationen).
- Der Hauptschalter „Erinnerungen“ schaltet auch den Tagesbeginn ab; vor Abschluss des Onboardings keine.
- Nachtruhe gilt nicht für den Tagesbeginn: weder beim Planen noch beim Unterdrücken in `onAlarm`.
- Fallen Aufwachzeit und ein Slot auf dieselbe Minute: eine Meldung, lautlos.
- Neuer Benachrichtigungskanal „Tagesbeginn“ (`IMPORTANCE_HIGH`, `setSound(null, null)`, Vibration aus);
  gleiche Notification-ID, Tipp öffnet neuen Eintrag wie bisher.
- Einstellungen: unter „Erinnerungen“ ein Schalter „Tagesbeginn“ und die Aufwachzeit (Zeitauswahl);
  alle Texte EN + DE.
- CLAUDE.md-Regel ändern zu: „Slot-Erinnerungen nie in der Nachtruhe; Tagesbeginn-Erinnerung ausgenommen und
  immer lautlos.“

### Dem Builder überlassen

- Name/Form des Alarm-Extras; Aufbau der Settings-UI im Detail; ob der Tagesbeginn im Onboarding erscheint
  (eigene Wahl: nein, Standard an genügt).
- Eigene Wahl: Der Tagesbeginn kommt auch, wenn an dem Tag schon ein Eintrag existiert (wie die Slots).
