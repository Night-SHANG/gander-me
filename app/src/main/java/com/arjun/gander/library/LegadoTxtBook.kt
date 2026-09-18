package com.arjun.gander.library

import io.legado.app.ui.book.read.page.entities.TextChapter as LegadoTextChapter
import io.legado.app.ui.book.read.page.provider.ReaderLayoutConfig
import io.legado.app.ui.book.read.page.provider.TxtChapterPaginator
import kotlin.math.roundToInt

data class LegadoReaderPosition(
    val chapterIndex: Int,
    val pageIndex: Int,
)

/**
 * Bridges VaultShelf's stable source-character progress model to Legado's chapter/page model.
 *
 * VaultShelf keeps character offsets in library metadata so changing font size, margins or page
 * turn mode does not permanently bind a book to one pagination. The Legado pages are rebuilt for
 * the current screen and typography, then this adapter resolves the closest page again.
 */
data class LegadoTxtBook(
    val sourceText: String,
    val sourceChapters: List<TxtChapter>,
    val chapters: List<LegadoTextChapter>,
) {
    fun positionForOffset(offset: Int): LegadoReaderPosition {
        if (chapters.isEmpty() || sourceChapters.isEmpty()) return LegadoReaderPosition(0, 0)
        val chapterIndex = TxtChapterParser.chapterIndexForOffset(sourceChapters, offset)
            .coerceIn(0, chapters.lastIndex)
        val sourceChapter = sourceChapters[chapterIndex]
        val legadoChapter = chapters[chapterIndex]
        if (legadoChapter.pages.isEmpty()) return LegadoReaderPosition(chapterIndex, 0)

        val sourceStart = sourceChapter.contentStartOffset.coerceIn(0, sourceText.length)
        val sourceEnd = sourceChapter.endOffset.coerceIn(sourceStart, sourceText.length)
        val sourceLength = (sourceEnd - sourceStart).coerceAtLeast(1)
        val localSourceOffset = (offset - sourceStart).coerceIn(0, sourceLength)
        val fraction = localSourceOffset.toFloat() / sourceLength.toFloat()

        val generatedLength = generatedLength(legadoChapter).coerceAtLeast(1)
        val targetGeneratedOffset = (generatedLength * fraction).roundToInt()
        val pageIndex = legadoChapter.pages
            .indexOfLast { it.chapterPosition <= targetGeneratedOffset }
            .coerceAtLeast(0)
            .coerceAtMost(legadoChapter.lastIndex)
        return LegadoReaderPosition(chapterIndex, pageIndex)
    }

    fun offsetForPosition(chapterIndex: Int, pageIndex: Int): Int {
        if (chapters.isEmpty() || sourceChapters.isEmpty()) return 0
        val safeChapterIndex = chapterIndex.coerceIn(0, chapters.lastIndex)
            .coerceAtMost(sourceChapters.lastIndex)
        val sourceChapter = sourceChapters[safeChapterIndex]
        val legadoChapter = chapters[safeChapterIndex]

        val sourceStart = sourceChapter.contentStartOffset.coerceIn(0, sourceText.length)
        val sourceEnd = sourceChapter.endOffset.coerceIn(sourceStart, sourceText.length)
        val sourceLength = (sourceEnd - sourceStart).coerceAtLeast(0)
        if (sourceLength == 0 || legadoChapter.pages.isEmpty()) {
            return sourceChapter.startOffset.coerceIn(0, sourceText.length)
        }

        val page = legadoChapter.page(pageIndex.coerceIn(0, legadoChapter.lastIndex))
            ?: legadoChapter.pages.first()
        val generatedLength = generatedLength(legadoChapter).coerceAtLeast(1)
        val fraction = page.chapterPosition.toFloat() / generatedLength.toFloat()
        return (sourceStart + (sourceLength * fraction).roundToInt())
            .coerceIn(sourceChapter.startOffset, sourceChapter.endOffset)
            .coerceIn(0, sourceText.length)
    }

    private fun generatedLength(chapter: LegadoTextChapter): Int =
        chapter.pages.sumOf { it.text.length }
}

object LegadoTxtBookBuilder {
    fun build(
        text: String,
        sourceChapters: List<TxtChapter>,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        config: ReaderLayoutConfig,
        startLabel: String,
    ): LegadoTxtBook {
        val effectiveChapters = sourceChapters.ifEmpty {
            listOf(TxtChapter(null, 0, 0, text.length))
        }
        val legadoChapters = effectiveChapters.mapIndexed { index, chapter ->
            TxtChapterPaginator.paginate(
                title = chapter.title?.takeIf { it.isNotBlank() } ?: startLabel,
                paragraphs = TxtChapterParser.paragraphs(text, chapter).map { it.text },
                chapterIndex = index,
                chapterCount = effectiveChapters.size,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                config = config,
            )
        }
        return LegadoTxtBook(
            sourceText = text,
            sourceChapters = effectiveChapters,
            chapters = legadoChapters,
        )
    }
}
