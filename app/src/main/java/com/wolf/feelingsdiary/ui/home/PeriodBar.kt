package com.wolf.feelingsdiary.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.GraphPeriod
import com.wolf.feelingsdiary.domain.PeriodKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

private val CHIP_KINDS =
    listOf(
        PeriodKind.YEAR to R.string.period_year,
        PeriodKind.MONTH to R.string.period_month,
        PeriodKind.WEEK to R.string.period_week,
        PeriodKind.DAY to R.string.period_day,
    )

@Composable
fun PeriodBar(
    period: GraphPeriod,
    locale: Locale,
    onKind: (PeriodKind) -> Unit,
    onRange: (LocalDate, LocalDate) -> Unit,
    onShift: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickRange by rememberSaveable { mutableStateOf(false) }
    val rangeCd = stringResource(R.string.period_range_cd)
    val prevCd = stringResource(R.string.period_prev_cd)
    val nextCd = stringResource(R.string.period_next_cd)
    Column(modifier) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CHIP_KINDS.forEach { (kind, label) ->
                FilterChip(
                    selected = period.kind == kind,
                    onClick = { onKind(kind) },
                    label = { Text(stringResource(label)) },
                )
            }
            IconButton(onClick = { pickRange = true }, modifier = Modifier.semantics { contentDescription = rangeCd }) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }, modifier = Modifier.semantics { contentDescription = prevCd }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
            }
            Text(
                formatPeriod(period, locale),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onShift(1) }, modifier = Modifier.semantics { contentDescription = nextCd }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
    if (pickRange) {
        RangePickerDialog(onDismiss = { pickRange = false }, onConfirm = { a, b ->
            pickRange = false
            onRange(a, b)
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val state = rememberDateRangePickerState()
    val start = state.selectedStartDateMillis
    val end = state.selectedEndDateMillis ?: start
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null,
                onClick = { if (start != null && end != null) onConfirm(utcDate(start), utcDate(end)) },
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    ) {
        DateRangePicker(state)
    }
}

// The picker reports UTC midnight of the chosen calendar day.
private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
