/*
 * UMD adapter built on Legado's vendored modules/book UMD parser.
 * Source: https://github.com/LegadoTeam/legado @ 62003ce732a7e30602754d28996da7f98b9ea296
 * Licensed under GNU GPL v3. VaultShelf adapter: 2026-09-18.
 */
package io.legado.app.ui.book.read.umd

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ReaderBlock
import io.legado.app.ui.book.read.page.provider.ReaderLayoutConfig
import io.legado.app.ui.book.read.page.provider.RichChapterPaginator
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import me.ag2s.umdlib.umd.UmdReader

data class UmdSourceChapter(
    val title: String,
    val content: String,
)

data class UmdReaderPosition(
    val chapterIndex: Int,
    val pageIndex: Int,
)

class LegadoUmdDocument private constructor(
    val title: String,
    val chapters: List<UmdSourceChapter>,
    private val coverBytes: ByteArray?,
) : Closeable {

    fun paginate(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        config: ReaderLayoutConfig,
    ): List<TextChapter> = chapters.mapIndexed { index, chapter ->
        val blocks = chapter.content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split(Regex("\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { ReaderBlock.Text(it) }
            .ifEmpty { listOf(ReaderBlock.Text("")) }

        RichChapterPaginator.paginate(
            title = chapter.title,
            blocks = blocks,
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
    ): UmdReaderPosition {
        if (paginated.isEmpty()) return UmdReaderPosition(0, 0)
        val safe = (progression ?: 0f).coerceIn(0f, 1f)
        val totalPages = paginated.sumOf { it.pages.size.coerceAtLeast(1) }.coerceAtLeast(1)
        var target = (safe * (totalPages - 1)).toInt().coerceIn(0, totalPages - 1)
        paginated.forEachIndexed { chapterIndex, chapter ->
            val count = chapter.pages.size.coerceAtLeast(1)
            if (target < count) {
                return UmdReaderPosition(
                    chapterIndex,
                    target.coerceAtMost(chapter.lastIndex.coerceAtLeast(0)),
                )
            }
            target -= count
        }
        val lastChapter = paginated.lastIndex
        return UmdReaderPosition(
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

    override fun close() = Unit

    companion object {
        fun open(file: File): Result<LegadoUmdDocument> = runCatching {
            val book = FileInputStream(file).use { input ->
                UmdReader().read(input)
            }
            val sourceChapters = book.chapters.titles.indices.map { index ->
                val title = book.chapters.getTitle(index)
                    .takeIf { it.isNotBlank() }
                    ?: "Chapter ${index + 1}"
                UmdSourceChapter(
                    title = title,
                    content = book.chapters.getContentString(index),
                )
            }
            require(sourceChapters.isNotEmpty()) { "UMD contains no readable chapters" }
            val title = book.header.title
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            LegadoUmdDocument(
                title = title,
                chapters = sourceChapters,
                coverBytes = book.cover?.coverData,
            )
        }
    }
}
