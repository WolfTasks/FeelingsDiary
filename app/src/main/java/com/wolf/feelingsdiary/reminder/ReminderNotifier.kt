package com.wolf.feelingsdiary.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wolf.feelingsdiary.MainActivity
import com.wolf.feelingsdiary.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The one reminder notification; a tap opens a new entry. */
@Singleton
class ReminderNotifier
    @Inject
    constructor(
        @ApplicationContext private val ctx: Context,
    ) {
        private val manager get() = ctx.getSystemService(NotificationManager::class.java)

        fun show() {
            if (!canPostNotifications(ctx)) return
            // Idempotent: re-creating an existing channel only updates its name.
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
            val open =
                Intent(ctx, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_NEW_ENTRY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val notification =
                NotificationCompat
                    .Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(ctx.getString(R.string.entry_question))
                    .setContentText(ctx.getString(R.string.reminder_text))
                    .setContentIntent(PendingIntent.getActivity(ctx, REQUEST_OPEN, open, PENDING_FLAGS))
                    .setAutoCancel(true)
                    .build()
            manager.notify(NOTIFICATION_ID, notification)
        }

        fun cancel() = manager.cancel(NOTIFICATION_ID)

        companion object {
            const val CHANNEL_ID = "reminders"
            const val NOTIFICATION_ID = 1
            private const val REQUEST_OPEN = 1
            private const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

/** Below Android 13 notifications need no runtime permission; from 13 on, POST_NOTIFICATIONS must be granted. */
fun canPostNotifications(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
