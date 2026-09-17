package com.arjun.gander.epub

import com.arjun.gander.library.TxtChapterParser
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

class TxtEpubBuilderTest {

    @Test
    fun build_createsReadableEpubSkeletonWithNavigationAndChapters() {
        val text = """
            序言内容

            第一章 开始
            第一段。
            第二段。

            第二章 继续
            第三段。
        """.trimIndent()
        val destination = File.createTempFile("vaultshelf-txt-", ".epub")
        destination.deleteOnExit()

        TxtEpubBuilder.build(
            title = "测试小说",
            text = text,
            chapters = TxtChapterParser.parse(text),
            destination = destination,
        )

        ZipFile(destination).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("mimetype" in entries)
            assertTrue("META-INF/container.xml" in entries)
            assertTrue("OEBPS/content.opf" in entries)
            assertTrue("OEBPS/nav.xhtml" in entries)
            assertTrue(entries.any { it.startsWith("OEBPS/chapter-") && it.endsWith(".xhtml") })

            val mimetype = zip.getInputStream(assertNotNull(zip.getEntry("mimetype")))
                .bufferedReader()
                .use { it.readText() }
            assertEquals("application/epub+zip", mimetype)

            val nav = zip.getInputStream(assertNotNull(zip.getEntry("OEBPS/nav.xhtml")))
                .bufferedReader()
                .use { it.readText() }
            assertTrue(nav.contains("第一章 开始"))
            assertTrue(nav.contains("第二章 继续"))

            val allChapterText = entries
                .filter { it.startsWith("OEBPS/chapter-") && it.endsWith(".xhtml") }
                .sorted()
                .joinToString("\n") { entryName ->
                    zip.getInputStream(assertNotNull(zip.getEntry(entryName)))
                        .bufferedReader()
                        .use { it.readText() }
                }
            assertTrue(allChapterText.contains("序言内容"))
            assertTrue(allChapterText.contains("第一段。"))
            assertTrue(allChapterText.contains("第三段。"))
        }
    }

    @Test
    fun build_escapesUnsafeMarkupFromPlainText() {
        val text = "第一章 <开始>\nA & B > C"
        val destination = File.createTempFile("vaultshelf-txt-escape-", ".epub")
        destination.deleteOnExit()

        TxtEpubBuilder.build(
            title = "A & B <书>",
            text = text,
            chapters = TxtChapterParser.parse(text),
            destination = destination,
        )

        ZipFile(destination).use { zip ->
            val opf = zip.getInputStream(assertNotNull(zip.getEntry("OEBPS/content.opf")))
                .bufferedReader()
                .use { it.readText() }
            assertTrue(opf.contains("A &amp; B &lt;书&gt;"))

            val chapterEntry = zip.entries().asSequence()
                .first { it.name.startsWith("OEBPS/chapter-") && it.name.endsWith(".xhtml") }
            val chapter = zip.getInputStream(chapterEntry).bufferedReader().use { it.readText() }
            assertTrue(chapter.contains("A &amp; B &gt; C"))
        }
    }
}
