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
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
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

    // Outside the default quiet hours (22–08), so an alarm now reminds.
    private val now = ZonedDateTime.of(2026, 11, 8, 8, 30, 0, 0, zone)
    private val clock = Clock.fixed(now.toInstant(), zone)
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
    private val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private lateinit var repo: SettingsRepository
    private lateinit var notifier: ReminderNotifier
    private lateinit var scheduler: ReminderScheduler

    private val originalZone = TimeZone.getDefault()

    @After
    fun restoreZone() = TimeZone.setDefault(originalZone)

    private suspend fun TestScope.setUp(
        clock: Clock = this@ReminderSchedulerTest.clock,
        notifier: ReminderNotifier = ReminderNotifier(app),
    ) {
        repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") })
        this@ReminderSchedulerTest.notifier = notifier
        scheduler = ReminderScheduler(app, repo, clock, notifier)
        // Reminders start only after the onboarding.
        repo.update { it.copy(onboardingDone = true) }
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

    /** An inexact alarm may arrive late, inside the quiet hours (22–08): no notification then, but the chain goes on. */
    @Test
    fun lateAlarmInQuietHoursOnlyReschedules() =
        runTest {
            val late = ZonedDateTime.of(2026, 11, 8, 22, 30, 0, 0, zone)
            setUp(Clock.fixed(late.toInstant(), zone))
            grantNotifications()
            scheduler.onAlarm()
            assertTrue(notifications.isEmpty())
            val expected = ReminderPlanner.next(ReminderSettings(), late, repo.reminderSalt())!!
            assertEquals(expected.toInstant().toEpochMilli(), alarms.single().triggerAtMs)
        }

    /** Not even briefly: a reminder that was turned off is never posted. */
    @Test
    fun alarmWhileDisabledShowsNothing() =
        runTest {
            setUp(notifier = spyk(ReminderNotifier(app)))
            grantNotifications()
            repo.update { it.copy(reminders = it.reminders.copy(enabled = false)) }
            scheduler.onAlarm()
            verify(exactly = 0) { notifier.show() }
            assertTrue(alarms.isEmpty())
        }

    /** A failing notification must not break the alarm chain. */
    @Test
    fun failingNotificationStillReschedules() =
        runTest {
            val failing = spyk(ReminderNotifier(app)) { every { show() } throws IllegalStateException("boom") }
            setUp(notifier = failing)
            val error = runCatching { scheduler.onAlarm() }.exceptionOrNull()
            assertEquals("boom", error?.message)
            assertEquals(1, alarms.size)
        }

    @Test
    fun nothingIsPlannedBeforeTheOnboarding() =
        runTest {
            setUp()
            scheduler.reschedule()
            assertEquals(1, alarms.size)
            repo.update { it.copy(onboardingDone = false) }
            scheduler.reschedule()
            assertTrue(alarms.isEmpty())
        }

    @Test
    fun alarmBeforeTheOnboardingShowsNothing() =
        runTest {
            setUp(notifier = spyk(ReminderNotifier(app)))
            grantNotifications()
            repo.update { it.copy(onboardingDone = false) }
            scheduler.onAlarm()
            verify(exactly = 0) { notifier.show() }
            assertTrue(alarms.isEmpty())
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
