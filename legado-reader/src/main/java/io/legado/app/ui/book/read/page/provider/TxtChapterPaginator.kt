/*
 * TXT-only extraction of Legado / 阅读 3.0's ChapterProvider pagination path.
 * Sources:
 * - https://github.com/LegadoTeam/legado
 * - https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.provider

import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextChar
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.utils.toStringArray
import kotlin.math.max

/**
 * Legado-style TXT paginator with Chinese-aware line breaking and page geometry.
 *
 * Online-book/image branches from Legado's ChapterProvider are intentionally omitted: this
 * module is used only for local TXT books. The pagination, punctuation handling, indentation,
 * full justification, chapter-title layout and per-character geometry remain the same class of
 * algorithm as Legado's mature reader.
 */
object TxtChapterPaginator {

    fun paginate(
        title: String,
        paragraphs: List<String>,
        chapterIndex: Int,
        chapterCount: Int,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        config: ReaderLayoutConfig,
    ): TextChapter {
        require(viewportWidthPx > config.paddingLeftPx + config.paddingRightPx) {
            "Horizontal reader padding leaves no content width"
        }
        require(viewportHeightPx > config.paddingTopPx + config.paddingBottomPx) {
            "Vertical reader padding leaves no content height"
        }

        val visibleWidth = viewportWidthPx - config.paddingLeftPx - config.paddingRightPx
        val visibleHeight = viewportHeightPx - config.paddingTopPx - config.paddingBottomPx
        val contentPaint = textPaint(config.contentTextSizePx, config)
        val titlePaint = textPaint(config.titleTextSizePx, config).apply {
            isFakeBoldText = true
        }

        val pages = arrayListOf<TextPage>()
        var currentPage = newPage(title, chapterIndex, chapterCount)
        pages += currentPage
        var pageText = StringBuilder()
        var pageStartPosition = 0
        var y = 0f

        fun finishPage(createNext: Boolean) {
            currentPage.text = pageText.toString()
            currentPage.chapterPosition = pageStartPosition
            currentPage.height = max(
                viewportHeightPx.toFloat(),
                config.paddingTopPx + y + config.paddingBottomPx,
            )
            pageStartPosition += currentPage.text.length
            if (createNext) {
                currentPage = newPage(title, chapterIndex, chapterCount)
                pages += currentPage
                pageText = StringBuilder()
                y = 0f
            }
        }

        fun addBlock(
            blockText: String,
            paint: TextPaint,
            isTitle: Boolean,
            countTowardProgress: Boolean,
        ) {
            if (blockText.isEmpty()) return
            val layout = createLayout(blockText, paint, visibleWidth, config.useZhLayout)
            val lineHeight = textHeight(paint)

            for (lineIndex in 0 until layout.lineCount) {
                if (y + lineHeight > visibleHeight && currentPage.textLines.isNotEmpty()) {
                    finishPage(createNext = true)
                }

                val start = layout.getLineStart(lineIndex)
                val end = if (lineIndex + 1 < layout.lineCount) {
                    layout.getLineStart(lineIndex + 1)
                } else {
                    blockText.length
                }
                if (end <= start) continue
                val words = blockText.substring(start, end)
                val isLastLine = lineIndex == layout.lineCount - 1
                val line = TextLine(
                    text = if (isLastLine) "$words\n" else words,
                    isTitle = isTitle,
                )

                val initialX = if (isTitle && config.centerChapterTitle) {
                    ((visibleWidth - paint.measureText(words)) / 2f).coerceAtLeast(0f)
                } else {
                    0f
                }
                appendCharacters(
                    line = line,
                    words = words.toStringArray(),
                    paint = paint,
                    absoluteLeft = config.paddingLeftPx.toFloat(),
                    initialX = initialX,
                    availableWidth = visibleWidth.toFloat(),
                    justify = config.fullJustify && !isTitle && !isLastLine,
                )
                line.updateTopBottom(config.paddingTopPx, y, paint)
                currentPage.textLines += line

                if (countTowardProgress) {
                    pageText.append(words)
                    if (isLastLine) pageText.append('\n')
                }
                y += lineHeight * config.lineSpacingMultiplier
            }
        }

        if (config.showChapterTitle && title.isNotBlank()) {
            y += config.titleTopSpacingPx
            addBlock(
                blockText = title.trim(),
                paint = titlePaint,
                isTitle = true,
                countTowardProgress = false,
            )
            y += config.titleBottomSpacingPx
        }

        paragraphs.forEach { paragraph ->
            val normalized = paragraph.trimEnd()
            if (normalized.isBlank()) {
                y += config.paragraphSpacingPx
            } else {
                val display = if (config.paragraphIndent.isNotEmpty()) {
                    config.paragraphIndent + normalized.trimStart()
                } else {
                    normalized
                }
                addBlock(
                    blockText = display,
                    paint = contentPaint,
                    isTitle = false,
                    countTowardProgress = true,
                )
                y += config.paragraphSpacingPx
            }
        }

        finishPage(createNext = false)

        pages.forEachIndexed { index, page ->
            page.index = index
            page.pageSize = pages.size
            page.chapterIndex = chapterIndex
            page.chapterSize = chapterCount
            page.title = title
        }

        return TextChapter(
            position = chapterIndex,
            title = title,
            pages = pages,
            chaptersSize = chapterCount,
        )
    }

    private fun newPage(title: String, chapterIndex: Int, chapterCount: Int) = TextPage(
        title = title,
        chapterIndex = chapterIndex,
        chapterSize = chapterCount,
    )

    private fun textPaint(sizePx: Float, config: ReaderLayoutConfig): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx
            typeface = config.typeface
        }

    private fun textHeight(paint: TextPaint): Float {
        val metrics = paint.fontMetrics
        return metrics.descent - metrics.ascent
    }

    @Suppress("DEPRECATION")
    private fun createLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        useZhLayout: Boolean,
    ): Layout = if (useZhLayout) {
        ZhLayout(text, paint, width)
    } else {
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
    }

    private fun appendCharacters(
        line: TextLine,
        words: Array<String>,
        paint: TextPaint,
        absoluteLeft: Float,
        initialX: Float,
        availableWidth: Float,
        justify: Boolean,
    ) {
        if (words.isEmpty()) return
        val measured = words.sumOf { paint.measureText(it).toDouble() }.toFloat()
        val gap = if (justify && words.size > 1) {
            ((availableWidth - measured) / (words.size - 1)).coerceAtLeast(0f)
        } else {
            0f
        }
        var x = initialX
        words.forEachIndexed { index, char ->
            val charWidth = paint.measureText(char)
            val start = absoluteLeft + x
            val end = start + charWidth
            line.textChars += TextChar(charData = char, start = start, end = end)
            x += charWidth
            if (index != words.lastIndex) x += gap
        }
    }
}
