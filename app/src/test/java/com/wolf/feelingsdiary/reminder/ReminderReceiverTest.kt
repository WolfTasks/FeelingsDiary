package com.wolf.feelingsdiary.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.MainActivity
import com.wolf.feelingsdiary.domain.ReminderMode
import com.wolf.feelingsdiary.domain.ReminderPlanner
import com.wolf.feelingsdiary.domain.ReminderSettings
import com.wolf.feelingsdiary.testutil.resetSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalTime
import java.time.ZonedDateTime

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

    // Reminders active at any wall-clock time: fixed slots, no quiet hours, onboarding done.
    private val reminders =
        ReminderSettings(count = 4, mode = ReminderMode.FIXED, quietStart = LocalTime.MIDNIGHT, quietEnd = LocalTime.MIDNIGHT)

    @Before
    fun activeReminders() = resetSettings { it.copy(reminders = reminders, onboardingDone = true) }

    @After
    fun defaults() = resetSettings()

    /**
     * App start schedules once, and again once it sees the settings of [activeReminders]; wait for the
     * alarm those settings plan (fixed slots, so salt-free), then clear it so the receiver's own work becomes observable.
     */
    private fun clearStartupAlarm() {
        awaitUntil {
            alarms.singleOrNull()?.let { targetOf(it) } ==
                ReminderPlanner.next(reminders, ZonedDateTime.now(), 0L)?.toInstant()?.toEpochMilli()
        }
        alarms.forEach { alarmManager.cancel(it.operation!!) }
        assertTrue(alarms.isEmpty())
    }

    private fun targetOf(alarm: ShadowAlarmManager.ScheduledAlarm) = ReminderReceiver.targetOf(shadowOf(alarm.operation).savedIntent)

    @Test
    fun targetIsReadFromTheIntent() {
        val intent = Intent(app, ReminderReceiver::class.java)
        assertNull(ReminderReceiver.targetOf(intent))
        intent.putExtra(ReminderScheduler.EXTRA_TARGET_EPOCH_MILLIS, 1_234L)
        assertEquals(1_234L, ReminderReceiver.targetOf(intent))
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
        clearStartupAlarm()
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        ).forEach { action ->
            app.sendBroadcast(Intent(action).setPackage(app.packageName))
            awaitUntil { alarms.size == 1 }
            alarms.forEach { alarmManager.cancel(it.operation!!) }
        }
        assertTrue(nm.allNotifications.isEmpty())
    }

    @Test
    fun bootReceiverIgnoresOtherActions() {
        clearStartupAlarm()
        // Returns before goAsync(), so a direct call is safe here.
        BootReceiver().onReceive(app, Intent(Intent.ACTION_SCREEN_ON))
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(alarms.isEmpty())
    }
}
