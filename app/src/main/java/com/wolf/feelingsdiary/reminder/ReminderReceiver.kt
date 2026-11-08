package com.wolf.feelingsdiary.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wolf.feelingsdiary.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** Fired by the reminder alarm: shows the notification and plans the next reminder. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = runAsync(scope) { scheduler.onAlarm() }
}
