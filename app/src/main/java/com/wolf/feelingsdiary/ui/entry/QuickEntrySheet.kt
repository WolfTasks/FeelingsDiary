package com.wolf.feelingsdiary.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.Feeling

private const val MAX_PEOPLE = 999

/**
 * Input variant A over the history. [key] must change per opening: it selects a fresh
 * [EntryViewModel], so a reopened sheet never shows the previous selection.
 */
@Composable
fun QuickEntryRoute(
    key: String,
    onDone: () -> Unit,
) {
    val vm: EntryViewModel = hiltViewModel(key = key)
    val state by vm.state.collectAsStateWithLifecycle()
    // No-op once people are set, e.g. after rotation.
    LaunchedEffect(vm) { vm.prefillPeopleFromLastEntry() }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    val actions = EntryActions(vm::onQueryChange, vm::toggle, vm::setPeople, vm::createFeeling, vm::save)
    QuickEntrySheet(state, actions, onDismiss = onDone, label = vm.labeler::label)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEntrySheet(
    state: EntryUiState,
    actions: EntryActions,
    onDismiss: () -> Unit,
    label: (Feeling) -> String,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    val searching = searchOpen && state.query.isNotBlank()
    // Shown until the prefill lookup returns; saving in that moment stores what is shown.
    val people = state.people ?: 0

    @Composable
    fun Chips(feelings: List<Feeling>) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (feeling in feelings) {
                val index = state.selectedIds.indexOf(feeling.id)
                FeelingChip(label(feeling), Color(feeling.colorArgb), index >= 0, index == 0) { actions.onToggle(feeling.id) }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.entry_question),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        // Closing the search also drops its text, so the chips are back.
                        if (searchOpen) actions.onQuery("")
                        searchOpen = !searchOpen
                    }) {
                        if (searchOpen) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.quick_search_close_cd))
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.quick_search_cd))
                        }
                    }
                }
                if (searchOpen) {
                    val focus = remember { FocusRequester() }
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = actions.onQuery,
                        placeholder = { Text(stringResource(R.string.entry_search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    LaunchedEffect(Unit) { focus.requestFocus() }
                }
                if (searching) {
                    Text(stringResource(R.string.entry_section_suggestions), style = MaterialTheme.typography.labelLarge)
                    Chips(state.suggestions.others)
                    if (state.suggestions.canCreate) {
                        OutlinedButton(onClick = { showCreate = true }) {
                            Text(stringResource(R.string.entry_create_offer, state.query.trim()))
                        }
                    }
                } else {
                    Chips(state.quick)
                }
                PeopleStepper(people, actions.onPeople)
            }
            Button(
                onClick = {
                    if (state.people == null) actions.onPeople(people)
                    actions.onSave()
                },
                enabled = state.selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 8.dp),
            ) { Text(stringResource(R.string.entry_save)) }
        }
    }
    if (showCreate) {
        // Same flow as the entry screen: a duplicate name selects the existing feeling.
        FeelingEditDialog(
            initial = null,
            nameEditable = true,
            error = null,
            onConfirm = { name, color, valence ->
                showCreate = false
                actions.onCreate(name, color, valence)
            },
            onDismiss = { showCreate = false },
            initialName = state.query.trim(),
        )
    }
}

/** "👥 Other people  − value +", 0..999; 0 reads "alone". */
@Composable
private fun PeopleStepper(
    people: Int,
    onPeople: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.quick_people), Modifier.weight(1f))
            FilledTonalIconButton(onClick = { onPeople(people - 1) }, enabled = people > 0) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.quick_people_less_cd))
            }
            Text(
                if (people == 0) stringResource(R.string.entry_people_alone) else people.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 56.dp),
            )
            FilledTonalIconButton(onClick = { onPeople(people + 1) }, enabled = people < MAX_PEOPLE) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.quick_people_more_cd))
            }
        }
    }
}
