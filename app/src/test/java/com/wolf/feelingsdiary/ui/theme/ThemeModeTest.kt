package com.wolf.feelingsdiary.ui.theme

import android.app.Application
import android.content.res.Configuration
import com.wolf.feelingsdiary.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The theme outside composition, as used for the first system bar style. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ThemeModeTest {
    private fun config(night: Int) = Configuration().apply { uiMode = Configuration.UI_MODE_TYPE_NORMAL or night }

    @Test
    fun systemFollowsNightMode() {
        assertEquals(true, ThemeMode.SYSTEM.isDark(config(Configuration.UI_MODE_NIGHT_YES)))
        assertEquals(false, ThemeMode.SYSTEM.isDark(config(Configuration.UI_MODE_NIGHT_NO)))
    }

    @Test
    fun fixedModesIgnoreNightMode() {
        assertEquals(true, ThemeMode.DARK.isDark(config(Configuration.UI_MODE_NIGHT_NO)))
        assertEquals(false, ThemeMode.LIGHT.isDark(config(Configuration.UI_MODE_NIGHT_YES)))
    }
}
