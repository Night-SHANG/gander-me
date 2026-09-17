/*
 * Adapted from Legado / 阅读 3.0.
 * Source: https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.entities

data class TextChar(
    val charData: String,
    var start: Float,
    var end: Float,
    var selected: Boolean = false,
    var isImage: Boolean = false,
    var isSearchResult: Boolean = false,
) {
    fun isTouch(x: Float): Boolean = x > start && x < end
}
