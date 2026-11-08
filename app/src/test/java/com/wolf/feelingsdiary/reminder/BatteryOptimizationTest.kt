package com.wolf.feelingsdiary.reminder

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BatteryOptimizationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val power = shadowOf(ctx.getSystemService(PowerManager::class.java))
    private val battery = SystemBatteryOptimization(ctx)

    @Test
    fun restrictedUntilOnTheExemptionList() {
        power.setIgnoringBatteryOptimizations(ctx.packageName, false)
        assertFalse(battery.isUnrestricted())
        power.setIgnoringBatteryOptimizations(ctx.packageName, true)
        assertTrue(battery.isUnrestricted())
    }

    @Test
    fun settingsIntentOpensTheAppDetailsOfThisPackage() {
        val intent = battery.settingsIntent()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${ctx.packageName}", intent.data.toString())
    }
}
