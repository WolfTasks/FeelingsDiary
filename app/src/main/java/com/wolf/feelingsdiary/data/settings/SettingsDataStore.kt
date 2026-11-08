package com.wolf.feelingsdiary.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

/** Plain top-level delegate so non-Hilt callers (e.g. a BackupAgent) can use it via applicationContext. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = settingsCorruptionHandler(),
)

/** An unreadable settings file is replaced by an empty one (all defaults) instead of failing every read. */
fun settingsCorruptionHandler(): ReplaceFileCorruptionHandler<Preferences> = ReplaceFileCorruptionHandler { emptyPreferences() }
