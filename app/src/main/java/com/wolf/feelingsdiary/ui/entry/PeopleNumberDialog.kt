package com.wolf.feelingsdiary.ui.entry

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.wolf.feelingsdiary.R

private val VALID_RANGE = 6..999
private const val MAX_DIGITS = 4

/** Number field for 6..999 other people; OK is enabled only for valid input. */
@Composable
fun PeopleNumberDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val value = text.toIntOrNull()?.takeIf { it in VALID_RANGE }
    val invalid = text.isNotEmpty() && value == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.people_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(MAX_DIGITS) },
                label = { Text(stringResource(R.string.people_dialog_label)) },
                isError = invalid,
                supportingText = if (invalid) ({ Text(stringResource(R.string.people_dialog_error)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
