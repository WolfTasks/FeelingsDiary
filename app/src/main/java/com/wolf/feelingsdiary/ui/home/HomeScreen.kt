package com.wolf.feelingsdiary.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.GraphPoint
import com.wolf.feelingsdiary.domain.PeriodKind
import java.time.LocalDate

data class HomeActions(
    val onAdd: () -> Unit,
    val onKind: (PeriodKind) -> Unit,
    val onRange: (LocalDate, LocalDate) -> Unit,
    val onShift: (Int) -> Unit,
    val onPoint: (GraphPoint) -> Unit,
    val onRow: (Long) -> Unit,
    val onEdit: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
)

@Composable
fun HomeRoute(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val actions =
        HomeActions(
            onAdd = onAdd,
            onKind = vm::selectKind,
            onRange = vm::selectRange,
            onShift = vm::shift,
            onPoint = vm::openBucket,
            onRow = {},
            onEdit = onEdit,
            onDelete = vm::delete,
        )
    HomeScreen(state, actions)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val openRow = state.rows.firstOrNull { it.id == openId }
    // The entry may vanish (deleted, or another period loaded): forget its id so the sheet stays closed.
    LaunchedEffect(openRow == null) { if (openRow == null) openId = null }

    fun open(id: Long) {
        openId = id
        actions.onRow(id)
    }

    val colorOf = { id: Long -> state.colors[id]?.let { Color(it) } ?: Color.Unspecified }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Edge-to-edge: keep content clear of status bar, navigation bar and display cutouts.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            item { PeriodBar(state.period, locale, actions.onKind, actions.onRange, actions.onShift) }
            item {
                HistoryGraph(
                    data = state.graph,
                    colorOf = colorOf,
                    xLabels = xAxisLabels(state.period, locale),
                    onPointTap = { point -> point.entryId?.let(::open) ?: actions.onPoint(point) },
                )
            }
            item { GraphLegend(state.legend.map { (label, argb) -> label to Color(argb) }) }
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(state.rows, key = { it.id }) { EntryRowCard(it, locale) { open(it.id) } }
            }
        }
        Button(
            onClick = actions.onAdd,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text(stringResource(R.string.home_add))
        }
    }
    if (openRow != null) {
        EntryDetailSheet(
            row = openRow,
            locale = locale,
            onDismiss = { openId = null },
            onEdit = {
                openId = null
                actions.onEdit(openRow.id)
            },
            onDelete = {
                openId = null
                actions.onDelete(openRow.id)
            },
        )
    }
}

@Composable
private fun EntryRowCard(
    row: HomeEntryRow,
    locale: java.util.Locale,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            formatEntryTime(row.time, locale),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FeelingList(row.feelings)
        Spacer(Modifier.height(6.dp))
        PeopleText(row.people)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeelingList(feelings: List<Pair<String, Int>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        feelings.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(10.dp).clip(CircleShape).background(Color(color)))
                Spacer(Modifier.size(6.dp))
                Text(label, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
internal fun PeopleText(people: Int) {
    val text =
        if (people == 0) {
            stringResource(R.string.entry_people_alone)
        } else {
            pluralStringResource(R.plurals.home_people, people, people)
        }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
