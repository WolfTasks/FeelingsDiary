package com.wolf.feelingsdiary.ui.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wolf.feelingsdiary.R

/** Selectable feeling chip: colour dot when idle, filled with the feeling colour when selected. */
@Composable
fun FeelingChip(
    label: String,
    color: Color,
    selected: Boolean,
    isMain: Boolean,
    onClick: () -> Unit,
) {
    val description =
        when {
            isMain -> stringResource(R.string.chip_cd_main, label)
            selected -> stringResource(R.string.chip_cd_selected, label)
            else -> stringResource(R.string.chip_cd_unselected, label)
        }
    val onColor = if (color.luminance() > LIGHT_THRESHOLD) Color.Black else Color.White
    Surface(
        shape = RoundedCornerShape(CHIP_RADIUS),
        color = if (selected) color else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) onColor else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) color else MaterialTheme.colorScheme.surfaceVariant),
        modifier =
            Modifier
                .semantics(mergeDescendants = true) { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!selected) {
                Spacer(Modifier.size(10.dp).background(color, CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (isMain) {
                Spacer(Modifier.width(6.dp))
                Text("1", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val CHIP_RADIUS = 20.dp
private const val LIGHT_THRESHOLD = 0.5f
