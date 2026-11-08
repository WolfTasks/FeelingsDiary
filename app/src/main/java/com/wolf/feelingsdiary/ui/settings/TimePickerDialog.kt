package com.wolf.feelingsdiary.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wolf.feelingsdiary.R
import java.time.LocalTime

const val TIME_DIALOG_MODE_TAG = "timeDialogMode"

/**
 * Material 3 time picker in a dialog, with the standard dial/keyboard toggle.
 * [validate] returns an error text for an invalid time; OK then shows it and keeps the dialog open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(
    title: String,
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    validate: (LocalTime) -> String? = { null },
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour)
    var mode by remember { mutableStateOf(TimePickerDisplayMode.Picker) }
    // Once OK was pressed on an invalid time, the error follows the current pick until it is valid.
    var tried by remember { mutableStateOf(false) }
    val picked = LocalTime.of(state.hour, state.minute)
    val error = if (tried) validate(picked) else null
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 20.dp)) },
        confirmButton = {
            TextButton(onClick = {
                if (validate(picked) == null) onConfirm(picked) else tried = true
            }) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    mode = if (mode == TimePickerDisplayMode.Picker) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker
                },
                displayMode = mode,
                modifier = Modifier.testTag(TIME_DIALOG_MODE_TAG),
            )
        },
    ) {
        if (mode == TimePickerDisplayMode.Picker) TimePicker(state) else TimeInput(state)
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
