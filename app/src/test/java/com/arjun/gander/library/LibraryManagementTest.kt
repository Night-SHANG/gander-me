package com.arjun.gander.library

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryManagementTest {

    private lateinit var context: Context
    private lateinit var repository: LocalLibraryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vaultshelf_library", Context.MODE_PRIVATE).edit { clear() }
        File(context.filesDir, "library").deleteRecursively()
        repository = LocalLibraryRepository(context)
    }

    @Test
    fun renameBookPersistsTrimmedTitle() = runBlocking {
        val source = File(context.cacheDir, "rename.txt").apply { writeText("第一章 测试\n正文") }
        val imported = repository.importTxt(Uri.fromFile(source))

        val renamed = repository.renameBook(imported.id, "  新书名  ")
        val reloaded = repository.getBook(imported.id)

        assertThat(renamed?.title).isEqualTo("新书名")
        assertThat(reloaded?.title).isEqualTo("新书名")
    }

    @Test
    fun blankRenameKeepsExistingTitle() = runBlocking {
        val source = File(context.cacheDir, "blank.txt").apply { writeText("正文") }
        val imported = repository.importTxt(Uri.fromFile(source))

        val renamed = repository.renameBook(imported.id, "   ")

        assertThat(renamed?.title).isEqualTo(imported.title)
        assertThat(repository.getBook(imported.id)?.title).isEqualTo(imported.title)
    }

    @Test
    fun deleteBookRemovesPrivateCopyButLeavesSourceFile() = runBlocking {
        val source = File(context.cacheDir, "delete.txt").apply { writeText("需要保留的原始文件") }
        val imported = repository.importTxt(Uri.fromFile(source))
        val privateCopy = repository.bookFile(imported.id)

        val deleted = repository.deleteBook(imported.id)

        assertThat(deleted).isTrue()
        assertThat(privateCopy.exists()).isFalse()
        assertThat(repository.getBook(imported.id)).isNull()
        assertThat(source.exists()).isTrue()
        assertThat(source.readText()).isEqualTo("需要保留的原始文件")
    }

    @Test
    fun generatedCoverStyleIsStableForSameBook() {
        val book = LibraryBook(
            id = "stable-id",
            title = "九阴真经2",
            storedFileName = "stable-id.txt",
            format = BookFormat.TXT,
            sizeBytes = 100L,
            totalCharacters = 500,
            addedAtEpochMillis = 1L,
            lastOpenedAtEpochMillis = 0L,
            readingOffset = 0,
        )

        val first = BookCoverStyle.from(book)
        val second = BookCoverStyle.from(book.copy(lastOpenedAtEpochMillis = 99L))

        assertThat(first).isEqualTo(second)
        assertThat(first.title).isEqualTo("九阴真经2")
        assertThat(first.formatLabel).isEqualTo("TXT")
        assertThat(first.paletteIndex).isAtLeast(0)
        assertThat(first.paletteIndex).isLessThan(BookCoverStyle.PALETTE_COUNT)
    }
}
