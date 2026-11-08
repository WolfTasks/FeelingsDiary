package com.wolf.feelingsdiary.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.reminder.BatteryOptimization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BatteryStateTest {
    @get:Rule
    val rule = createComposeRule()

    private class FakeBattery(
        var unrestricted: Boolean,
    ) : BatteryOptimization {
        override fun isUnrestricted() = unrestricted

        override fun settingsIntent() = Intent("test.SETTINGS")
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun stateIsReReadWhenTheScreenResumes() {
        val battery = FakeBattery(unrestricted = false)
        val owner = TestOwner().also { it.registry.currentState = Lifecycle.State.RESUMED }
        var state: BatteryState? = null
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { state = rememberBatteryState(battery) }
        }
        rule.runOnIdle { assertTrue(state!!.restricted) }

        // The user sets "Unrestricted" in the system settings and comes back.
        battery.unrestricted = true
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.runOnIdle { assertFalse(state!!.restricted) }
    }

    @Test
    fun opensTheSettingsIntent() {
        var state: BatteryState? = null
        var activity: Activity? = null
        rule.setContent {
            activity = LocalActivity.current
            state = rememberBatteryState(FakeBattery(unrestricted = false))
        }
        rule.runOnIdle { state!!.openSettings() }
        assertEquals("test.SETTINGS", shadowOf(activity!!).nextStartedActivity.action)
    }

    @Test
    fun fallsBackWhenTheSettingsPageIsMissing() {
        var state: BatteryState? = null
        var activity: Activity? = null
        rule.setContent {
            activity = LocalActivity.current
            state = rememberBatteryState(FakeBattery(unrestricted = false))
        }
        // Only the fallback resolves; starting anything else throws ActivityNotFoundException.
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).checkActivities(true)
        val fallback = FakeBattery(false).fallbackSettingsIntent()
        shadowOf(activity!!.packageManager).addResolveInfoForIntent(fallback, ResolveInfo())
        rule.runOnIdle { state!!.openSettings() }
        assertEquals(fallback.action, shadowOf(activity!!).nextStartedActivity.action)
    }
}
