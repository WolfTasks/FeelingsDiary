package com.wolf.feelingsdiary.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.reminder.BatteryOptimization

/** Whether the system may restrict the app in the background (delaying reminders), and the way to change that. */
internal class BatteryState(
    val restricted: Boolean,
    val openSettings: () -> Unit,
)

/**
 * The battery state of a screen, re-read on every resume: the user changes it in the system settings
 * and comes back. [BatteryState.openSettings] opens the app's settings page, or the system's
 * battery-optimization list where that page is missing.
 */
@Composable
internal fun rememberBatteryState(battery: BatteryOptimization): BatteryState {
    val context = LocalContext.current
    var restricted by remember { mutableStateOf(!battery.isUnrestricted()) }
    LifecycleResumeEffect(battery) {
        restricted = !battery.isUnrestricted()
        onPauseOrDispose {}
    }
    return BatteryState(restricted, { openBatterySettings(context, battery) })
}

private fun openBatterySettings(
    context: Context,
    battery: BatteryOptimization,
) {
    try {
        context.startActivity(battery.settingsIntent())
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(battery.fallbackSettingsIntent())
        } catch (_: ActivityNotFoundException) {
            // Neither screen exists on this device; the hint stays, there is nothing more to offer.
        }
    }
}

/** The hint "reminders may be delayed" with the button to the battery settings. */
@Composable
internal fun BatteryHint(onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(stringResource(R.string.reminder_battery_title), color = MaterialTheme.colorScheme.error)
        Text(
            stringResource(R.string.reminder_battery_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.reminder_battery_open))
        }
    }
}
