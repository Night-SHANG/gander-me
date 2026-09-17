/*
 * Adapted from Legado / 阅读 3.0.
 * Source: https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.entities

import android.text.TextPaint

@Suppress("unused")
data class TextLine(
    var text: String = "",
    val textChars: ArrayList<TextChar> = arrayListOf(),
    var lineTop: Float = 0f,
    var lineBase: Float = 0f,
    var lineBottom: Float = 0f,
    val isTitle: Boolean = false,
    var isReadAloud: Boolean = false,
    var isImage: Boolean = false,
) {
    val charSize: Int get() = textChars.size
    val lineStart: Float get() = textChars.firstOrNull()?.start ?: 0f
    val lineEnd: Float get() = textChars.lastOrNull()?.end ?: 0f

    fun updateTopBottom(paddingTop: Int, y: Float, textPaint: TextPaint) {
        val metrics = textPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        lineTop = paddingTop + y
        lineBottom = lineTop + textHeight
        lineBase = lineBottom - metrics.descent
    }

    fun getTextChar(index: Int): TextChar = textChars.getOrElse(index) { textChars.last() }

    fun getTextCharReverseAt(index: Int): TextChar = textChars[textChars.lastIndex - index]

    fun getTextCharsCount(): Int = textChars.size

    fun isTouch(x: Float, y: Float, relativeOffset: Float): Boolean =
        y > lineTop + relativeOffset &&
            y < lineBottom + relativeOffset &&
            x >= lineStart &&
            x <= lineEnd
}
