package com.wolf.feelingsdiary.testutil

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.data.settings.SettingsRepository
import com.wolf.feelingsdiary.data.settings.settingsDataStore
import com.wolf.feelingsdiary.domain.AppSettings
import kotlinx.coroutines.runBlocking

/**
 * Resets the real settings store to a fresh install (all keys removed), then applies [transform] if given.
 * The DataStore delegate is process-wide, so every Robolectric test in the same JVM shares its contents:
 * tests that write it call this before and after each test.
 */
fun resetSettings(transform: ((AppSettings) -> AppSettings)? = null) =
    runBlocking {
        val store = ApplicationProvider.getApplicationContext<Context>().settingsDataStore
        store.edit { it.clear() }
        if (transform != null) SettingsRepository(store).update(transform)
    }
