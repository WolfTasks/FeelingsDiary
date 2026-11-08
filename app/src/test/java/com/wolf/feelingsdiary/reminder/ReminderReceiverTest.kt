package com.wolf.feelingsdiary.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * End-to-end through the real Hilt app: the manifest-declared receivers get their collaborators
 * injected and work in the background via goAsync().
 */
@RunWith(RobolectricTestRunner::class)
class ReminderReceiverTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val alarmManager = app.getSystemService(AlarmManager::class.java)
    private val alarms get() = shadowOf(alarmManager).scheduledAlarms
    private val nm get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    private fun awaitUntil(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("condition not met in time")
    }

    /** App start schedules once; clear that so the receiver's own work becomes observable. */
    private fun clearStartupAlarm() {
        awaitUntil { alarms.isNotEmpty() }
        alarms.forEach { alarmManager.cancel(it.operation!!) }
        assertTrue(alarms.isEmpty())
    }

    @Test
    fun receiverShowsNotificationAndSchedulesNext() {
        clearStartupAlarm()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.sendBroadcast(Intent(app, ReminderReceiver::class.java))
        awaitUntil { alarms.isNotEmpty() && nm.allNotifications.isNotEmpty() }
        val notification = nm.getNotification(1)
        assertEquals("reminders", notification.channelId)
        assertEquals(MainActivity.ACTION_NEW_ENTRY, shadowOf(notification.contentIntent).savedIntent.action)
        assertTrue(alarms.single().triggerAtMs > System.currentTimeMillis())
    }

    @Test
    fun bootReceiverReschedules() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        ).forEach { action ->
            if (alarms.isEmpty()) awaitUntil { alarms.isNotEmpty() }
            alarms.forEach { alarmManager.cancel(it.operation!!) }
            app.sendBroadcast(Intent(action).setPackage(app.packageName))
            awaitUntil { alarms.size == 1 }
        }
        assertTrue(nm.allNotifications.isEmpty())
    }
}
