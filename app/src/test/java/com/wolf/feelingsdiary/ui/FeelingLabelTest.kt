package com.wolf.feelingsdiary.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.domain.Feeling
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class FeelingLabelTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val happy = Feeling(1, "happy", null, 0, 2, false, null)

    @Test
    fun baseLabelsFollowLocaleSourceNotAppContext() {
        val source = LocaleSource().apply { update(Locale.ENGLISH) }
        val labeler = LocalizedFeelingLabeler(ctx, source)
        assertEquals("happy", labeler.label(happy))
        source.update(Locale.GERMAN)
        assertEquals("fröhlich", labeler.label(happy))
    }

    @Test
    fun customLabelIsTheUsersText() {
        val labeler = LocalizedFeelingLabeler(ctx, LocaleSource().apply { update(Locale.GERMAN) })
        assertEquals("Wehmut", labeler.label(Feeling(9, null, "Wehmut", 0, -1, false, null)))
    }
}
