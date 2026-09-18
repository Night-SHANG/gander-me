package io.legado.app.ui.book.read.epub

import com.google.common.truth.Truth.assertThat
import io.legado.app.ui.book.read.page.provider.ReaderLayoutConfig
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegadoEpubDocumentTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun epub2SpineIsParsedAndPaginatedIntoLegadoPages() {
        val file = temporaryFolder.newFile("book.epub")
        writeMinimalEpub(file)

        LegadoEpubDocument.open(file).getOrThrow().use { document ->
            assertThat(document.title).isEqualTo("测试书籍")
            assertThat(document.chapters).hasSize(1)
            assertThat(document.chapters.single().title).isEqualTo("第一章")

            val pages = document.paginate(
                viewportWidthPx = 1080,
                viewportHeightPx = 1920,
                config = ReaderLayoutConfig(
                    contentTextSizePx = 42f,
                    titleTextSizePx = 52f,
                    paddingLeftPx = 48,
                    paddingTopPx = 64,
                    paddingRightPx = 48,
                    paddingBottomPx = 64,
                ),
            )

            assertThat(pages).hasSize(1)
            assertThat(pages.single().pages).isNotEmpty()
            assertThat(pages.single().pages.joinToString { it.text }).contains("这是 EPUB 正文")

            val midpoint = document.positionForProgression(pages, 0.5f)
            val restored = document.progressionForPosition(
                pages,
                midpoint.chapterIndex,
                midpoint.pageIndex,
            )
            assertThat(restored).isAtLeast(0f)
            assertThat(restored).isAtMost(1f)
        }
    }

    private fun writeMinimalEpub(file: File) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun entry(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.trimIndent().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            entry("mimetype", "application/epub+zip")
            entry(
                "META-INF/container.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """,
            )
            entry(
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>测试书籍</dc:title>
                    <dc:identifier id="BookId">vaultshelf-test</dc:identifier>
                    <dc:language>zh-CN</dc:language>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="ncx">
                    <itemref idref="chapter1"/>
                  </spine>
                </package>
                """,
            )
            entry(
                "OEBPS/toc.ncx",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <head><meta name="dtb:uid" content="vaultshelf-test"/></head>
                  <docTitle><text>测试书籍</text></docTitle>
                  <navMap>
                    <navPoint id="nav1" playOrder="1">
                      <navLabel><text>第一章</text></navLabel>
                      <content src="chapter1.xhtml"/>
                    </navPoint>
                  </navMap>
                </ncx>
                """,
            )
            entry(
                "OEBPS/chapter1.xhtml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>第一章</title></head>
                  <body>
                    <h1>第一章</h1>
                    <p>这是 EPUB 正文，用于验证 Legado EPUB 解析与统一分页。</p>
                  </body>
                </html>
                """,
            )
        }
    }
}
