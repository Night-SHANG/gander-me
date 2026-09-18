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
    fun markdownUsesTextProgressModel() {
        val book = LibraryBook(
            id = "markdown",
            title = "Notes",
            storedFileName = "notes.md",
            format = BookFormat.MARKDOWN,
            sizeBytes = 100L,
            totalCharacters = 200,
            addedAtEpochMillis = 0L,
            lastOpenedAtEpochMillis = 0L,
            readingOffset = 50,
        )

        assertThat(book.progressFraction).isEqualTo(0.25f)
        assertThat(book.progressPercent).isEqualTo(25)
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
}