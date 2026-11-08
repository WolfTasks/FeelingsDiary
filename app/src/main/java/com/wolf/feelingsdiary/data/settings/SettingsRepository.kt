package com.wolf.feelingsdiary.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.InputMode
import com.wolf.feelingsdiary.domain.ReminderSettings
import com.wolf.feelingsdiary.domain.ThemeMode
import com.wolf.feelingsdiary.domain.TimeWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import kotlin.random.Random

class SettingsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) {
        val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

        suspend fun update(transform: (AppSettings) -> AppSettings) {
            store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
        }

        suspend fun reminderSalt(): Long {
            store.data.first()[SALT]?.let { return it }
            var result = 0L
            store.edit { prefs ->
                result = prefs[SALT] ?: Random.nextLong().also { prefs[SALT] = it }
            }
            return result
        }

        companion object {
            private val ENABLED = booleanPreferencesKey("reminder_enabled")
            private val COUNT = intPreferencesKey("reminder_count")
            private val MODE = stringPreferencesKey("reminder_mode")
            private val WINDOWS = stringPreferencesKey("reminder_windows")
            private val FIXED = stringPreferencesKey("reminder_fixed_times")
            private val QUIET_START = intPreferencesKey("quiet_start")
            private val QUIET_END = intPreferencesKey("quiet_end")
            private val THEME = stringPreferencesKey("theme")
            private val INPUT = stringPreferencesKey("input_mode")
            private val BACKUP = booleanPreferencesKey("backup_enabled")
            private val ONBOARDING = booleanPreferencesKey("onboarding_done")
            private val SALT = longPreferencesKey("reminder_salt")

            private const val MINUTES_PER_DAY = 24 * 60

            /** The backup flag alone, for callers outside Hilt (the backup agent); default off. */
            fun backupEnabled(prefs: Preferences): Boolean = prefs[BACKUP] ?: false
        }

        private fun minutes(t: LocalTime) = t.hour * 60 + t.minute

        private fun time(m: Int): LocalTime? = if (m in 0 until MINUTES_PER_DAY) LocalTime.of(m / 60, m % 60) else null

        private fun parseWindows(raw: String?): List<TimeWindow>? {
            val parts = raw?.split(",") ?: return null
            if (parts.size != 4) return null
            return parts.map { p ->
                val ends = p.split("-").map { it.trim().toIntOrNull()?.let(::time) ?: return null }
                if (ends.size != 2 || ends[0] >= ends[1]) return null
                TimeWindow(ends[0], ends[1])
            }
        }

        private fun parseTimes(raw: String?): List<LocalTime>? {
            val parts = raw?.split(",") ?: return null
            if (parts.size != 4) return null
            return parts.map { it.trim().toIntOrNull()?.let(::time) ?: return null }
        }

        private inline fun <reified E : Enum<E>> enumOr(
            raw: String?,
            default: E,
        ): E = enumValues<E>().firstOrNull { it.name == raw } ?: default

        private fun Preferences.toSettings(): AppSettings {
            val d = ReminderSettings()
            return AppSettings(
                reminders =
                    ReminderSettings(
                        enabled = this[ENABLED] ?: d.enabled,
                        count = this[COUNT]?.takeIf { it in 1..4 } ?: d.count,
                        mode = enumOr(this[MODE], d.mode),
                        windows = parseWindows(this[WINDOWS]) ?: d.windows,
                        fixedTimes = parseTimes(this[FIXED]) ?: d.fixedTimes,
                        quietStart = this[QUIET_START]?.let(::time) ?: d.quietStart,
                        quietEnd = this[QUIET_END]?.let(::time) ?: d.quietEnd,
                    ),
                theme = enumOr(this[THEME], ThemeMode.DARK),
                input = enumOr(this[INPUT], InputMode.SCREEN),
                backupEnabled = backupEnabled(this),
                onboardingDone = this[ONBOARDING] ?: false,
            )
        }

        private fun MutablePreferences.write(s: AppSettings) {
            val r = s.reminders
            this[ENABLED] = r.enabled
            this[COUNT] = r.count
            this[MODE] = r.mode.name
            this[WINDOWS] = r.windows.joinToString(",") { "${minutes(it.start)}-${minutes(it.end)}" }
            this[FIXED] = r.fixedTimes.joinToString(",") { minutes(it).toString() }
            this[QUIET_START] = minutes(r.quietStart)
            this[QUIET_END] = minutes(r.quietEnd)
            this[THEME] = s.theme.name
            this[INPUT] = s.input.name
            this[BACKUP] = s.backupEnabled
            this[ONBOARDING] = s.onboardingDone
        }
    }
