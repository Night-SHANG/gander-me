/*
 * Local EPUB adapter built on Legado's standalone :modules:book parser.
 * Source: https://github.com/LegadoTeam/legado @ 62003ce732a7e30602754d28996da7f98b9ea296
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.epub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import io.legado.app.ui.book.read.page.ReaderImageProvider
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ReaderBlock
import io.legado.app.ui.book.read.page.provider.ReaderLayoutConfig
import io.legado.app.ui.book.read.page.provider.RichChapterPaginator
import java.io.Closeable
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.zip.ZipFile
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.domain.TOCReference
import me.ag2s.epublib.epub.EpubReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

data class EpubSourceChapter(
    val title: String,
    val href: String,
    val blocks: List<ReaderBlock>,
)

data class EpubReaderPosition(
    val chapterIndex: Int,
    val pageIndex: Int,
)

class LegadoEpubDocument private constructor(
    private val zipFile: ZipFile,
    private val epubBook: EpubBook,
    val title: String,
    val chapters: List<EpubSourceChapter>,
) : Closeable, ReaderImageProvider {

    private val bitmapCache = object : LruCache<String, Bitmap>(BITMAP_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun paginate(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        config: ReaderLayoutConfig,
    ): List<TextChapter> = chapters.mapIndexed { index, chapter ->
        RichChapterPaginator.paginate(
            title = chapter.title,
            blocks = chapter.blocks,
            chapterIndex = index,
            chapterCount = chapters.size,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            config = config,
        )
    }

    fun positionForProgression(
        paginated: List<TextChapter>,
        progression: Float?,
    ): EpubReaderPosition {
        if (paginated.isEmpty()) return EpubReaderPosition(0, 0)
        val safe = (progression ?: 0f).coerceIn(0f, 1f)
        val totalPages = paginated.sumOf { it.pages.size.coerceAtLeast(1) }.coerceAtLeast(1)
        var target = (safe * (totalPages - 1)).toInt().coerceIn(0, totalPages - 1)
        paginated.forEachIndexed { chapterIndex, chapter ->
            val count = chapter.pages.size.coerceAtLeast(1)
            if (target < count) {
                return EpubReaderPosition(chapterIndex, target.coerceAtMost(chapter.lastIndex.coerceAtLeast(0)))
            }
            target -= count
        }
        val lastChapter = paginated.lastIndex
        return EpubReaderPosition(lastChapter, paginated[lastChapter].lastIndex.coerceAtLeast(0))
    }

    fun progressionForPosition(
        paginated: List<TextChapter>,
        chapterIndex: Int,
        pageIndex: Int,
    ): Float {
        if (paginated.isEmpty()) return 0f
        val totalPages = paginated.sumOf { it.pages.size.coerceAtLeast(1) }.coerceAtLeast(1)
        val safeChapter = chapterIndex.coerceIn(0, paginated.lastIndex)
        val before = paginated.take(safeChapter).sumOf { it.pages.size.coerceAtLeast(1) }
        val chapterPages = paginated[safeChapter].pages.size.coerceAtLeast(1)
        val safePage = pageIndex.coerceIn(0, chapterPages - 1)
        if (totalPages <= 1) return 0f
        return ((before + safePage).toFloat() / (totalPages - 1).toFloat()).coerceIn(0f, 1f)
    }

    override fun load(source: String): Bitmap? {
        bitmapCache.get(source)?.let { if (!it.isRecycled) return it }
        val resource = epubBook.resources.getByHref(source) ?: return null
        val data = runCatching { resource.data }.getOrNull() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
        bitmapCache.put(source, bitmap)
        return bitmap
    }

    override fun close() {
        bitmapCache.snapshot().values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        bitmapCache.evictAll()
        runCatching { zipFile.close() }
    }

    companion object {
        private const val BITMAP_CACHE_BYTES = 16 * 1024 * 1024
        private val BLOCK_TAGS = setOf(
            "address", "article", "aside", "blockquote", "dd", "div", "dl", "dt",
            "figcaption", "figure", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
            "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section", "table",
            "tbody", "td", "tfoot", "th", "thead", "tr", "ul",
        )

        fun open(file: File): Result<LegadoEpubDocument> = runCatching {
            val zip = ZipFile(file)
            try {
                val book = EpubReader().readEpubLazy(zip, "utf-8")
                val tocTitles = buildTocTitleMap(book.tableOfContents.tocReferences)
                val sourceChapters = book.spine.spineReferences.mapIndexedNotNull { index, reference ->
                    val resource = reference.resource ?: return@mapIndexedNotNull null
                    val href = resource.href?.substringBefore('#') ?: return@mapIndexedNotNull null
                    val document = resource.reader.use { reader -> Jsoup.parse(reader, href) }
                    val fallbackTitle = document.title().takeIf { it.isNotBlank() }
                        ?: resource.title?.takeIf { it.isNotBlank() }
                        ?: "Chapter ${index + 1}"
                    val title = tocTitles[href]?.takeIf { it.isNotBlank() } ?: fallbackTitle
                    val blocks = extractBlocks(book, resource, document.body())
                    if (blocks.isEmpty() && title.isBlank()) return@mapIndexedNotNull null
                    EpubSourceChapter(title = title, href = href, blocks = blocks)
                }
                val bookTitle = book.metadata.firstTitle?.takeIf { it.isNotBlank() }
                    ?: file.nameWithoutExtension
                LegadoEpubDocument(
                    zipFile = zip,
                    epubBook = book,
                    title = bookTitle,
                    chapters = sourceChapters,
                )
            } catch (error: Throwable) {
                runCatching { zip.close() }
                throw error
            }
        }

        private fun buildTocTitleMap(references: List<TOCReference>): Map<String, String> {
            val result = linkedMapOf<String, String>()
            fun visit(items: List<TOCReference>) {
                items.forEach { reference ->
                    val href = reference.resource?.href?.substringBefore('#')
                    val title = reference.title
                    if (!href.isNullOrBlank() && !title.isNullOrBlank()) {
                        result.putIfAbsent(href, title)
                    }
                    visit(reference.children.orEmpty())
                }
            }
            visit(references)
            return result
        }

        private fun extractBlocks(
            book: EpubBook,
            resource: Resource,
            body: Element,
        ): List<ReaderBlock> {
            body.select("script, style, title, [style*=display:none]").remove()
            body.select("image").forEach { image ->
                image.tagName("img")
                if (!image.hasAttr("src")) image.attr("src", image.attr("xlink:href"))
            }

            val blocks = mutableListOf<ReaderBlock>()
            val text = StringBuilder()

            fun flushText() {
                val normalized = text.toString()
                    .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                    .replace(Regex("\\n{2,}"), "\n")
                    .trim()
                text.setLength(0)
                normalized.split('\n').map { it.trim() }.filter { it.isNotBlank() }.forEach {
                    blocks += ReaderBlock.Text(it)
                }
            }

            fun addImage(element: Element) {
                flushText()
                val raw = element.attr("src").trim()
                if (raw.isBlank()) return
                val resolved = resolveHref(resource.href, raw)
                val imageResource = book.resources.getByHref(resolved) ?: return
                val data = runCatching { imageResource.data }.getOrNull() ?: return
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)
                val width = options.outWidth.takeIf { it > 0 } ?: 480
                val height = options.outHeight.takeIf { it > 0 } ?: 320
                blocks += ReaderBlock.Image(
                    source = resolved,
                    intrinsicWidthPx = width,
                    intrinsicHeightPx = height,
                )
            }

            fun visit(node: Node) {
                when (node) {
                    is TextNode -> text.append(node.text())
                    is Element -> {
                        val tag = node.normalName()
                        when (tag) {
                            "img" -> addImage(node)
                            "br" -> flushText()
                            else -> {
                                val block = tag in BLOCK_TAGS
                                if (block) flushText()
                                node.childNodes().forEach(::visit)
                                if (block) flushText()
                            }
                        }
                    }
                    else -> node.childNodes().forEach(::visit)
                }
            }

            body.childNodes().forEach(::visit)
            flushText()
            return blocks
        }

        private fun resolveHref(baseHref: String?, rawHref: String): String {
            if (rawHref.startsWith("data:", ignoreCase = true)) return rawHref
            return runCatching {
                val base = URI(baseHref.orEmpty())
                val resolved = base.resolve(rawHref).normalize().toString()
                URLDecoder.decode(resolved, Charsets.UTF_8.name())
            }.getOrDefault(rawHref)
        }
    }
}
