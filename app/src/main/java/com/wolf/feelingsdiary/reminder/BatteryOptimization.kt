package com.wolf.feelingsdiary.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Whether the system may restrict this app in the background, and where the user can change that.
 * No permission is requested (REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is a Play policy risk); the app only
 * detects the state and guides to the setting. Restricted apps get their alarms deferred (e.g. Samsung Freecess).
 */
interface BatteryOptimization {
    /** True when the app is on the battery-optimization exemption list ("Unrestricted"). */
    fun isUnrestricted(): Boolean

    /** The app's system settings page, where the battery usage can be set to "Unrestricted". */
    fun settingsIntent(): Intent

    /** The system's battery-optimization list, for devices that lack the app details page. */
    fun fallbackSettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

class SystemBatteryOptimization
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BatteryOptimization {
        override fun isUnrestricted(): Boolean =
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

        override fun settingsIntent(): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
