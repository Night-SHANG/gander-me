/*
 * Shared local-book content blocks for VaultShelf's Legado reader adaptation.
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.provider

sealed interface ReaderBlock {
    data class Text(val text: String) : ReaderBlock

    data class Image(
        val source: String,
        val intrinsicWidthPx: Int,
        val intrinsicHeightPx: Int,
    ) : ReaderBlock
}
