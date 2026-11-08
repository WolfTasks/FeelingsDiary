package com.wolf.feelingsdiary.data

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.data.settings.settingsCorruptionHandler
import com.wolf.feelingsdiary.domain.AppSettings
import com.wolf.feelingsdiary.domain.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * An unreadable settings file must not make the app unusable. Robolectric (not plain JUnit) so the
 * store replaces files the Android way; a plain JVM on Windows cannot rename over an existing file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsCorruptionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Bytes that are no valid preferences file: the varint tag never ends. */
    private fun garbageFile() = File(tmp.root, "bad.preferences_pb").apply { writeBytes(ByteArray(32) { 0xFF.toByte() }) }

    @Test
    fun corruptFileIsReplacedWithDefaultsAndStaysWritable() =
        runTest(UnconfinedTestDispatcher()) {
            val file = garbageFile()
            // The same handler the app's settings delegate uses.
            val store = PreferenceDataStoreFactory.create(corruptionHandler = settingsCorruptionHandler(), scope = backgroundScope) { file }
            val repo = SettingsRepository(store)
            assertEquals(AppSettings(), repo.settings.first())
            repo.update { it.copy(theme = ThemeMode.LIGHT) }
            assertEquals(ThemeMode.LIGHT, repo.settings.first().theme)
        }

    @Test
    fun unreadableStoreYieldsDefaults() =
        runTest(UnconfinedTestDispatcher()) {
            val file = garbageFile()
            // No corruption handler: the read itself fails with an IOException.
            val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })
            assertEquals(AppSettings(), repo.settings.first())
        }
}
