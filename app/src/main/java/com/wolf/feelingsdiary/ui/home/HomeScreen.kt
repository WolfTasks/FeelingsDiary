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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.wolf.feelingsdiary.domain.InputMode
import com.wolf.feelingsdiary.domain.PeriodKind
import com.wolf.feelingsdiary.ui.entry.QuickEntryRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    val onSettings: () -> Unit,
)

@Composable
fun HomeRoute(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
    sheetRequests: Flow<Unit> = emptyFlow(),
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
            onSettings = onSettings,
        )
    HomeScreen(state, actions, sheetRequests) { key, onDone -> QuickEntryRoute(key, onDone) }
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    sheetRequests: Flow<Unit> = emptyFlow(),
    quickSheet: @Composable (key: String, onDone: () -> Unit) -> Unit = { _, _ -> },
) {
    val locale = LocalConfiguration.current.locales[0]
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Each opening gets its own key and with it a fresh entry ViewModel.
    var quickOpenings by rememberSaveable { mutableIntStateOf(0) }
    var quickOpen by rememberSaveable { mutableStateOf(false) }

    fun openQuick() {
        // A request while the sheet is open keeps the running entry.
        if (quickOpen) return
        openId = null
        quickOpenings++
        quickOpen = true
    }
    LaunchedEffect(sheetRequests) { sheetRequests.collect { openQuick() } }
    val openRow = state.rows.firstOrNull { it.id == openId }
    // The entry may vanish (deleted, or another period loaded): forget its id so the sheet stays closed.
    LaunchedEffect(openRow == null) { if (openRow == null) openId = null }

    fun open(id: Long) {
        openId = id
        actions.onRow(id)
    }

    val colorOf = { id: Long -> state.colors[id]?.let { Color(it) } ?: Color.Unspecified }
    // Surface provides the content color; bare Text would fall back to black in dark theme.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                // Edge-to-edge: keep content clear of status bar, navigation bar and display cutouts.
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.history_title),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f).padding(vertical = 16.dp),
                        )
                        IconButton(onClick = actions.onSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_open_cd))
                        }
                    }
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
                if (state.rows.isEmpty() && !state.loading) {
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
                onClick = { if (state.inputMode == InputMode.QUICK_SHEET) openQuick() else actions.onAdd() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                Text(stringResource(R.string.home_add))
            }
        }
    }
    if (quickOpen) quickSheet("quick-$quickOpenings") { quickOpen = false }
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
