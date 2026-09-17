/*
 * Adapted from Legado / 阅读 3.0.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.api

abstract class PageFactory<DATA>(protected val dataSource: DataSource) {
    abstract fun moveToFirst()
    abstract fun moveToLast()
    abstract fun moveToNext(upContent: Boolean): Boolean
    abstract fun moveToPrev(upContent: Boolean): Boolean

    abstract val nextPage: DATA
    abstract val prevPage: DATA
    abstract val curPage: DATA
    abstract val nextPlusPage: DATA

    abstract fun hasNext(): Boolean
    abstract fun hasPrev(): Boolean
    abstract fun hasNextPlus(): Boolean
}
