/*
 * Rich local-book paginator adapted from Legado / 阅读 3.0 TextChapterLayout.
 * Source: https://github.com/LegadoTeam/legado
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
import kotlin.math.min

/**
 * Paginates text and block images into the same TextPage model used by all page-turn delegates.
 * This mirrors Legado's local EPUB path where cleaned XHTML becomes text plus image markers before
 * entering TextChapterLayout.
 */
object RichChapterPaginator {

    fun paginate(
        title: String,
        blocks: List<ReaderBlock>,
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

        fun ensureRoom(requiredHeight: Float) {
            if (y + requiredHeight > visibleHeight && currentPage.textLines.isNotEmpty()) {
                finishPage(createNext = true)
            }
        }

        fun addTextBlock(
            blockText: String,
            paint: TextPaint,
            isTitle: Boolean,
            countTowardProgress: Boolean,
        ) {
            if (blockText.isEmpty()) return
            val layout = createLayout(blockText, paint, visibleWidth, config.useZhLayout)
            val lineHeight = textHeight(paint)

            for (lineIndex in 0 until layout.lineCount) {
                ensureRoom(lineHeight)

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

        fun addImageBlock(block: ReaderBlock.Image) {
            val sourceWidth = block.intrinsicWidthPx.coerceAtLeast(1).toFloat()
            val sourceHeight = block.intrinsicHeightPx.coerceAtLeast(1).toFloat()
            val maxWidth = visibleWidth.toFloat()
            val maxHeight = visibleHeight.toFloat()
            val scale = min(1f, min(maxWidth / sourceWidth, maxHeight / sourceHeight))
            val drawWidth = max(1f, sourceWidth * scale)
            val drawHeight = max(1f, sourceHeight * scale)

            ensureRoom(drawHeight)
            val line = TextLine(
                text = OBJECT_REPLACEMENT.toString(),
                isImage = true,
                imageSource = block.source,
                imageLeft = config.paddingLeftPx + (visibleWidth - drawWidth) / 2f,
                imageWidth = drawWidth,
                imageHeight = drawHeight,
            ).apply {
                lineTop = config.paddingTopPx + y
                lineBottom = lineTop + drawHeight
                lineBase = lineBottom
            }
            currentPage.textLines += line
            pageText.append(OBJECT_REPLACEMENT)
            y += drawHeight + config.paragraphSpacingPx
        }

        if (config.showChapterTitle && title.isNotBlank()) {
            y += config.titleTopSpacingPx
            addTextBlock(
                blockText = title.trim(),
                paint = titlePaint,
                isTitle = true,
                countTowardProgress = false,
            )
            y += config.titleBottomSpacingPx
        }

        blocks.forEach { block ->
            when (block) {
                is ReaderBlock.Text -> {
                    val normalized = block.text.trimEnd()
                    if (normalized.isBlank()) {
                        y += config.paragraphSpacingPx
                    } else {
                        val display = if (config.paragraphIndent.isNotEmpty()) {
                            config.paragraphIndent + normalized.trimStart()
                        } else {
                            normalized
                        }
                        addTextBlock(
                            blockText = display,
                            paint = contentPaint,
                            isTitle = false,
                            countTowardProgress = true,
                        )
                        y += config.paragraphSpacingPx
                    }
                }

                is ReaderBlock.Image -> addImageBlock(block)
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
        val measured = metrics.descent - metrics.ascent
        return measured.takeIf { it > 0f } ?: (paint.textSize * 1.2f).coerceAtLeast(1f)
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

    private const val OBJECT_REPLACEMENT = '\uFFFC'
}
