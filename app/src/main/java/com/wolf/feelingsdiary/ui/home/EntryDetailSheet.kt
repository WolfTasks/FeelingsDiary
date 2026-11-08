package com.wolf.feelingsdiary.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wolf.feelingsdiary.R
import java.util.Locale

/** Details of one entry; Delete asks for confirmation before calling [onDelete]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailSheet(
    row: HomeEntryRow,
    locale: Locale,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(formatEntryTime(row.time, locale), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            FeelingList(row.feelings)
            Spacer(Modifier.height(8.dp))
            PeopleText(row.people)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { confirming = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.detail_delete))
                }
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.detail_edit))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDelete()
                }) { Text(stringResource(R.string.delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}
