package com.wolf.feelingsdiary.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/** Whether this device supports a per-app language (Android 13+); below that the app follows the system. */
val supportsAppLanguage: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** The chosen per-app language tag ("de", "en"), or `null` when the app follows the system language. */
fun appLanguage(ctx: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val locales = ctx.getSystemService(LocaleManager::class.java).applicationLocales
    return if (locales.isEmpty) null else locales[0].language
}

/** Sets the per-app language; `null` returns to the system language. The system recreates the activity. */
fun setAppLanguage(
    ctx: Context,
    tag: String?,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val locales = tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
    ctx.getSystemService(LocaleManager::class.java).applicationLocales = locales
}
