package com.wolf.feelingsdiary.ui.settings

import androidx.annotation.StringRes
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.ThemeMode

/** Later sections (reminders, input, feelings, privacy) add their callbacks here. */
data class SettingsActions(
    val onBack: () -> Unit,
    val onTheme: (ThemeMode) -> Unit,
    val onLanguage: (String?) -> Unit,
)

private val THEMES =
    listOf(
        ThemeMode.DARK to R.string.theme_dark,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.SYSTEM to R.string.theme_system,
    )

// null = follow the system language. Language names stay in their own language in every UI language.
private val LANGUAGES = listOf(null to R.string.language_system, "de" to R.string.language_de, "en" to R.string.language_en)

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Not part of AppSettings: the system stores the per-app language. A change recreates the activity.
    var language by remember { mutableStateOf(appLanguage(context)) }
    val actions =
        SettingsActions(
            onBack = onBack,
            onTheme = vm::setTheme,
            onLanguage = { tag ->
                language = tag
                setAppLanguage(context, tag)
            },
        )
    SettingsScreen(state, actions, language)
}

/**
 * Section order (spec): Reminders, Input, Manage feelings, Appearance, Language (API 33+), Privacy.
 * Reminders, Input, Manage feelings and Privacy follow in later steps and slot in at their place below.
 */
@Composable
fun SettingsScreen(
    state: AppSettings,
    actions: SettingsActions,
    language: String?,
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
                // Reminders, Input and Manage feelings go here.
                Section(R.string.settings_section_appearance) {
                    THEMES.forEach { (mode, label) -> ChoiceRow(stringResource(label), state.theme == mode) { actions.onTheme(mode) } }
                }
                if (showLanguage) {
                    Section(R.string.settings_section_language) {
                        LANGUAGES.forEach { (tag, label) -> ChoiceRow(stringResource(label), language == tag) { actions.onLanguage(tag) } }
                    }
                }
                // Privacy goes here.
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
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    Column(Modifier.selectableGroup(), content = content)
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row handles the tap; the button only shows the state.
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(12.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}
