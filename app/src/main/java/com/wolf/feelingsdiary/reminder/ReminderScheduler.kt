package com.wolf.feelingsdiary.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.domain.ReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps exactly one inexact alarm for the next reminder (no exact-alarm permission needed).
 * Each alarm plans the following one, so only the next slot is ever registered.
 */
@Singleton
class ReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val ctx: Context,
        private val settings: SettingsRepository,
        private val clock: Clock,
        private val notifier: ReminderNotifier,
    ) {
        // App start, settings changes, boot and the alarm itself may reschedule concurrently.
        private val lock = Mutex()

        suspend fun reschedule() =
            lock.withLock {
                val current = settings.settings.first()
                val reminders = current.reminders
                // No reminders before the onboarding is finished: the user has not chosen yet.
                val active = reminders.enabled && current.onboardingDone
                val alarms = ctx.getSystemService(AlarmManager::class.java)
                val operation = alarmIntent()
                if (!active) notifier.cancel()
                val next =
                    if (active) ReminderPlanner.next(reminders, ZonedDateTime.now(clock), settings.reminderSalt()) else null
                if (next == null) {
                    alarms.cancel(operation)
                } else {
                    // Inexact but Doze-proof; the same PendingIntent replaces any earlier alarm.
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), operation)
                }
            }

        /**
         * The alarm fired: remind now, then plan the next one. The inexact alarm may arrive late, inside the
         * quiet hours, or after reminders were turned off; then it only plans (or cancels). The chain never breaks.
         */
        suspend fun onAlarm() {
            try {
                val current = settings.settings.first()
                val reminders = current.reminders
                val remind = reminders.enabled && current.onboardingDone && !ReminderPlanner.isQuiet(LocalTime.now(clock), reminders)
                if (remind) notifier.show()
            } finally {
                reschedule()
            }
        }

        private fun alarmIntent(): PendingIntent =
            PendingIntent.getBroadcast(
                ctx,
                REQUEST_ALARM,
                Intent(ctx, ReminderReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private companion object {
            const val REQUEST_ALARM = 1
        }
    }
