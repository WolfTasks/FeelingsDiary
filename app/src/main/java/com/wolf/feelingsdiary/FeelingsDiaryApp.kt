package com.wolf.feelingsdiary

import android.app.Application
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.di.ApplicationScope
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FeelingsDiaryApp : Application() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var scheduler: ReminderScheduler

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // The first value covers app start, every later one a change of the reminder settings.
        scope.launch {
            settings.settings
                .reminderPlanChanges()
                .collect { scheduler.reschedule() }
        }
    }
}

/** Every change that alters the reminder plan: the reminder settings themselves and the end of the onboarding. */
internal fun Flow<AppSettings>.reminderPlanChanges(): Flow<Any> = map { it.reminders to it.onboardingDone }.distinctUntilChanged()
