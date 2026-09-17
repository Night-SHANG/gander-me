package com.arjun.gander

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LocalLibraryRepository
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class LibraryEpubContractTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vaultshelf_library", Context.MODE_PRIVATE)
            .edit { clear() }
        File(context.filesDir, "library").deleteRecursively()
    }

    @Test
    fun epubProgressUsesPublicationProgression() {
        val book = LibraryBook(
            id = "epub",
            title = "Example",
            storedFileName = "epub.epub",
            format = BookFormat.EPUB,
            sizeBytes = 10L,
            totalCharacters = 0,
            addedAtEpochMillis = 1L,
            lastOpenedAtEpochMillis = 2L,
            readingOffset = 0,
            readingLocatorJson = "{\"href\":\"chapter.xhtml\"}",
            publicationProgression = 0.625f,
        )

        assertThat(book.progressFraction).isWithin(0.0001f).of(0.625f)
        assertThat(book.progressPercent).isEqualTo(62)
    }

    @Test
    fun oldTxtMetadataStillDecodesWithoutEpubFields() = runBlocking {
        context.getSharedPreferences("vaultshelf_library", Context.MODE_PRIVATE)
            .edit {
                putString(
                    "book:legacy",
                    """{"id":"legacy","title":"Legacy","storedFileName":"legacy.txt","format":"TXT","sizeBytes":12,"totalCharacters":100,"addedAtEpochMillis":1,"lastOpenedAtEpochMillis":2,"readingOffset":25}""",
                )
            }

        val book = LocalLibraryRepository(context).getBook("legacy")

        assertThat(book).isNotNull()
        assertThat(book!!.format).isEqualTo(BookFormat.TXT)
        assertThat(book.readingOffset).isEqualTo(25)
        assertThat(book.readingLocatorJson).isNull()
        assertThat(book.publicationProgression).isNull()
        assertThat(book.progressPercent).isEqualTo(25)
    }

    @Test
    fun epubImportCreatesPrivateCopyAndPersistsFormat() = runBlocking {
        val source = File(context.cacheDir, "sample.epub").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4))
        }
        val repository = LocalLibraryRepository(context)

        val book = repository.importEpub(Uri.fromFile(source))
        val stored = repository.bookFile(book.id)

        assertThat(book.format).isEqualTo(BookFormat.EPUB)
        assertThat(book.storedFileName).endsWith(".epub")
        assertThat(stored.canonicalPath).startsWith(File(context.filesDir, "library").canonicalPath)
        assertThat(stored.readBytes()).isEqualTo(source.readBytes())
        assertThat(repository.getBook(book.id)?.format).isEqualTo(BookFormat.EPUB)
    }

    @Test
    fun epubProgressRoundTripsLocatorAndProgression() = runBlocking {
        val source = File(context.cacheDir, "progress.epub").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 9, 8, 7, 6))
        }
        val repository = LocalLibraryRepository(context)
        val imported = repository.importEpub(Uri.fromFile(source))
        val locator = """{"href":"chapter-2.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":0.4}}"""

        val updated = repository.updateEpubProgress(imported.id, locator, 0.4f)
        val reloaded = repository.getBook(imported.id)

        assertThat(updated?.readingLocatorJson).isEqualTo(locator)
        assertThat(updated?.publicationProgression).isWithin(0.0001f).of(0.4f)
        assertThat(reloaded?.readingLocatorJson).isEqualTo(locator)
        assertThat(reloaded?.publicationProgression).isWithin(0.0001f).of(0.4f)
    }
}
