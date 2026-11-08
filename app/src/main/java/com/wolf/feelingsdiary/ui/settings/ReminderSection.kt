package com.wolf.feelingsdiary.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.ReminderMode
import com.wolf.feelingsdiary.domain.ReminderPlanner
import com.wolf.feelingsdiary.domain.ReminderSettings
import com.wolf.feelingsdiary.domain.TimeWindow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Names of the four windows / fixed times, by index. */
private val WINDOW_NAMES =
    listOf(
        R.string.reminder_window_morning,
        R.string.reminder_window_midday,
        R.string.reminder_window_afternoon,
        R.string.reminder_window_evening,
    )

fun formatTime(
    time: LocalTime,
    locale: Locale,
): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)

/** Which time the open dialog edits; a window takes two steps, start then end. */
private sealed interface Edit {
    data class WindowStart(
        val index: Int,
    ) : Edit

    data class WindowEnd(
        val index: Int,
        val start: LocalTime,
    ) : Edit

    data class Fixed(
        val index: Int,
    ) : Edit

    data object QuietStart : Edit

    data class QuietEnd(
        val start: LocalTime,
    ) : Edit
}

/**
 * Reminders section: on/off, count, mode, the active windows or fixed times, the quiet hours and the accuracy.
 * Every change goes out as a transform of the current settings, so it never overwrites a concurrent change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSection(
    s: ReminderSettings,
    notificationsAllowed: Boolean,
    onChange: ((ReminderSettings) -> ReminderSettings) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val fmt = { time: LocalTime -> formatTime(time, locale) }
    var edit by remember { mutableStateOf<Edit?>(null) }
    val allow = rememberAskOnce(onRequestPermission, onOpenSystemSettings)

    Column {
        if (s.enabled && !notificationsAllowed) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reminder_notifications_off),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = allow) { Text(stringResource(R.string.reminder_allow)) }
            }
        }
        SwitchRow(stringResource(R.string.reminder_switch), s.enabled) { on -> onChange { it.copy(enabled = on) } }
        SwitchRow(
            stringResource(R.string.reminder_alarm_sound),
            s.alarmSound,
            enabled = s.enabled,
            hint = stringResource(R.string.reminder_alarm_sound_hint),
        ) { on -> onChange { it.copy(alarmSound = on) } }

        ReminderCountRow(s.count, s.enabled) { perDay -> onChange { it.copy(count = perDay) } }

        Column(Modifier.selectableGroup().padding(top = 8.dp)) {
            ChoiceRow(stringResource(R.string.reminder_mode_random), s.mode == ReminderMode.RANDOM, s.enabled) {
                onChange { it.copy(mode = ReminderMode.RANDOM) }
            }
            ChoiceRow(stringResource(R.string.reminder_mode_fixed), s.mode == ReminderMode.FIXED, s.enabled) {
                onChange { it.copy(mode = ReminderMode.FIXED) }
            }
        }

        ReminderPlanner.activeIndices(s.count).forEach { i ->
            val name = stringResource(WINDOW_NAMES[i])
            if (s.mode == ReminderMode.RANDOM) {
                val w = s.windows[i]
                TimeRow(
                    name,
                    stringResource(R.string.reminder_time_range, fmt(w.start), fmt(w.end)),
                    ReminderPlanner.isFullyQuiet(w, s),
                    s.enabled,
                ) { edit = Edit.WindowStart(i) }
            } else {
                val time = s.fixedTimes[i]
                TimeRow(name, fmt(time), ReminderPlanner.isQuiet(time, s), s.enabled) { edit = Edit.Fixed(i) }
            }
        }
        TimeRow(
            stringResource(R.string.reminder_quiet_hours),
            stringResource(R.string.reminder_time_range, fmt(s.quietStart), fmt(s.quietEnd)),
            quietHint = false,
            enabled = s.enabled,
        ) { edit = Edit.QuietStart }

        AccuracyGroup(s.maxDelayMinutes, s.enabled) { minutes -> onChange { it.copy(maxDelayMinutes = minutes) } }
    }

    edit?.let { current -> EditDialog(current, s, onChange, onStep = { edit = it }) }
}

/** Labels of the accuracy options, by maximum delay in minutes ([ReminderSettings.MAX_DELAY_OPTIONS]). */
private val ACCURACY_LABELS =
    mapOf(
        5 to R.string.reminder_accuracy_5,
        15 to R.string.reminder_accuracy_15,
        60 to R.string.reminder_accuracy_60,
    )

/** How late a reminder may arrive at most; finer means more (silent) wake-ups. */
@Composable
private fun AccuracyGroup(
    maxDelayMinutes: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Text(
        stringResource(R.string.reminder_accuracy),
        color = rowColor(enabled),
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        stringResource(R.string.reminder_accuracy_hint),
        style = MaterialTheme.typography.bodySmall,
        color = rowColor(enabled),
    )
    Column(Modifier.selectableGroup()) {
        ReminderSettings.MAX_DELAY_OPTIONS.forEach { minutes ->
            ChoiceRow(stringResource(ACCURACY_LABELS.getValue(minutes)), maxDelayMinutes == minutes, enabled) {
                onSelect(minutes)
            }
        }
    }
}

/** The label and the 1–4 buttons for the number of reminders per day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderCountRow(
    count: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Text(
        stringResource(R.string.reminder_count),
        color = rowColor(enabled),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        (1..4).forEach { perDay ->
            SegmentedButton(
                selected = count == perDay,
                onClick = { onSelect(perDay) },
                shape = SegmentedButtonDefaults.itemShape(index = perDay - 1, count = 4),
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(perDay.toString()) }
        }
    }
}

@Composable
private fun EditDialog(
    current: Edit,
    s: ReminderSettings,
    onChange: ((ReminderSettings) -> ReminderSettings) -> Unit,
    onStep: (Edit?) -> Unit,
) {
    val close = { onStep(null) }
    // key: each step gets a fresh picker state with its own initial time.
    key(current) {
        when (current) {
            is Edit.WindowStart -> {
                TimeDialog(
                    title = stringResource(R.string.reminder_pick_start, stringResource(WINDOW_NAMES[current.index])),
                    initial = s.windows[current.index].start,
                    onConfirm = { onStep(Edit.WindowEnd(current.index, it)) },
                    onDismiss = close,
                )
            }

            is Edit.WindowEnd -> {
                val endError = stringResource(R.string.reminder_end_before_start)
                TimeDialog(
                    title = stringResource(R.string.reminder_pick_end, stringResource(WINDOW_NAMES[current.index])),
                    initial = s.windows[current.index].end,
                    validate = { end -> endError.takeIf { end <= current.start } },
                    onConfirm = { end ->
                        val window = TimeWindow(current.start, end)
                        onChange { settings -> settings.copy(windows = settings.windows.replaced(current.index, window)) }
                        close()
                    },
                    onDismiss = close,
                )
            }

            is Edit.Fixed -> {
                TimeDialog(
                    title = stringResource(WINDOW_NAMES[current.index]),
                    initial = s.fixedTimes[current.index],
                    onConfirm = { time ->
                        onChange { settings -> settings.copy(fixedTimes = settings.fixedTimes.replaced(current.index, time)) }
                        close()
                    },
                    onDismiss = close,
                )
            }

            // Quiet hours may wrap midnight, and start == end means none, so there is nothing to validate.
            Edit.QuietStart -> {
                TimeDialog(
                    title = stringResource(R.string.reminder_pick_start, stringResource(R.string.reminder_quiet_hours)),
                    initial = s.quietStart,
                    onConfirm = { onStep(Edit.QuietEnd(it)) },
                    onDismiss = close,
                )
            }

            is Edit.QuietEnd -> {
                TimeDialog(
                    title = stringResource(R.string.reminder_pick_end, stringResource(R.string.reminder_quiet_hours)),
                    initial = s.quietEnd,
                    onConfirm = { end ->
                        onChange { it.copy(quietStart = current.start, quietEnd = end) }
                        close()
                    },
                    onDismiss = close,
                )
            }
        }
    }
}

private fun <T> List<T>.replaced(
    index: Int,
    value: T,
): List<T> = toMutableList().also { it[index] = value }

@Composable
private fun rowColor(enabled: Boolean) =
    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_TEXT_ALPHA)

@Composable
private fun TimeRow(
    name: String,
    value: String,
    quietHint: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = rowColor(enabled))
            if (quietHint) {
                Text(
                    stringResource(R.string.reminder_quiet_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.error else rowColor(false),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Text(value, color = rowColor(enabled))
    }
}
