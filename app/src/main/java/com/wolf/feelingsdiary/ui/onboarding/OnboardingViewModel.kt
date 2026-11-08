package com.wolf.feelingsdiary.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.reminder.BatteryOptimization
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val repo: SettingsRepository,
        val batteryOptimization: BatteryOptimization,
    ) : ViewModel() {
        private var finishing = false
        private val _finished = MutableStateFlow(false)

        /**
         * True once the choice is stored. The screen leaves only then: leaving first would cancel the write
         * with this ViewModel. A state, not a callback, so it also reaches a screen recreated meanwhile.
         */
        val finished: StateFlow<Boolean> = _finished.asStateFlow()

        /** Stores the reminder choice and marks the onboarding as done in one write; a second call is ignored. */
        fun finish(
            enabled: Boolean,
            count: Int,
        ) {
            if (finishing) return
            finishing = true
            viewModelScope.launch {
                repo.update { it.copy(reminders = it.reminders.copy(enabled = enabled, count = count), onboardingDone = true) }
                _finished.value = true
            }
        }
    }
