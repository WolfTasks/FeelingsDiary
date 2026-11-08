package com.wolf.feelingsdiary.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderNotifierTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private val notifier = ReminderNotifier(app)

    @Before
    fun grant() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun posted() = shadowOf(manager).allNotifications.single()

    @Test
    fun defaultChannelIsHighImportanceWithDefaultSound() {
        notifier.show()
        val channel = manager.getNotificationChannel("reminders")
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        // No custom sound set: the platform default notification sound applies.
        assertEquals(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, channel.sound)
        assertNull(manager.getNotificationChannel("reminders_alarm"))
    }

    @Test
    fun defaultUsesReminderChannel() {
        notifier.show()
        assertEquals("reminders", posted().channelId)
    }

    @Test
    fun notificationIsAHighPriorityReminder() {
        notifier.show()
        val n = posted()
        assertEquals(Notification.CATEGORY_REMINDER, n.category)
        assertEquals(Notification.PRIORITY_HIGH, n.priority)
    }

    @Test
    fun alarmSoundUsesAlarmChannel() {
        notifier.show(alarmSound = true)
        assertEquals("reminders_alarm", posted().channelId)
        val channel = manager.getNotificationChannel("reminders_alarm")
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), channel.sound)
        assertEquals(AudioAttributes.USAGE_ALARM, channel.audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION, channel.audioAttributes.contentType)
    }
}
