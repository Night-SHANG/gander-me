package com.arjun.gander.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LibraryBookTest {

    @Test
    fun progressIsClampedToBookLength() {
        val book = LibraryBook(
            id = "book",
            title = "Book",
            storedFileName = "book.txt",
            format = BookFormat.TXT,
            sizeBytes = 10L,
            totalCharacters = 100,
            addedAtEpochMillis = 0L,
            lastOpenedAtEpochMillis = 0L,
            readingOffset = 125,
        )

        assertThat(book.progressFraction).isEqualTo(1f)
        assertThat(book.progressPercent).isEqualTo(100)
    }

    @Test
    fun emptyBookHasZeroProgress() {
        val book = LibraryBook(
            id = "book",
            title = "Book",
            storedFileName = "book.txt",
            format = BookFormat.TXT,
            sizeBytes = 0L,
            totalCharacters = 0,
            addedAtEpochMillis = 0L,
            lastOpenedAtEpochMillis = 0L,
            readingOffset = 0,
        )

        assertThat(book.progressFraction).isEqualTo(0f)
        assertThat(book.progressPercent).isEqualTo(0)
    }

    @Test
    fun txtUsesPublicationProgressWhenUnifiedReadiumReaderHasSavedOne() {
        val book = LibraryBook(
            id = "book",
            title = "Book",
            storedFileName = "book.txt",
            format = BookFormat.TXT,
            sizeBytes = 10L,
            totalCharacters = 1000,
            addedAtEpochMillis = 0L,
            lastOpenedAtEpochMillis = 0L,
            readingOffset = 100,
            publicationProgression = 0.62f,
        )

        assertThat(book.progressFraction).isEqualTo(0.62f)
        assertThat(book.progressPercent).isEqualTo(62)
    }
}
