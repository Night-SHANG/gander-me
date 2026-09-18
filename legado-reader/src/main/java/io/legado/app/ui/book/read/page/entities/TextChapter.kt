/*
 * Adapted from Legado / 阅读 3.0 and an earlier Legado reader implementation.
 * Sources:
 * - https://github.com/LegadoTeam/legado
 * - https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.entities

import kotlin.math.min

/** Lightweight chapter/page model extracted from Legado's reader core. */
data class TextChapter(
    val position: Int,
    val title: String,
    val url: String = "",
    val pages: List<TextPage>,
    val chaptersSize: Int,
    val isVip: Boolean = false,
    val isPay: Boolean = false,
) {
    fun page(index: Int): TextPage? = pages.getOrNull(index)

    fun getPageByReadPos(readPos: Int): TextPage? = page(getPageIndexByCharIndex(readPos))

    val lastPage: TextPage? get() = pages.lastOrNull()
    val lastIndex: Int get() = pages.lastIndex
    val lastReadLength: Int get() = getReadLength(lastIndex)
    val pageSize: Int get() = pages.size

    fun isLastIndex(index: Int): Boolean = index >= pages.size - 1

    fun getReadLength(pageIndex: Int): Int {
        if (pages.isEmpty() || pageIndex < 0) return 0
        var length = 0
        val maxIndex = min(pageIndex, pages.size)
        for (index in 0 until maxIndex) {
            length += pages[index].charSize
        }
        return length
    }

    fun getNextPageLength(length: Int): Int = getReadLength(getPageIndexByCharIndex(length) + 1)

    fun getUnRead(pageIndex: Int): String = buildString {
        if (pages.isNotEmpty()) {
            for (index in pageIndex.coerceAtLeast(0)..pages.lastIndex) {
                append(pages[index].text)
            }
        }
    }

    fun getContent(): String = buildString {
        pages.forEach { append(it.text) }
    }

    /** Returns the page containing the supplied character offset. */
    fun getPageIndexByCharIndex(charIndex: Int): Int {
        if (pages.isEmpty()) return -1
        var length = 0
        pages.forEachIndexed { index, page ->
            length += page.charSize
            if (length > charIndex) return index
        }
        return pages.lastIndex
    }
}
