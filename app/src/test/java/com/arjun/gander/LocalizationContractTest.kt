package com.arjun.gander

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * Keeps Simplified Chinese a first-class locale instead of a translation that
 * silently falls behind whenever new UI is added.
 */
class LocalizationContractTest {

    private companion object {
        val REPO = File("..")
        val FALLBACK = File(REPO, "app/src/main/res/values/strings.xml")
        val SIMPLIFIED_CHINESE = File(REPO, "app/src/main/res/values-zh-rCN/strings.xml")
    }

    @Test
    fun simplifiedChineseCoversEveryTranslatableFallbackString() {
        assertWithMessage("Simplified Chinese resource file must exist")
            .that(SIMPLIFIED_CHINESE.exists())
            .isTrue()

        val fallback = stringNames(FALLBACK, skipNotTranslatable = true)
        val chinese = stringNames(SIMPLIFIED_CHINESE, skipNotTranslatable = false)
        val missing = fallback - chinese

        assertWithMessage("Missing Simplified Chinese strings: ${missing.sorted()}")
            .that(missing)
            .isEmpty()
    }

    @Test
    fun simplifiedChineseDoesNotInventUnknownStringKeys() {
        assertThat(SIMPLIFIED_CHINESE.exists()).isTrue()

        val fallback = stringNames(FALLBACK, skipNotTranslatable = false)
        val chinese = stringNames(SIMPLIFIED_CHINESE, skipNotTranslatable = false)
        val unknown = chinese - fallback

        assertWithMessage("Chinese-only string keys should be declared in the fallback locale: ${unknown.sorted()}")
            .that(unknown)
            .isEmpty()
    }

    private fun stringNames(file: File, skipNotTranslatable: Boolean): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildSet {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                if (skipNotTranslatable && element.getAttribute("translatable") == "false") continue
                add(element.getAttribute("name"))
            }
        }
    }
}
