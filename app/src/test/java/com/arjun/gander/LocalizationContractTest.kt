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
        val FALLBACK = File(REPO, "app/src/main/res/values")
        val SIMPLIFIED_CHINESE = File(REPO, "app/src/main/res/values-zh-rCN")
        val FORMAT_TOKEN = Regex("""%%|%(?:\d+\$)?[a-zA-Z]""")
    }

    @Test
    fun simplifiedChineseCoversEveryTranslatableFallbackString() {
        assertWithMessage("Simplified Chinese resource directory must exist")
            .that(SIMPLIFIED_CHINESE.exists())
            .isTrue()

        val fallback = stringValues(FALLBACK, skipNotTranslatable = true).keys
        val chinese = stringValues(SIMPLIFIED_CHINESE, skipNotTranslatable = false).keys
        val missing = fallback - chinese

        assertWithMessage("Missing Simplified Chinese strings: ${missing.sorted()}")
            .that(missing)
            .isEmpty()
    }

    @Test
    fun simplifiedChineseDoesNotInventUnknownStringKeys() {
        assertThat(SIMPLIFIED_CHINESE.exists()).isTrue()

        val fallback = stringValues(FALLBACK, skipNotTranslatable = false).keys
        val chinese = stringValues(SIMPLIFIED_CHINESE, skipNotTranslatable = false).keys
        val unknown = chinese - fallback

        assertWithMessage("Chinese-only string keys should be declared in the fallback locale: ${unknown.sorted()}")
            .that(unknown)
            .isEmpty()
    }

    @Test
    fun simplifiedChinesePreservesFormatPlaceholders() {
        val fallback = stringValues(FALLBACK, skipNotTranslatable = true)
        val chinese = stringValues(SIMPLIFIED_CHINESE, skipNotTranslatable = false)

        val mismatches = fallback.mapNotNull { (name, fallbackText) ->
            val chineseText = chinese[name] ?: return@mapNotNull null
            val expected = FORMAT_TOKEN.findAll(fallbackText).map { it.value }.sorted().toList()
            val actual = FORMAT_TOKEN.findAll(chineseText).map { it.value }.sorted().toList()
            if (expected == actual) null else "$name expected=$expected actual=$actual"
        }

        assertWithMessage("Translated format placeholders must match the fallback locale: $mismatches")
            .that(mismatches)
            .isEmpty()
    }

    private fun stringValues(directory: File, skipNotTranslatable: Boolean): Map<String, String> {
        val files = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "xml" }
            .sortedBy { it.name }
        val result = linkedMapOf<String, String>()

        files.forEach { file ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = document.getElementsByTagName("string")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                if (skipNotTranslatable && element.getAttribute("translatable") == "false") continue
                val name = element.getAttribute("name")
                check(name !in result) { "Duplicate string resource $name in ${file.path}" }
                result[name] = element.textContent
            }
        }
        return result
    }
}
