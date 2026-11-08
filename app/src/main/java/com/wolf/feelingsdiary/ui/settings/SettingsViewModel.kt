package com.wolf.feelingsdiary.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.feelingsdiary.backup.BackupNotifier
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.InputMode
import com.wolf.feelingsdiary.domain.ReminderSettings
import com.wolf.feelingsdiary.domain.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repo: SettingsRepository,
        private val backup: BackupNotifier,
    ) : ViewModel() {
        // Starts from the last known settings, so a recreated screen does not show the defaults first.
        val state: StateFlow<AppSettings> =
            repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), repo.lastKnown ?: AppSettings())

        fun setTheme(theme: ThemeMode) = update { it.copy(theme = theme) }

        fun setInput(input: InputMode) = update { it.copy(input = input) }

        /** Turning backup on also tells Android there is data to back up, once the flag is stored. */
        fun setBackup(enabled: Boolean) {
            viewModelScope.launch {
                repo.update { it.copy(backupEnabled = enabled) }
                if (enabled) backup.dataChanged()
            }
        }

        fun updateReminders(transform: (ReminderSettings) -> ReminderSettings) = update { it.copy(reminders = transform(it.reminders)) }

        // Each change is a read-modify-write on the store, so concurrent changes never overwrite each other.
        private fun update(transform: (AppSettings) -> AppSettings) {
            viewModelScope.launch { repo.update(transform) }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
