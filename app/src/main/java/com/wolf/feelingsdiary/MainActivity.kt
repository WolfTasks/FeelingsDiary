package com.wolf.feelingsdiary

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wolf.feelingsdiary.ui.navigation.AppNavHost
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Buffered until the nav graph collects, consumed once; a recreated activity starts empty.
    private val newEntryRequests = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Same edge-to-edge layout on every API level; the app is always dark, so light bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // On recreation (rotation, process death) the launch intent was already handled.
        if (savedInstanceState == null) handle(intent)
        val requests = newEntryRequests.receiveAsFlow()
        setContent {
            FeelingsDiaryTheme {
                AppNavHost(requests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // Reopening from Recents replays the last launch intent; that must not open another entry.
        val fromHistory = (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (intent?.action == ACTION_NEW_ENTRY && !fromHistory) newEntryRequests.trySend(Unit)
    }

    companion object {
        const val ACTION_NEW_ENTRY = "com.wolf.feelingsdiary.NEW_ENTRY"
    }
}
