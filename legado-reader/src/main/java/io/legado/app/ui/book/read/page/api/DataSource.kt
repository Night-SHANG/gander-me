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
 * The original implementation reads pageIndex from Legado's global ReadBook singleton.
 * VaultShelf supplies it explicitly so the reader core stays independent of book-source,
 * networking and application-global state.
 */
interface DataSource {
    val pageIndex: Int
    val allowPageMove: Boolean get() = true
    val currentChapter: TextChapter?
    val nextChapter: TextChapter?
    val prevChapter: TextChapter?
    val isScroll: Boolean

    fun hasNextChapter(): Boolean
    fun hasPrevChapter(): Boolean
    fun upContent(relativePosition: Int = 0, resetPageOffset: Boolean = true)
}
