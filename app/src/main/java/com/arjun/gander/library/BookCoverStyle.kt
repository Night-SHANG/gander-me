package com.arjun.gander.library

data class BookCoverStyle(
    val title: String,
    val formatLabel: String,
    val paletteIndex: Int,
) {
    companion object {
        const val PALETTE_COUNT = 6

        fun from(book: LibraryBook): BookCoverStyle {
            val seed = "${book.title.lowercase()}|${book.format.name}"
            val paletteIndex = (seed.hashCode() and Int.MAX_VALUE) % PALETTE_COUNT
            return BookCoverStyle(
                title = book.title,
                formatLabel = book.format.name,
                paletteIndex = paletteIndex,
            )
        }
    }
}
