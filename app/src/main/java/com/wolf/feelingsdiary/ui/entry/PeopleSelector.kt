package com.wolf.feelingsdiary.ui.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wolf.feelingsdiary.R

private const val MAX_QUICK = 5

/** Quick buttons alone, 1..5 and 6+; 6+ opens [PeopleNumberDialog]. */
@Composable
fun PeopleSelector(
    people: Int?,
    onPeople: (Int) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
        for (n in 0..MAX_QUICK) {
            val text = if (n == 0) stringResource(R.string.entry_people_alone) else n.toString()
            val cd =
                if (n == 0) {
                    stringResource(R.string.entry_people_alone_cd)
                } else {
                    stringResource(R.string.entry_people_count_cd, n)
                }
            // "alone" takes the width its label needs (any language, large font); the numbers share the rest.
            if (n == 0) {
                PeopleButton(
                    text,
                    cd,
                    people == n,
                    Modifier.widthIn(min = MIN_BUTTON_WIDTH),
                    labelPadding = ALONE_LABEL_PADDING,
                ) { onPeople(n) }
            } else {
                PeopleButton(text, cd, people == n, Modifier.weight(1f)) { onPeople(n) }
            }
        }
        PeopleButton(
            stringResource(R.string.entry_people_more),
            stringResource(R.string.entry_people_more_hint),
            people != null && people > MAX_QUICK,
            Modifier.weight(1f),
        ) { showDialog = true }
    }
    if (showDialog) {
        PeopleNumberDialog(
            onConfirm = {
                showDialog = false
                onPeople(it)
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun PeopleButton(
    text: String,
    description: String,
    selected: Boolean,
    modifier: Modifier,
    labelPadding: Dp = 0.dp,
    onClick: () -> Unit,
) {
    // Custom button: Material buttons enforce a minimum width that overflows six across on narrow screens.
    Surface(
        shape = RoundedCornerShape(BUTTON_RADIUS),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = labelPadding), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
}

private val BUTTON_RADIUS = 12.dp
private val MIN_BUTTON_WIDTH = 48.dp
private val ALONE_LABEL_PADDING = 12.dp
private val BUTTON_GAP = 4.dp
