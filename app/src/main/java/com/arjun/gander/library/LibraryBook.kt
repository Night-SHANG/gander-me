package com.arjun.gander.library

enum class BookFormat {
    TXT,
    EPUB,
}

data class LibraryBook(
    val id: String,
    val title: String,
    val storedFileName: String,
    val format: BookFormat,
    val sizeBytes: Long,
    val totalCharacters: Int,
    val addedAtEpochMillis: Long,
    val lastOpenedAtEpochMillis: Long,
    val readingOffset: Int,
) {
    val progressFraction: Float
        get() = if (totalCharacters <= 0) {
            0f
        } else {
            readingOffset.coerceIn(0, totalCharacters).toFloat() / totalCharacters.toFloat()
        }

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt().coerceIn(0, 100)
}