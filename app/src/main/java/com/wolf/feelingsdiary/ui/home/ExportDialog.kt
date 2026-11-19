package com.wolf.feelingsdiary.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.wolf.feelingsdiary.R

/** Lets the user pick CSV and/or graph image for the shown period; sharing needs at least one. */
@Composable
fun ExportDialog(
    periodText: String,
    onDismiss: () -> Unit,
    onShare: (csv: Boolean, graph: Boolean) -> Unit,
) {
    var csv by rememberSaveable { mutableStateOf(true) }
    var graph by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column {
                Text(periodText, style = MaterialTheme.typography.bodyMedium)
                Option(stringResource(R.string.export_option_csv), csv) { csv = it }
                Option(stringResource(R.string.export_option_graph), graph) { graph = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onShare(csv, graph) }, enabled = csv || graph) {
                Text(stringResource(R.string.export_share))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_cancel)) } },
    )
}

@Composable
private fun Option(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}
