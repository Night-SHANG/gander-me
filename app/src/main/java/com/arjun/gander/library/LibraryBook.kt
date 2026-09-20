package com.arjun.gander.library

enum class BookFormat {
    TXT,
    EPUB,
    MARKDOWN,
    PDF,
    UMD,
    MOBI,
    AZW3,
    AZW;

    companion object {
        fun fromFileName(name: String): BookFormat? =
            when (name.substringAfterLast('.', "").lowercase()) {
                "txt" -> TXT
                "epub" -> EPUB
                "md", "markdown" -> MARKDOWN
                "pdf" -> PDF
                "umd" -> UMD
                "mobi" -> MOBI
                "azw3" -> AZW3
                "azw" -> AZW
                else -> null
            }

        fun extension(format: BookFormat): String =
            when (format) {
                TXT -> "txt"
                EPUB -> "epub"
                MARKDOWN -> "md"
                PDF -> "pdf"
                UMD -> "umd"
                MOBI -> "mobi"
                AZW3 -> "azw3"
                AZW -> "azw"
            }
    }
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
    val publicationProgression: Float? = null,
    val coverFileName: String? = null,
    val legadoBookUrl: String? = null,
    val contentSha256: String? = null,
    val sourceUri: String? = null,
) {
    val progressFraction: Float
        get() = when (format) {
            BookFormat.TXT -> publicationProgression?.coerceIn(0f, 1f)
                ?: if (totalCharacters <= 0) {
                    0f
                } else {
                    readingOffset.coerceIn(0, totalCharacters).toFloat() / totalCharacters.toFloat()
                }

            BookFormat.MARKDOWN -> if (totalCharacters <= 0) {
                0f
            } else {
                readingOffset.coerceIn(0, totalCharacters).toFloat() / totalCharacters.toFloat()
            }

            BookFormat.EPUB -> (publicationProgression ?: 0f).coerceIn(0f, 1f)

            BookFormat.PDF -> (publicationProgression ?: 0f).coerceIn(0f, 1f)

            BookFormat.UMD,
            BookFormat.MOBI,
            BookFormat.AZW3,
            BookFormat.AZW -> (publicationProgression ?: 0f).coerceIn(0f, 1f)
        }

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt().coerceIn(0, 100)
}
