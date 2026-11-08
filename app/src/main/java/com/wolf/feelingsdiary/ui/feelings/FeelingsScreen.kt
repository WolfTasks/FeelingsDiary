package com.wolf.feelingsdiary.ui.feelings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.ui.entry.FeelingDraft
import com.wolf.feelingsdiary.ui.entry.FeelingEditDialog
import com.wolf.feelingsdiary.ui.settings.DISABLED_TEXT_ALPHA

data class FeelingsActions(
    val onBack: () -> Unit,
    /** Returns false when the name is taken; the dialog then stays open with an error. */
    val onSave: (id: Long, name: String, colorArgb: Int, valence: Int) -> Boolean,
    val onToggleHidden: (Long) -> Unit,
    /** Returns false when the name is taken; the dialog then stays open with an error. */
    val onAdd: (name: String, colorArgb: Int, valence: Int) -> Boolean,
)

@Composable
fun FeelingsRoute(
    onBack: () -> Unit,
    vm: FeelingsViewModel = hiltViewModel(),
) {
    val rows by vm.state.collectAsStateWithLifecycle()
    FeelingsScreen(rows, FeelingsActions(onBack, vm::save, vm::toggleHidden, vm::add))
}

private const val NEW_FEELING = -1L

@Composable
fun FeelingsScreen(
    rows: List<FeelingRow>,
    actions: FeelingsActions,
) {
    // Id of the feeling in the dialog, NEW_FEELING for a new one, null when closed.
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    var nameTaken by rememberSaveable { mutableStateOf(false) }
    val visibleCount = rows.count { !it.feeling.hidden }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back_cd))
                }
                Text(stringResource(R.string.feelings_title), style = MaterialTheme.typography.titleMedium)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(rows, key = { it.feeling.id }) { row ->
                    FeelingRowItem(
                        row = row,
                        // The last visible feeling stays: the entry screen needs at least one.
                        canHide = visibleCount > 1,
                        onClick = {
                            nameTaken = false
                            editing = row.feeling.id
                        },
                        onToggleHidden = { actions.onToggleHidden(row.feeling.id) },
                    )
                }
            }
            Button(
                onClick = {
                    nameTaken = false
                    editing = NEW_FEELING
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 8.dp),
            ) { Text(stringResource(R.string.feelings_add)) }
        }
    }

    val current = editing
    val row = rows.firstOrNull { it.feeling.id == current }
    if (current != null && (current == NEW_FEELING || row != null)) {
        val error = if (nameTaken) stringResource(R.string.feeling_name_exists) else null
        val close = {
            editing = null
            nameTaken = false
        }
        FeelingEditDialog(
            initial = row?.let { FeelingDraft(it.label, it.feeling.colorArgb, it.feeling.valence) },
            nameEditable = row == null || row.feeling.baseKey == null,
            error = error,
            onConfirm = { name, color, valence ->
                val ok = if (row == null) actions.onAdd(name, color, valence) else actions.onSave(current, name, color, valence)
                if (ok) close() else nameTaken = true
            },
            onDismiss = close,
        )
    }
}

@Composable
private fun FeelingRowItem(
    row: FeelingRow,
    canHide: Boolean,
    onClick: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    val hidden = row.feeling.hidden
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textColor = if (hidden) onSurface.copy(alpha = DISABLED_TEXT_ALPHA) else onSurface
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // clickable merges dot and texts into one button node; the eye stays a separate button.
        Row(
            Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.size(20.dp).background(Color(row.feeling.colorArgb), CircleShape))
            Column {
                Text(row.label, color = textColor, style = MaterialTheme.typography.bodyLarge)
                Text(valenceText(row.feeling.valence, hidden), color = textColor, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = onToggleHidden, enabled = hidden || canHide) {
            Icon(
                if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = stringResource(if (hidden) R.string.feelings_show_cd else R.string.feelings_hide_cd),
            )
        }
    }
}

/** For example "good (+2)", or "good (+2) · hidden" for a hidden feeling. */
@Composable
private fun valenceText(
    valence: Int,
    hidden: Boolean,
): String {
    val word =
        stringResource(
            when {
                valence > 0 -> R.string.valence_good
                valence < 0 -> R.string.valence_bad
                else -> R.string.valence_neutral
            },
        )
    val text = stringResource(R.string.feelings_valence, word, if (valence > 0) "+$valence" else valence.toString())
    return if (hidden) stringResource(R.string.feelings_hidden, text) else text
}
