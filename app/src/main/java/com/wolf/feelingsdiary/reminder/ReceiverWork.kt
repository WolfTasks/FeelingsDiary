package com.wolf.feelingsdiary.reminder

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Runs [work] off the main thread while keeping the broadcast (and the process) alive until it ends. */
internal fun BroadcastReceiver.runAsync(
    scope: CoroutineScope,
    work: suspend () -> Unit,
) {
    val pending = goAsync()
    scope.launch {
        try {
            work()
        } finally {
            pending.finish()
        }
    }
}
