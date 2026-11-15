package com.wolf.feelingsdiary.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wolf.feelingsdiary.MainActivity
import com.wolf.feelingsdiary.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The one reminder notification; a tap opens a new entry. [show] picks the default, the alarm-tone or the silent wake-up channel. */
@Singleton
class ReminderNotifier
    @Inject
    constructor(
        @ApplicationContext private val ctx: Context,
    ) {
        private val manager get() = ctx.getSystemService(NotificationManager::class.java)

        /** [silent] (heads-up popup without sound or vibration) takes precedence over [alarmSound]. */
        fun show(
            alarmSound: Boolean = false,
            silent: Boolean = false,
        ) {
            if (!canPostNotifications(ctx)) return
            val (channelId, channel) =
                when {
                    silent -> WAKE_CHANNEL_ID to wakeChannel()
                    alarmSound -> ALARM_CHANNEL_ID to alarmChannel()
                    else -> CHANNEL_ID to defaultChannel()
                }
            // Idempotent: re-creating an existing channel only updates its name.
            manager.createNotificationChannel(channel)
            val open =
                Intent(ctx, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_NEW_ENTRY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val notification =
                NotificationCompat
                    .Builder(ctx, channelId)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(ctx.getString(R.string.entry_question))
                    .setContentText(ctx.getString(R.string.reminder_text))
                    .setContentIntent(PendingIntent.getActivity(ctx, REQUEST_OPEN, open, PENDING_FLAGS))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setSilent(silent)
                    .build()
            manager.notify(NOTIFICATION_ID, notification)
        }

        /** Heads-up popup with the phone's default notification sound (none set, so the platform default applies). */
        private fun defaultChannel() =
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_HIGH)

        /** Heads-up popup that rings with the default alarm tone; does not override Do Not Disturb. */
        private fun alarmChannel() =
            NotificationChannel(ALARM_CHANNEL_ID, ctx.getString(R.string.reminder_channel_alarm_name), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    val attributes =
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attributes)
                }

        /** Heads-up popup without sound or vibration, so the day-start reminder never wakes anyone. */
        private fun wakeChannel() =
            NotificationChannel(WAKE_CHANNEL_ID, ctx.getString(R.string.reminder_channel_wake_name), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                }

        fun cancel() = manager.cancel(NOTIFICATION_ID)

        companion object {
            const val CHANNEL_ID = "reminders"
            const val ALARM_CHANNEL_ID = "reminders_alarm"
            const val WAKE_CHANNEL_ID = "reminders_wake"
            const val NOTIFICATION_ID = 1
            private const val REQUEST_OPEN = 1
            private const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

/** Below Android 13 notifications need no runtime permission; from 13 on, POST_NOTIFICATIONS must be granted. */
fun canPostNotifications(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
