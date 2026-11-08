package com.wolf.feelingsdiary.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wolf.feelingsdiary.R
import kotlin.math.roundToInt

/** The 12 fixed extra colours offered for custom feelings. */
val CustomFeelingColors: List<Int> =
    listOf(
        0xFF7E57C2,
        0xFF26A69A,
        0xFFEC407A,
        0xFF8D6E63,
        0xFF42A5F5,
        0xFF66BB6A,
        0xFFFFA726,
        0xFF78909C,
        0xFFD4E157,
        0xFFAB47BC,
        0xFF29B6F6,
        0xFFFF7043,
    ).map { it.toInt() }

private const val VALENCE_MIN = -2
private const val VALENCE_MAX = 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewFeelingDialog(
    initialName: String,
    onConfirm: (name: String, colorArgb: Int, valence: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    var valence by rememberSaveable { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_feeling_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.new_feeling_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.new_feeling_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CustomFeelingColors.forEachIndexed { index, argb ->
                        val selected = index == colorIndex
                        val cd =
                            if (selected) {
                                stringResource(R.string.new_feeling_color_selected_cd, index + 1)
                            } else {
                                stringResource(R.string.new_feeling_color_cd, index + 1)
                            }
                        Spacer(
                            Modifier
                                .size(36.dp)
                                .background(Color(argb), CircleShape)
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .semantics { contentDescription = cd }
                                .clickable(role = Role.RadioButton) { colorIndex = index },
                        )
                    }
                }
                Text(stringResource(R.string.new_feeling_valence), style = MaterialTheme.typography.labelLarge)
                val valenceCd = stringResource(R.string.new_feeling_valence_cd, valence)
                Slider(
                    value = valence.toFloat(),
                    onValueChange = { valence = it.roundToInt() },
                    valueRange = VALENCE_MIN.toFloat()..VALENCE_MAX.toFloat(),
                    steps = VALENCE_MAX - VALENCE_MIN - 1,
                    modifier = Modifier.semantics { contentDescription = valenceCd },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.valence_bad), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.valence_neutral), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.valence_good), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), CustomFeelingColors[colorIndex], valence) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
