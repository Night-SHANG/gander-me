package com.arjun.gander.epub

import com.arjun.gander.library.TxtChapter
import com.arjun.gander.library.TxtChapterParser
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a small standards-compliant EPUB derivative for a plain-text book.
 *
 * VaultShelf keeps the imported TXT untouched. This derivative exists only so TXT can use
 * the same Readium pagination, scrolling, navigation and preference pipeline as EPUB.
 */
object TxtEpubBuilder {

    fun build(
        title: String,
        text: String,
        chapters: List<TxtChapter>,
        destination: File,
        startLabel: String = "Start",
    ): File {
        destination.parentFile?.mkdirs()
        val effectiveChapters = chapters.ifEmpty {
            listOf(TxtChapter(null, 0, 0, text.length))
        }
        val identifier = UUID.nameUUIDFromBytes(
            (title + "\u0000" + text.length + "\u0000" + text.hashCode())
                .toByteArray(StandardCharsets.UTF_8),
        ).toString()

        FileOutputStream(destination).use { fileOutput ->
            ZipOutputStream(fileOutput).use { zip ->
                putStored(zip, "mimetype", "application/epub+zip")
                putText(zip, "META-INF/container.xml", containerXml())
                putText(zip, "OEBPS/styles.css", stylesCss())
                putText(
                    zip,
                    "OEBPS/nav.xhtml",
                    navigationXhtml(effectiveChapters, startLabel),
                )
                effectiveChapters.forEachIndexed { index, chapter ->
                    putText(
                        zip,
                        "OEBPS/chapter-${index + 1}.xhtml",
                        chapterXhtml(
                            title = chapter.title ?: startLabel,
                            text = text,
                            chapter = chapter,
                        ),
                    )
                }
                putText(
                    zip,
                    "OEBPS/content.opf",
                    packageOpf(
                        title = title,
                        identifier = identifier,
                        chapterCount = effectiveChapters.size,
                    ),
                )
            }
        }
        return destination
    }

    private fun putStored(zip: ZipOutputStream, name: String, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun putText(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun containerXml(): String = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

    private fun packageOpf(title: String, identifier: String, chapterCount: Int): String {
        val manifestItems = buildString {
            append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
            append("    <item id=\"css\" href=\"styles.css\" media-type=\"text/css\"/>\n")
            repeat(chapterCount) { index ->
                append("    <item id=\"chapter-${index + 1}\" href=\"chapter-${index + 1}.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
            }
        }
        val spineItems = buildString {
            repeat(chapterCount) { index ->
                append("    <itemref idref=\"chapter-${index + 1}\"/>\n")
            }
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="book-id" version="3.0" xml:lang="zh-CN">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">urn:uuid:${escapeXml(identifier)}</dc:identifier>
    <dc:title>${escapeXml(title)}</dc:title>
    <dc:language>zh-CN</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
$manifestItems  </manifest>
  <spine>
$spineItems  </spine>
</package>
"""
    }

    private fun navigationXhtml(chapters: List<TxtChapter>, startLabel: String): String {
        val items = chapters.mapIndexed { index, chapter ->
            val label = chapter.title?.takeIf { it.isNotBlank() } ?: startLabel
            "      <li><a href=\"chapter-${index + 1}.xhtml\">${escapeXml(label)}</a></li>"
        }.joinToString("\n")
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh-CN">
<head><title>${escapeXml(startLabel)}</title><meta charset="UTF-8"/></head>
<body>
  <nav epub:type="toc" id="toc">
    <ol>
$items
    </ol>
  </nav>
</body>
</html>
"""
    }

    private fun chapterXhtml(title: String, text: String, chapter: TxtChapter): String {
        val paragraphs = TxtChapterParser.paragraphs(text, chapter)
        val body = if (paragraphs.isEmpty()) {
            "    <p></p>"
        } else {
            paragraphs.joinToString("\n") { paragraph ->
                "    <p>${escapeXml(paragraph.text)}</p>"
            }
        }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="zh-CN">
<head>
  <title>${escapeXml(title)}</title>
  <meta charset="UTF-8"/>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
  <section>
    <h1>${escapeXml(title)}</h1>
$body
  </section>
</body>
</html>
"""
    }

    private fun stylesCss(): String = """
html, body { margin: 0; padding: 0; }
body { line-height: 1.65; }
h1 { font-size: 1.35em; margin: 1.6em 0 1.1em; }
p { margin: 0 0 0.9em; text-indent: 2em; }
""".trimIndent()

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            append(
                when (char) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> char
                },
            )
        }
    }
}
