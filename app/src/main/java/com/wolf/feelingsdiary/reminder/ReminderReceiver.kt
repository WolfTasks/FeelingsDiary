package com.wolf.feelingsdiary.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wolf.feelingsdiary.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** Fired by the reminder alarm (a stage or the reminder itself): reminds if due and plans the next alarm. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val target = targetOf(intent)
        runAsync(scope) { scheduler.onAlarm(target) }
    }

    companion object {
        /** The reminder slot the alarm stands for; null for an alarm without one (then it reminds right away). */
        fun targetOf(intent: Intent): Long? =
            if (intent.hasExtra(ReminderScheduler.EXTRA_TARGET_EPOCH_MILLIS)) {
                intent.getLongExtra(ReminderScheduler.EXTRA_TARGET_EPOCH_MILLIS, 0L)
            } else {
                null
            }
    }
}
