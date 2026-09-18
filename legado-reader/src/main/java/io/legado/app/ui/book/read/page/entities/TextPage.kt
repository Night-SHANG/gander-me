/*
 * Adapted from Legado / 阅读 3.0 and an earlier Legado reader implementation.
 * Sources:
 * - https://github.com/LegadoTeam/legado
 * - https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.entities

import java.text.DecimalFormat
import kotlin.math.min

@Suppress("unused", "MemberVisibilityCanBePrivate")
data class TextPage(
    var index: Int = 0,
    var text: String = "",
    var title: String = "",
    val textLines: ArrayList<TextLine> = arrayListOf(),
    var pageSize: Int = 0,
    var chapterSize: Int = 0,
    var chapterIndex: Int = 0,
    var chapterPosition: Int = 0,
    var height: Float = 0f,
    var leftLineSize: Int = 0,
) {
    val lineSize: Int get() = textLines.size
    val charSize: Int get() = text.length

    fun getLine(index: Int): TextLine = textLines.getOrElse(index) { textLines.last() }

    fun getSelectStartLength(lineIndex: Int, charIndex: Int): Int {
        var length = 0
        val maxIndex = min(lineIndex, lineSize)
        for (index in 0 until maxIndex) {
            length += textLines[index].charSize
        }
        return length + charIndex
    }

    val readProgress: String
        get() {
            val formatter = DecimalFormat("0.0%")
            if (chapterSize == 0 || (pageSize == 0 && chapterIndex == 0)) {
                return "0.0%"
            }
            if (pageSize == 0) {
                return formatter.format((chapterIndex + 1.0f) / chapterSize.toDouble())
            }
            var percent = formatter.format(
                chapterIndex.toDouble() / chapterSize.toDouble() +
                    1.0 / chapterSize.toDouble() * (index + 1).toDouble() / pageSize.toDouble(),
            )
            if (percent == "100.0%" && (chapterIndex + 1 != chapterSize || index + 1 != pageSize)) {
                percent = "99.9%"
            }
            return percent
        }
}
