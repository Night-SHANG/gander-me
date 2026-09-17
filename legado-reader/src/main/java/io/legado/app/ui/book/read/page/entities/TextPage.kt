/*
 * Adapted from Legado / 阅读 3.0 and an earlier Legado reader implementation.
 * Sources:
 * - https://github.com/LegadoTeam/legado
 * - https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.entities

import java.text.DecimalFormat

@Suppress("unused", "MemberVisibilityCanBePrivate")
data class TextPage(
    var index: Int = 0,
    var text: String = "",
    var title: String = "",
    var pageSize: Int = 0,
    var chapterSize: Int = 0,
    var chapterIndex: Int = 0,
    var chapterPosition: Int = 0,
) {
    val charSize: Int get() = text.length

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
