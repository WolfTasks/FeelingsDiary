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
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps exactly one inexact alarm for the next reminder (no exact-alarm permission needed).
 * Each alarm plans the following one, so only the next slot is ever registered. An inexact alarm may arrive up to
 * 75 % of its lead time late (at most an hour), so a far slot is approached in stages that only plan again
 * ([ReminderPlanner.nextWake]); the alarm carries its slot in [EXTRA_TARGET_EPOCH_MILLIS].
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

        /** Plans the next slot after now, and after the slot last reminded if that is later (it came a bit early). */
        suspend fun reschedule() =
            lock.withLock {
                val current = settings.settings.first()
                val reminders = current.reminders
                // No reminders before the onboarding is finished: the user has not chosen yet.
                val active = reminders.enabled && current.onboardingDone
                val alarms = ctx.getSystemService(AlarmManager::class.java)
                if (!active) notifier.cancel()
                val now = ZonedDateTime.now(clock)
                val lastShown = settings.lastReminderShown()?.let(Instant::ofEpochMilli)
                val after = lastShown?.takeIf { it.isAfter(now.toInstant()) }?.atZone(now.zone) ?: now
                val next = if (active) ReminderPlanner.next(reminders, after, settings.reminderSalt()) else null
                if (next == null) {
                    alarms.cancel(alarmIntent(target = null))
                } else {
                    val target = next.toInstant()
                    val wake = ReminderPlanner.nextWake(target, now.toInstant(), reminders.maxDelayMinutes)
                    // Inexact but Doze-proof; the same request code replaces any earlier alarm and its target.
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wake.toEpochMilli(), alarmIntent(target))
                }
            }

        /**
         * The alarm for the slot at [targetEpochMillis] fired. A stage (more than [ReminderPlanner.STAGE_TOLERANCE]
         * before the slot) only plans again: the slot is still ahead, so it is planned once more, from closer by.
         * Otherwise remind now, then plan the slot after this one. The inexact alarm may arrive late, inside the
         * quiet hours, or after reminders were turned off; then it only plans (or cancels). The chain never breaks.
         * Without a slot (an alarm set by an older version) it reminds right away.
         */
        suspend fun onAlarm(targetEpochMillis: Long? = null) {
            val target = targetEpochMillis?.let(Instant::ofEpochMilli)
            if (target != null && ReminderPlanner.isStage(target, clock.instant())) {
                reschedule()
                return
            }
            try {
                val current = settings.settings.first()
                val reminders = current.reminders
                val remind = reminders.enabled && current.onboardingDone && !ReminderPlanner.isQuiet(LocalTime.now(clock), reminders)
                if (remind) notifier.show(reminders.alarmSound)
            } finally {
                // Never the same slot twice, should the reminder have come within the tolerance before it:
                // remembered, so that any other caller of reschedule() skips the slot too.
                if (target != null) settings.setLastReminderShown(target.toEpochMilli())
                reschedule()
            }
        }

        // Extras do not count for PendingIntent identity; FLAG_UPDATE_CURRENT swaps in the new target.
        private fun alarmIntent(target: Instant?): PendingIntent =
            PendingIntent.getBroadcast(
                ctx,
                REQUEST_ALARM,
                Intent(ctx, ReminderReceiver::class.java).apply {
                    target?.let { putExtra(EXTRA_TARGET_EPOCH_MILLIS, it.toEpochMilli()) }
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        companion object {
            const val EXTRA_TARGET_EPOCH_MILLIS = "target_epoch_millis"
            private const val REQUEST_ALARM = 1
        }
    }
