package com.arjun.gander

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScrollPositionsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun savesAndRestoresNormalizedScrollFraction() {
        val key = "markdown-scroll-test"
        ScrollPositions.save(context, key, 0.42f)

        assertThat(ScrollPositions.fraction(context, key)).isWithin(0.0001f).of(0.42f)
    }

    @Test
    fun finishedDocumentClearsSavedPosition() {
        val key = "markdown-finished-test"
        ScrollPositions.save(context, key, 0.5f)
        ScrollPositions.save(context, key, 1f)

        assertThat(ScrollPositions.fraction(context, key)).isEqualTo(0f)
    }
}
