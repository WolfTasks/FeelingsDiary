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
import androidx.compose.runtime.remember
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

/** Values of a feeling being edited; [name] is the displayed (localized) name. */
data class FeelingDraft(
    val name: String,
    val colorArgb: Int,
    val valence: Int,
)

/**
 * Creates ([initial] null, prefilled with [initialName]) or edits a feeling. Base feelings keep their
 * name ([nameEditable] false). A starting colour outside the palette is offered as the first swatch.
 * [error] is shown under the name until the name is changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeelingEditDialog(
    initial: FeelingDraft?,
    nameEditable: Boolean,
    error: String?,
    onConfirm: (name: String, colorArgb: Int, valence: Int) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: initialName) }
    var color by rememberSaveable { mutableIntStateOf(initial?.colorArgb ?: CustomFeelingColors.first()) }
    var valence by rememberSaveable { mutableIntStateOf(initial?.valence ?: 0) }
    // Hidden once the name changes, shown again with the next confirm.
    var showError by rememberSaveable { mutableStateOf(true) }
    val palette =
        remember(initial) {
            initial?.colorArgb?.takeIf { it !in CustomFeelingColors }?.let { listOf(it) + CustomFeelingColors }
                ?: CustomFeelingColors
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_feeling_title else R.string.feeling_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val visibleError = error?.takeIf { showError }
                if (nameEditable) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            showError = false
                        },
                        label = { Text(stringResource(R.string.new_feeling_name)) },
                        singleLine = true,
                        isError = visibleError != null,
                        supportingText = visibleError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(R.string.new_feeling_color), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    palette.forEachIndexed { index, argb ->
                        val selected = argb == color
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
                                .clickable(role = Role.RadioButton) { color = argb },
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
                onClick = {
                    showError = true
                    onConfirm(name.trim(), color, valence)
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
