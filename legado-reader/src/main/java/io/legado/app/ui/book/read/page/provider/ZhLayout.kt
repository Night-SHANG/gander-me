/*
 * Adapted from Legado / 阅读 3.0 (original Chinese layout by hoodie13).
 * Source: https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.provider

import android.graphics.Rect
import android.text.Layout
import android.text.TextPaint
import io.legado.app.utils.toStringArray
import kotlin.math.max

/** Chinese-aware line breaking used by Legado to avoid awkward punctuation placement. */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class ZhLayout(
    text: String,
    textPaint: TextPaint,
    width: Int,
) : Layout(text, textPaint, width, Alignment.ALIGN_NORMAL, 0f, 0f) {
    private val defaultCapacity = 10
    private var lineStart = IntArray(defaultCapacity)
    private var lineWidth = FloatArray(defaultCapacity)
    private var internalLineCount = 0
    private val curPaint = textPaint
    private val cnCharWidth = getDesiredWidth("我", textPaint)

    private enum class BreakMode { NORMAL, BREAK_ONE_CHAR, BREAK_MORE_CHAR, CPS_1, CPS_2, CPS_3 }

    init {
        var line = 0
        val words = text.toStringArray()
        var lineW = 0f
        var previousWidth = 0f
        var length = 0
        words.forEachIndexed { index, value ->
            val charWidth = getDesiredWidth(value, curPaint)
            var breakMode = BreakMode.NORMAL
            var breakLine = false
            lineW += charWidth
            var offset = 0f
            var breakCharCount = 0

            if (lineW > width) {
                breakMode = if (index >= 1 && isPrePunctuation(words[index - 1])) {
                    if (index >= 2 && isPrePunctuation(words[index - 2])) BreakMode.CPS_2
                    else BreakMode.BREAK_ONE_CHAR
                } else if (isPostPunctuation(words[index])) {
                    if (index >= 1 && isPostPunctuation(words[index - 1])) BreakMode.CPS_1
                    else if (index >= 2 && isPrePunctuation(words[index - 2])) BreakMode.CPS_3
                    else BreakMode.BREAK_ONE_CHAR
                } else {
                    BreakMode.NORMAL
                }

                var recheck = false
                var breakIndex = 0
                if (breakMode == BreakMode.CPS_1 &&
                    (isCompressible(words[index]) || isCompressible(words[index - 1]))
                ) recheck = true
                if (breakMode == BreakMode.CPS_2 &&
                    (isCompressible(words[index - 1]) || isCompressible(words[index - 2]))
                ) recheck = true
                if (breakMode == BreakMode.CPS_3 &&
                    (isCompressible(words[index]) || isCompressible(words[index - 2]))
                ) recheck = true
                if (breakMode.ordinal > BreakMode.BREAK_MORE_CHAR.ordinal &&
                    index < words.lastIndex && isPostPunctuation(words[index + 1])
                ) recheck = true

                var breakLength = 0
                if (recheck && index > 2) {
                    breakMode = BreakMode.NORMAL
                    for (i in index downTo 1) {
                        if (i == index) {
                            breakIndex = 0
                            previousWidth = 0f
                        } else {
                            breakIndex++
                            breakLength += words[i].length
                            previousWidth += getDesiredWidth(words[i], textPaint)
                        }
                        if (!isPostPunctuation(words[i]) && !isPrePunctuation(words[i - 1])) {
                            breakMode = BreakMode.BREAK_MORE_CHAR
                            break
                        }
                    }
                }

                when (breakMode) {
                    BreakMode.NORMAL -> {
                        offset = charWidth
                        lineStart[line + 1] = length
                        breakCharCount = 1
                    }
                    BreakMode.BREAK_ONE_CHAR -> {
                        offset = charWidth + previousWidth
                        lineStart[line + 1] = length - words[index - 1].length
                        breakCharCount = 2
                    }
                    BreakMode.BREAK_MORE_CHAR -> {
                        offset = charWidth + previousWidth
                        lineStart[line + 1] = length - breakLength
                        breakCharCount = breakIndex + 1
                    }
                    BreakMode.CPS_1, BreakMode.CPS_2, BreakMode.CPS_3 -> {
                        offset = 0f
                        lineStart[line + 1] = length + value.length
                        breakCharCount = 0
                    }
                }
                breakLine = true
            }

            if (breakLine) {
                lineWidth[line] = lineW - offset
                lineW = offset
                addLineArray(++line)
            }
            if (words.lastIndex == index) {
                if (!breakLine) {
                    lineStart[line + 1] = length + value.length
                    lineWidth[line] = lineW
                    addLineArray(++line)
                } else if (breakCharCount > 0) {
                    lineStart[line + 1] = lineStart[line] + breakCharCount
                    lineWidth[line] = lineW
                    addLineArray(++line)
                }
            }
            length += value.length
            previousWidth = charWidth
        }
        internalLineCount = line
    }

    private fun addLineArray(line: Int) {
        if (lineStart.size <= line + 1) {
            lineStart = lineStart.copyOf(line + defaultCapacity)
            lineWidth = lineWidth.copyOf(line + defaultCapacity)
        }
    }

    private fun isPostPunctuation(value: String): Boolean = value in POST_PUNCTUATION
    private fun isPrePunctuation(value: String): Boolean = value in PRE_PUNCTUATION
    private fun isCompressible(value: String): Boolean = getDesiredWidth(value, curPaint) < cnCharWidth

    @Suppress("unused")
    private val gap = (cnCharWidth / 12.75).toFloat()

    @Suppress("unused")
    private fun getPostPunctuationOffset(value: String): Float {
        val textRect = Rect()
        curPaint.getTextBounds(value, 0, value.length.coerceAtMost(1), textRect)
        return max(textRect.left.toFloat() - gap, 0f)
    }

    @Suppress("unused")
    private fun getPrePunctuationOffset(value: String): Float {
        val textRect = Rect()
        curPaint.getTextBounds(value, 0, value.length.coerceAtMost(1), textRect)
        val delta = max(cnCharWidth - textRect.right.toFloat() - gap, 0f)
        return cnCharWidth / 2 - delta
    }

    private fun getDesiredWidth(value: String, paint: TextPaint): Float = paint.measureText(value)

    override fun getLineCount(): Int = internalLineCount
    override fun getLineTop(line: Int): Int = 0
    override fun getLineDescent(line: Int): Int = 0
    override fun getLineStart(line: Int): Int = lineStart[line]
    override fun getParagraphDirection(line: Int): Int = 0
    override fun getLineContainsTab(line: Int): Boolean = true
    override fun getLineDirections(line: Int): Directions? = null
    override fun getTopPadding(): Int = 0
    override fun getBottomPadding(): Int = 0
    override fun getLineWidth(line: Int): Float = lineWidth[line]
    override fun getEllipsisStart(line: Int): Int = 0
    override fun getEllipsisCount(line: Int): Int = 0

    private companion object {
        val POST_PUNCTUATION = setOf(
            "，", "。", "：", "？", "！", "、", "”", "’", "）", "》", "}", "】", ")", ">", "]",
            ",", ".", "?", "!", ":", "」", "；", ";",
        )
        val PRE_PUNCTUATION = setOf("“", "（", "《", "【", "‘", "(", "<", "[", "{", "「")
    }
}
