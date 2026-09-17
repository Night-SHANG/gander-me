/*
 * Adapted from Legado / 阅读 3.0.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.api

import io.legado.app.ui.book.read.page.entities.TextChapter

/**
 * Reader data contract extracted from Legado.
 *
 * The original implementation reads and mutates global ReadBook state. VaultShelf exposes the
 * same navigation operations through this interface so the mature reader core can run without
 * Legado's book-source/network singleton.
 */
interface DataSource {
    val pageIndex: Int
    val allowPageMove: Boolean get() = true
    val currentChapter: TextChapter?
    val nextChapter: TextChapter?
    val prevChapter: TextChapter?
    val isScroll: Boolean

    fun setPageIndex(index: Int)
    fun hasNextChapter(): Boolean
    fun hasPrevChapter(): Boolean
    fun moveToNextChapter(): Boolean
    fun moveToPrevChapter(toLastPage: Boolean): Boolean
    fun upContent(relativePosition: Int = 0, resetPageOffset: Boolean = true)
}
