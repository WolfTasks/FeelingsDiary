package com.wolf.feelingsdiary.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wolf.feelingsdiary.R

@Composable
fun HomeRoute(
    onAdd: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    HomeScreen(rows, onAdd)
}

@Composable
fun HomeScreen(
    entries: List<HomeEntryRow>,
    onAdd: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Edge-to-edge: keep content clear of status bar, navigation bar and display cutouts.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.id }) { EntryRowCard(it) }
        }
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text(stringResource(R.string.home_add))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryRowCard(row: HomeEntryRow) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Text(row.time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            row.feelings.forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(10.dp).clip(CircleShape).background(Color(color)))
                    Spacer(Modifier.size(6.dp))
                    Text(label, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val people =
            if (row.people == 0) {
                stringResource(R.string.entry_people_alone)
            } else {
                pluralStringResource(R.plurals.home_people, row.people, row.people)
            }
        Text(people, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
