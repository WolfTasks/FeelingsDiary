package com.wolf.feelingsdiary.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.Feeling
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class EntryActions(
    val onQuery: (String) -> Unit,
    val onToggle: (Long) -> Unit,
    val onPeople: (Int) -> Unit,
    val onCreate: (String, Int, Int) -> Unit,
    val onSave: () -> Unit,
)

@Composable
fun EntryRoute(
    onDone: () -> Unit,
    vm: EntryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val actions = EntryActions(vm::onQueryChange, vm::toggle, vm::setPeople, vm::createFeeling, vm::save)
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    val time = rememberSaveable { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    EntryScreen(state, actions, onClose = onDone, label = vm.labeler::label, time = time)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    state: EntryUiState,
    actions: EntryActions,
    onClose: () -> Unit,
    label: (Feeling) -> String,
    time: String,
) {
    var showCreate by rememberSaveable { mutableStateOf(false) }
    // Guards against a double tap on Save; the screen is left once saving completes.
    var saveTapped by remember { mutableStateOf(false) }

    @Composable
    fun Chips(feelings: List<Feeling>) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (f in feelings) {
                val index = state.selectedIds.indexOf(f.id)
                FeelingChip(label(f), Color(f.colorArgb), index >= 0, index == 0) { actions.onToggle(f.id) }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // safeDrawing includes the keyboard: Save stays above it while the content above scrolls.
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.entry_close))
                }
                Text(stringResource(R.string.entry_title, time), style = MaterialTheme.typography.titleMedium)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.entry_question), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions.onQuery,
                    placeholder = { Text(stringResource(R.string.entry_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.query.isNotBlank()) {
                    Text(stringResource(R.string.entry_section_suggestions), style = MaterialTheme.typography.labelLarge)
                    Chips(state.suggestions.others)
                    if (state.suggestions.canCreate) {
                        OutlinedButton(onClick = { showCreate = true }) {
                            Text(stringResource(R.string.entry_create_offer, state.query.trim()))
                        }
                    }
                } else {
                    if (state.suggestions.recent.isNotEmpty()) {
                        Text(stringResource(R.string.entry_section_recent), style = MaterialTheme.typography.labelLarge)
                        Chips(state.suggestions.recent)
                    }
                    Text(stringResource(R.string.entry_section_all), style = MaterialTheme.typography.labelLarge)
                    Chips(state.suggestions.others)
                }
                Text(stringResource(R.string.entry_people_question), style = MaterialTheme.typography.labelLarge)
                PeopleSelector(state.people, actions.onPeople)
            }
            Button(
                onClick = {
                    if (!saveTapped) {
                        saveTapped = true
                        actions.onSave()
                    }
                },
                enabled = state.canSave && !saveTapped,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 8.dp),
            ) { Text(stringResource(R.string.entry_save)) }
        }
    }
    if (showCreate) {
        NewFeelingDialog(
            initialName = state.query.trim(),
            onConfirm = { name, color, valence ->
                showCreate = false
                actions.onCreate(name, color, valence)
            },
            onDismiss = { showCreate = false },
        )
    }
}
