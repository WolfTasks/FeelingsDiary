package com.wolf.feelingsdiary.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The locale the UI currently shows. ViewModels outlive a language change (the activity is
 * recreated, they are not), so they combine [locale] to recompute their cached labels.
 */
@Singleton
class LocaleSource
    @Inject
    constructor() {
        private val current = MutableStateFlow<Locale>(Locale.getDefault())
        val locale: StateFlow<Locale> = current.asStateFlow()

        fun update(locale: Locale) {
            current.value = locale
        }
    }
