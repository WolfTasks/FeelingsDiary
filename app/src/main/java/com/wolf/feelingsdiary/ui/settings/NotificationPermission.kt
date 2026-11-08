package com.wolf.feelingsdiary.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wolf.feelingsdiary.reminder.canPostNotifications

/** Whether notifications may be posted, and the two ways to change that. */
internal class NotificationPermission(
    val allowed: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * The notification permission state of a screen. It is re-read on every resume (the user may have
 * changed it in the system settings meanwhile) and after the system dialog. [NotificationPermission.request]
 * shows the system dialog on API 33+ and does nothing below, where no permission is needed.
 */
@Composable
internal fun rememberNotificationPermission(): NotificationPermission {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(canPostNotifications(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = canPostNotifications(context)
        onPauseOrDispose {}
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            allowed = canPostNotifications(context)
        }
    return NotificationPermission(
        allowed = allowed,
        request = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        openSettings = { openNotificationSettings(context) },
    )
}

/**
 * The action of an "allow" button: the system dialog is offered once; later taps lead to the app's
 * notification settings, so a denial never turns into a loop of dialogs.
 */
@Composable
internal fun rememberAskOnce(
    onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
): () -> Unit {
    var asked by rememberSaveable { mutableStateOf(false) }
    return {
        if (asked) {
            onOpenSystemSettings()
        } else {
            asked = true
            onRequestPermission()
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some devices lack this screen; fall back to the app's details page.
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }
}
