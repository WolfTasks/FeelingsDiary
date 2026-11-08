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
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.InputMode
import com.wolf.feelingsdiary.domain.ReminderSettings
import com.wolf.feelingsdiary.domain.ThemeMode
import com.wolf.feelingsdiary.reminder.canPostNotifications

data class SettingsActions(
    val onBack: () -> Unit,
    val onTheme: (ThemeMode) -> Unit,
    val onInput: (InputMode) -> Unit,
    val onLanguage: (String?) -> Unit,
    val onReminders: ((ReminderSettings) -> ReminderSettings) -> Unit,
    val onRequestNotifications: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onManageFeelings: () -> Unit,
    val onBackup: (Boolean) -> Unit,
)

private val THEMES =
    listOf(
        ThemeMode.DARK to R.string.theme_dark,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.SYSTEM to R.string.theme_system,
    )

private val INPUTS =
    listOf(
        InputMode.SCREEN to R.string.settings_input_screen,
        InputMode.QUICK_SHEET to R.string.settings_input_sheet,
    )

// null = follow the system language. Language names stay in their own language in every UI language.
private val LANGUAGES = listOf(null to R.string.language_system, "de" to R.string.language_de, "en" to R.string.language_en)

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onManageFeelings: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Not part of AppSettings: the system stores the per-app language. A change recreates the activity.
    var language by remember { mutableStateOf(appLanguage(context)) }
    // Re-read on every resume: the user may have changed it in the system settings meanwhile.
    var notificationsAllowed by remember { mutableStateOf(canPostNotifications(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = canPostNotifications(context)
        onPauseOrDispose {}
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsAllowed = canPostNotifications(context)
        }
    val actions =
        SettingsActions(
            onBack = onBack,
            onTheme = vm::setTheme,
            onInput = vm::setInput,
            onLanguage = { tag ->
                language = tag
                setAppLanguage(context, tag)
            },
            onReminders = vm::updateReminders,
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onOpenNotificationSettings = { openNotificationSettings(context) },
            onManageFeelings = onManageFeelings,
            onBackup = vm::setBackup,
        )
    SettingsScreen(state, actions, language, notificationsAllowed)
}

/**
 * Section order (spec): Reminders, Input, Manage feelings, Appearance, Language (API 33+), Privacy.
 */
@Composable
fun SettingsScreen(
    state: AppSettings,
    actions: SettingsActions,
    language: String?,
    notificationsAllowed: Boolean = true,
    showLanguage: Boolean = supportsAppLanguage,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_cd))
                }
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle(R.string.settings_section_reminders)
                ReminderSection(
                    state.reminders,
                    notificationsAllowed,
                    actions.onReminders,
                    actions.onRequestNotifications,
                    actions.onOpenNotificationSettings,
                )
                Section(R.string.settings_section_input) {
                    INPUTS.forEach { (mode, label) -> ChoiceRow(stringResource(label), state.input == mode) { actions.onInput(mode) } }
                }
                SectionTitle(R.string.settings_section_feelings)
                NavigationRow(stringResource(R.string.feelings_title), actions.onManageFeelings)
                Section(R.string.settings_section_appearance) {
                    THEMES.forEach { (mode, label) -> ChoiceRow(stringResource(label), state.theme == mode) { actions.onTheme(mode) } }
                }
                if (showLanguage) {
                    Section(R.string.settings_section_language) {
                        LANGUAGES.forEach { (tag, label) -> ChoiceRow(stringResource(label), language == tag) { actions.onLanguage(tag) } }
                    }
                }
                SectionTitle(R.string.settings_section_privacy)
                SwitchRow(stringResource(R.string.backup_switch), state.backupEnabled, actions.onBackup)
                Text(
                    stringResource(R.string.backup_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun Section(
    @StringRes title: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionTitle(title)
    Column(Modifier.selectableGroup(), content = content)
}

@Composable
private fun SectionTitle(
    @StringRes title: Int,
) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
internal fun ChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row handles the tap; the button only shows the state.
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.size(12.dp))
        val color = MaterialTheme.colorScheme.onSurface
        Text(label, color = if (enabled) color else color.copy(alpha = DISABLED_TEXT_ALPHA))
    }
}

/** An on/off row; the whole row is the touch target. */
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        // The row handles the tap; the switch only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A row that opens another screen. */
@Composable
private fun NavigationRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

internal const val DISABLED_TEXT_ALPHA = 0.38f

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
