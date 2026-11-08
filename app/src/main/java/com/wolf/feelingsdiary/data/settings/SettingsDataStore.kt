package com.wolf.feelingsdiary.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Plain top-level delegate so non-Hilt callers (e.g. a BackupAgent) can use it via applicationContext. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
