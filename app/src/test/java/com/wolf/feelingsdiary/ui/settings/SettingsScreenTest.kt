package com.wolf.feelingsdiary.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.ThemeMode
import com.wolf.feelingsdiary.ui.theme.FeelingsDiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val themes = mutableListOf<ThemeMode>()
    private val languages = mutableListOf<String?>()
    private var backs = 0
    private val actions =
        SettingsActions(
            onBack = { backs++ },
            onTheme = { themes += it },
            onLanguage = { languages += it },
        )

    private fun show(
        state: AppSettings = AppSettings(),
        language: String? = null,
    ) {
        rule.setContent { FeelingsDiaryTheme { SettingsScreen(state, actions, language) } }
    }

    @Test
    fun themeChoiceCallsAction() {
        show()
        rule.onNodeWithText(ctx.getString(R.string.theme_dark)).assertIsSelected()
        rule.onNodeWithText(ctx.getString(R.string.theme_light)).performScrollTo().performClick()
        assertEquals(listOf(ThemeMode.LIGHT), themes)
    }

    @Test
    fun backArrowCallsAction() {
        show()
        rule.onNodeWithContentDescription(ctx.getString(R.string.settings_back_cd)).performClick()
        assertEquals(1, backs)
    }

    @Test
    @Config(sdk = [32])
    fun languageRowHiddenBelowApi33() {
        show()
        rule.onNodeWithText(ctx.getString(R.string.settings_section_language)).assertDoesNotExist()
        rule.onNodeWithText(ctx.getString(R.string.language_system)).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [33])
    fun languageRowShownOnApi33() {
        show(language = "de")
        rule.onNodeWithText(ctx.getString(R.string.language_de)).performScrollTo().assertIsSelected()
        rule.onNodeWithText(ctx.getString(R.string.language_en)).performScrollTo().performClick()
        rule.onNodeWithText(ctx.getString(R.string.language_system)).performScrollTo().performClick()
        assertEquals(listOf("en", null), languages)
    }
}
