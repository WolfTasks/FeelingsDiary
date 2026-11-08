package com.wolf.feelingsdiary.ui

import android.content.Context
import android.content.res.Configuration
import com.wolf.feelingsdiary.data.db.BaseFeelings
import com.wolf.feelingsdiary.domain.Feeling
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton

/** Display name of a feeling: localized for base feelings, the user's text for custom ones. */
fun interface FeelingLabeler {
    fun label(f: Feeling): String
}

/**
 * Resolves base-feeling names in the [LocaleSource] locale rather than the application context's,
 * which may still carry the previous language after a per-app language change.
 */
internal class LocalizedFeelingLabeler(
    private val app: Context,
    private val localeSource: LocaleSource,
) : FeelingLabeler {
    // One localized context per locale; rebuilt only when the language changes.
    @Volatile
    private var cache: Pair<Locale, Context>? = null

    override fun label(f: Feeling): String = f.baseKey?.let { localized().getString(BaseFeelings.labelFor(it)) } ?: f.customName.orEmpty()

    private fun localized(): Context {
        val locale = localeSource.locale.value
        cache?.let { (cached, ctx) -> if (cached == locale) return ctx }
        val config = Configuration(app.resources.configuration).apply { setLocale(locale) }
        return app.createConfigurationContext(config).also { cache = locale to it }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object FeelingLabelModule {
    @Provides
    @Singleton
    fun provideFeelingLabeler(
        @ApplicationContext context: Context,
        localeSource: LocaleSource,
    ): FeelingLabeler = LocalizedFeelingLabeler(context, localeSource)
}
