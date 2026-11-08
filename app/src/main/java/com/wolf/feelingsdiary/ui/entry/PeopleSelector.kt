package com.wolf.feelingsdiary.ui.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
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
    val aloneText = stringResource(R.string.entry_people_alone)
    val aloneCd = stringResource(R.string.entry_people_alone_cd)
    val moreText = stringResource(R.string.entry_people_more)
    val moreCd = stringResource(R.string.entry_people_more_hint)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val aloneWidth =
            with(density) {
                textMeasurer
                    .measure(aloneText, labelStyle, softWrap = false)
                    .size.width
                    .toDp()
            } +
                ALONE_LABEL_PADDING * 2
        val fitsOneRow = aloneWidth + MIN_BUTTON_WIDTH * 6 + BUTTON_GAP * 6 <= maxWidth

        @Composable
        fun RowScope.alone() =
            PeopleButton(
                aloneText,
                aloneCd,
                people == 0,
                Modifier.widthIn(min = MIN_BUTTON_WIDTH),
                labelPadding = ALONE_LABEL_PADDING,
            ) { onPeople(0) }

        @Composable
        fun RowScope.number(n: Int) =
            PeopleButton(
                n.toString(),
                stringResource(R.string.entry_people_count_cd, n),
                people == n,
                Modifier.weight(1f),
            ) { onPeople(n) }

        @Composable
        fun RowScope.more() =
            PeopleButton(moreText, moreCd, people != null && people > MAX_QUICK, Modifier.weight(1f)) {
                showDialog = true
            }

        if (fitsOneRow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                // "alone" takes the width its label needs (any language, large font); the numbers share the rest.
                alone()
                for (n in 1..MAX_QUICK) number(n)
                more()
            }
        } else {
            // Too narrow for 48dp targets in one row: wrap, "alone" 1 2 3 / 4 5 6+.
            Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                    alone()
                    for (n in 1..3) number(n)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
                    number(4)
                    number(5)
                    more()
                }
            }
        }
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
