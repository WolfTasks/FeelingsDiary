package com.wolf.feelingsdiary.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.MainActivity
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.di.SystemZoneClock
import com.wolf.feelingsdiary.domain.ReminderPlanner
import com.wolf.feelingsdiary.domain.ReminderSettings
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Plain [Application] instead of the Hilt app: the app-start rescheduling would otherwise race
 * these tests on the same alarm slot with the real settings store and the real clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReminderSchedulerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 11, 8, 7, 0, 0, 0, zone)
    private val clock = Clock.fixed(now.toInstant(), zone)
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private lateinit var repo: SettingsRepository
    private lateinit var notifier: ReminderNotifier
    private lateinit var scheduler: ReminderScheduler

    private val originalZone = TimeZone.getDefault()

    @After
    fun restoreZone() = TimeZone.setDefault(originalZone)

    private fun TestScope.setUp(clock: Clock = this@ReminderSchedulerTest.clock) {
        repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") })
        notifier = ReminderNotifier(app)
        scheduler = ReminderScheduler(app, repo, clock, notifier)
    }

    private fun grantNotifications() = shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun schedulesNextSlotInexact() =
        runTest {
            setUp()
            scheduler.reschedule()
            val expected = ReminderPlanner.next(ReminderSettings(), now, repo.reminderSalt())!!
            assertEquals(1, alarms.size)
            val alarm = alarms.single()
            assertEquals(expected.toInstant().toEpochMilli(), alarm.triggerAtMs)
            assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
            assertNotEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
        }

    /** BootReceiver reschedules on TIMEZONE_CHANGED: the long-lived scheduler must plan in the new zone. */
    @Test
    fun rescheduleFollowsTimeZoneChange() =
        runTest {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            setUp(SystemZoneClock { now.toInstant() })
            scheduler.reschedule()
            val berlin = alarms.single().triggerAtMs
            val newYork = ZoneId.of("America/New_York")
            TimeZone.setDefault(TimeZone.getTimeZone(newYork))
            scheduler.reschedule()
            val expected = ReminderPlanner.next(ReminderSettings(), now.withZoneSameInstant(newYork), repo.reminderSalt())!!
            assertEquals(expected.toInstant().toEpochMilli(), alarms.single().triggerAtMs)
            assertNotEquals(berlin, alarms.single().triggerAtMs)
        }

    @Test
    fun rescheduleReplacesPreviousAlarm() =
        runTest {
            setUp()
            scheduler.reschedule()
            scheduler.reschedule()
            assertEquals(1, alarms.size)
        }

    @Test
    fun disabledCancelsAlarmAndNotification() =
        runTest {
            setUp()
            grantNotifications()
            scheduler.onAlarm()
            assertEquals(1, alarms.size)
            assertEquals(1, notifications.size)
            repo.update { it.copy(reminders = it.reminders.copy(enabled = false)) }
            scheduler.reschedule()
            assertTrue(alarms.isEmpty())
            assertTrue(notifications.isEmpty())
        }

    @Test
    fun alarmShowsNotificationAndSchedulesNext() =
        runTest {
            setUp()
            grantNotifications()
            scheduler.onAlarm()
            val nm = shadowOf(app.getSystemService(NotificationManager::class.java))
            val notification = nm.getNotification(1)
            assertEquals(1, notifications.size)
            assertEquals("reminders", notification.channelId)
            assertEquals(MainActivity.ACTION_NEW_ENTRY, shadowOf(notification.contentIntent).savedIntent.action)
            assertTrue(notification.flags and android.app.Notification.FLAG_AUTO_CANCEL != 0)
            assertTrue(alarms.single().triggerAtMs > now.toInstant().toEpochMilli())
        }

    /** Robolectric runs API 36 here, so POST_NOTIFICATIONS (API 33+) is a runtime permission. */
    @Test
    fun noNotificationWithoutPermission() =
        runTest {
            setUp()
            notifier.show()
            assertTrue(notifications.isEmpty())
        }
}
