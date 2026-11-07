package com.wolf.feelingsdiary.ui

import android.content.Context
import com.wolf.feelingsdiary.data.db.BaseFeelings
import com.wolf.feelingsdiary.domain.Feeling
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** Display name of a feeling: localized for base feelings, the user's text for custom ones. */
fun interface FeelingLabeler {
    fun label(f: Feeling): String
}

@Module
@InstallIn(SingletonComponent::class)
object FeelingLabelModule {
    @Provides
    fun provideFeelingLabeler(
        @ApplicationContext context: Context,
    ): FeelingLabeler =
        FeelingLabeler { f ->
            f.baseKey?.let { context.getString(BaseFeelings.labelFor(it)) } ?: f.customName.orEmpty()
        }
}
