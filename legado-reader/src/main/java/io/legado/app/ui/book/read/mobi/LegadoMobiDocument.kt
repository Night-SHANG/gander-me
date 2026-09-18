/*
 * MOBI/AZW adapter built on Legado's lib/mobi parser.
 * Source: https://github.com/LegadoTeam/legado @ 62003ce732a7e30602754d28996da7f98b9ea296
 * Licensed under GNU GPL v3. VaultShelf adapter: 2026-09-18.
 */
package io.legado.app.ui.book.read.mobi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.util.LruCache
import io.legado.app.lib.mobi.KF6Book
import io.legado.app.lib.mobi.KF8Book
import io.legado.app.lib.mobi.MobiBook
import io.legado.app.lib.mobi.MobiReader
import io.legado.app.lib.mobi.entities.TOC
import io.legado.app.ui.book.read.page.ReaderImageProvider
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ReaderBlock
import io.legado.app.ui.book.read.page.provider.ReaderLayoutConfig
import io.legado.app.ui.book.read.page.provider.RichChapterPaginator
import java.io.Closeable
import java.io.File
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

data class MobiSourceChapter(
    val title: String,
    val blocks: List<ReaderBlock>,
)

data class MobiReaderPosition(
    val chapterIndex: Int,
    val pageIndex: Int,
)

private data class MobiChapterSpec(
    val title: String,
    val href: String,
)

class LegadoMobiDocument private constructor(
    val title: String,
    val chapters: List<MobiSourceChapter>,
    private val coverBytes: ByteArray?,
    private val imageBytes: Map<String, ByteArray>,
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
    ): MobiReaderPosition {
        if (paginated.isEmpty()) return MobiReaderPosition(0, 0)
        val safe = (progression ?: 0f).coerceIn(0f, 1f)
        val totalPages = paginated.sumOf { it.pages.size.coerceAtLeast(1) }.coerceAtLeast(1)
        var target = (safe * (totalPages - 1)).toInt().coerceIn(0, totalPages - 1)
        paginated.forEachIndexed { chapterIndex, chapter ->
            val count = chapter.pages.size.coerceAtLeast(1)
            if (target < count) {
                return MobiReaderPosition(
                    chapterIndex,
                    target.coerceAtMost(chapter.lastIndex.coerceAtLeast(0)),
                )
            }
            target -= count
        }
        val lastChapter = paginated.lastIndex
        return MobiReaderPosition(
            lastChapter,
            paginated[lastChapter].lastIndex.coerceAtLeast(0),
        )
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

    fun coverBitmap(maxWidth: Int, maxHeight: Int): Bitmap? {
        val data = coverBytes?.takeIf { it.isNotEmpty() } ?: return null
        return decodeBoundedBitmap(data, maxWidth, maxHeight)
    }

    override fun load(source: String): Bitmap? {
        bitmapCache.get(source)?.let { bitmap ->
            if (!bitmap.isRecycled) return bitmap
        }
        val data = imageBytes[source] ?: return null
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
        bitmapCache.put(source, bitmap)
        return bitmap
    }

    override fun close() {
        bitmapCache.snapshot().values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        bitmapCache.evictAll()
    }

    companion object {
        private const val BITMAP_CACHE_BYTES = 16 * 1024 * 1024
        private val BLOCK_TAGS = setOf(
            "address", "article", "aside", "blockquote", "dd", "div", "dl", "dt",
            "figcaption", "figure", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
            "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section", "table",
            "tbody", "td", "tfoot", "th", "thead", "tr", "ul",
        )

        fun open(file: File): Result<LegadoMobiDocument> = runCatching {
            val descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            var mobiBook: MobiBook? = null
            try {
                mobiBook = MobiReader().readMobi(descriptor)
                val specs = buildChapterSpecs(mobiBook)
                val images = linkedMapOf<String, ByteArray>()
                val sourceChapters = specs.mapIndexedNotNull { index, spec ->
                    val nextHref = specs.getOrNull(index + 1)?.href
                    val html = getChapterHtml(mobiBook, spec.href, nextHref)
                    val blocks = extractBlocks(mobiBook, html, images)
                    if (blocks.isEmpty() && spec.title.isBlank()) {
                        null
                    } else {
                        MobiSourceChapter(
                            title = spec.title.ifBlank { "Chapter ${index + 1}" },
                            blocks = blocks,
                        )
                    }
                }.ifEmpty {
                    fallbackSections(mobiBook, images)
                }
                require(sourceChapters.isNotEmpty()) {
                    "MOBI contains no readable chapters"
                }
                val metadata = mobiBook.metadata
                LegadoMobiDocument(
                    title = metadata.title.takeIf { it.isNotBlank() }
                        ?: file.nameWithoutExtension,
                    chapters = sourceChapters,
                    coverBytes = mobiBook.getCover(),
                    imageBytes = images,
                )
            } finally {
                if (mobiBook != null) {
                    runCatching { mobiBook.close() }
                } else {
                    runCatching { descriptor.close() }
                }
            }
        }

        private fun buildChapterSpecs(book: MobiBook): List<MobiChapterSpec> {
            val result = mutableListOf<MobiChapterSpec>()
            fun append(items: List<TOC>) {
                items.forEach { item ->
                    result += MobiChapterSpec(
                        title = item.label.orEmpty(),
                        href = item.href,
                    )
                    append(item.subitems.orEmpty())
                }
            }
            append(book.toc.orEmpty())
            return result.distinctBy { it.href }
        }

        private fun fallbackSections(
            book: MobiBook,
            images: MutableMap<String, ByteArray>,
        ): List<MobiSourceChapter> = when (book) {
            is KF6Book -> book.sections.mapIndexedNotNull { index, section ->
                val blocks = extractBlocks(book, book.getSectionText(section), images)
                if (blocks.isEmpty()) null else MobiSourceChapter(
                    title = "Chapter ${index + 1}",
                    blocks = blocks,
                )
            }

            is KF8Book -> book.sections.mapIndexedNotNull { index, section ->
                if (!section.linear) return@mapIndexedNotNull null
                val blocks = extractBlocks(book, book.getSectionText(section), images)
                if (blocks.isEmpty()) null else MobiSourceChapter(
                    title = "Chapter ${index + 1}",
                    blocks = blocks,
                )
            }

            else -> emptyList()
        }

        private fun getChapterHtml(
            book: MobiBook,
            href: String,
            nextHref: String?,
        ): String = when (book) {
            is KF6Book -> {
                var section = book.getSectionByHref(href) ?: return ""
                val result = StringBuilder(book.getSectionText(section))
                while (true) {
                    section = section.next ?: break
                    if (section.href == nextHref) break
                    if (book.sectionIdMap[section.index] != null) break
                    result.append(book.getSectionText(section))
                }
                result.toString()
            }

            is KF8Book -> {
                var section = book.getSectionByHref(href) ?: return ""
                val next = nextHref.orEmpty()
                val nextPos = book.parsePosURI(next)
                val result = StringBuilder()
                if (nextHref != null) {
                    result.append(book.getTextByHref(href, nextHref))
                } else {
                    result.append(book.getSectionText(section))
                }
                while (true) {
                    if (nextPos != null && section.frags.any { it.index == nextPos.fid }) {
                        break
                    }
                    section = section.next ?: break
                    if (!section.linear) continue
                    if (section.href == nextHref) break
                    if (book.sectionIdMap[section.index] != null) break
                    result.append(book.getSectionText(section))
                }
                result.toString()
            }

            else -> ""
        }

        private fun extractBlocks(
            book: MobiBook,
            html: String,
            images: MutableMap<String, ByteArray>,
        ): List<ReaderBlock> {
            if (html.isBlank()) return emptyList()
            val document = Jsoup.parse(html)
            document.select("script, style, title, [style*=display:none]").remove()
            document.select("img[recindex]").forEach { image ->
                val recindex = image.attr("recindex")
                image.clearAttributes()
                image.attr("src", "recindex:$recindex")
            }

            val blocks = mutableListOf<ReaderBlock>()
            val text = StringBuilder()

            fun flushText() {
                val normalized = text.toString()
                    .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                    .replace(Regex("\\n{2,}"), "\n")
                    .trim()
                text.setLength(0)
                normalized
                    .split('\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { blocks += ReaderBlock.Text(it) }
            }

            fun resourceBytes(source: String): ByteArray? = when (book) {
                is KF6Book -> book.getResourceByHref(source)
                is KF8Book -> book.getResourceByHref(source)
                else -> null
            }

            fun addImage(element: Element) {
                flushText()
                val source = element.attr("src").trim()
                if (source.isBlank()) return
                val data = runCatching { resourceBytes(source) }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
                images.putIfAbsent(source, data)
                blocks += ReaderBlock.Image(
                    source = source,
                    intrinsicWidthPx = bounds.outWidth,
                    intrinsicHeightPx = bounds.outHeight,
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

            document.body().childNodes().forEach(::visit)
            flushText()
            return blocks
        }

        private fun decodeBoundedBitmap(
            data: ByteArray,
            maxWidth: Int,
            maxHeight: Int,
        ): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (
                bounds.outWidth / (sampleSize * 2) >= maxWidth &&
                bounds.outHeight / (sampleSize * 2) >= maxHeight
            ) {
                sampleSize *= 2
            }

            val decoded = BitmapFactory.decodeByteArray(
                data,
                0,
                data.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: return null
            val scale = minOf(
                1f,
                maxWidth.toFloat() / decoded.width.coerceAtLeast(1),
                maxHeight.toFloat() / decoded.height.coerceAtLeast(1),
            )
            if (scale >= 1f) return decoded
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== decoded) decoded.recycle()
            return scaled
        }
    }
}
