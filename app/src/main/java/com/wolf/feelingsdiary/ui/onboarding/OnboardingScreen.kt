package com.wolf.feelingsdiary.ui.onboarding

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.ReminderSettings
import com.wolf.feelingsdiary.ui.settings.ReminderCountRow
import com.wolf.feelingsdiary.ui.settings.SwitchRow
import com.wolf.feelingsdiary.ui.settings.rememberAskOnce
import com.wolf.feelingsdiary.ui.settings.rememberNotificationPermission

@Composable
fun OnboardingRoute(
    onDone: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val finished by vm.finished.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(finished) { if (finished) currentOnDone() }
    val permission = rememberNotificationPermission()
    OnboardingScreen(permission.allowed, permission.request, permission.openSettings, vm::finish)
}

/**
 * First-start welcome: purpose, privacy, and the reminder choice (on/off and count; the rest stays at its
 * defaults and can be changed in the settings). Nothing is stored before [onStart].
 */
@Composable
fun OnboardingScreen(
    notificationsAllowed: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onStart: (enabled: Boolean, count: Int) -> Unit,
) {
    val defaults = ReminderSettings()
    var enabled by rememberSaveable { mutableStateOf(defaults.enabled) }
    var count by rememberSaveable { mutableIntStateOf(defaults.count) }
    val allow = rememberAskOnce(onRequestPermission, onOpenSystemSettings)
    // Below Android 13 notifications need no permission, so there is nothing to allow.
    val showAllow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
                Text(stringResource(R.string.onboarding_text), style = MaterialTheme.typography.bodyLarge)
                Column(Modifier.padding(top = 16.dp)) {
                    SwitchRow(stringResource(R.string.reminder_switch), enabled) { enabled = it }
                    ReminderCountRow(count, enabled) { count = it }
                }
                if (showAllow) {
                    OutlinedButton(onClick = allow, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.onboarding_allow))
                    }
                }
                Text(
                    stringResource(R.string.onboarding_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onStart(enabled, count) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.onboarding_start)) }
        }
    }
}
