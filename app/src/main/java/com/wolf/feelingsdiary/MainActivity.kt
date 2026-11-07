package com.wolf.feelingsdiary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wolf.feelingsdiary.ui.navigation.AppNavHost
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeelingsDiaryTheme {
                AppNavHost()
            }
        }
    }
}
