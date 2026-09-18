package com.arjun.gander

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class VaultShelfUxRegressionTest {

    private val repo = File("..")

    @Test
    fun bookshelfKeepsLegadoStyleTwoThroughSixColumnChoices() {
        val source = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()

        assertThat(source).contains("DEFAULT_GRID_COLUMNS = 3")
        assertThat(source).contains("(2..6).forEach")
        assertThat(source).contains("GridCells.Fixed(gridColumns)")
        assertThat(source).doesNotContain("GridCells.Adaptive")
    }

    @Test
    fun filesBottomDestinationOpensTheExistingBrowserDirectly() {
        val source = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/shell/VaultShelfShell.kt",
        ).readText()

        assertThat(source).contains("destination == VaultShelfDestination.FILES")
        assertThat(source).contains("onOpenFiles()")
    }

    @Test
    fun txtAndEpubShareTheSameSettingsAndContentsPanels() {
        val txt = File(repo, "app/src/main/java/com/arjun/gander/TxtReaderActivity.kt").readText()
        val epub = File(repo, "app/src/main/java/com/arjun/gander/EpubReaderActivity.kt").readText()

        listOf(txt, epub).forEach { source ->
            assertThat(source).contains("MatureReaderSettingsSheet")
            assertThat(source).contains("MatureReaderContentsSheet")
        }
        assertThat(txt).doesNotContain("private fun PageModeRows")
        assertThat(epub).doesNotContain("private fun EpubPageModeRows")
    }

    @Test
    fun libraryKeepsLegadoLocalFormatsPlusMarkdown() {
        val bookModel = File(
            repo,
            "app/src/main/java/com/arjun/gander/library/LibraryBook.kt",
        ).readText()
        val library = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/library/LibraryScreen.kt",
        ).readText()

        listOf("TXT", "EPUB", "UMD", "PDF", "MOBI", "AZW3", "AZW", "MARKDOWN")
            .forEach { format ->
                assertThat(bookModel).contains("BookFormat.$format")
                assertThat(library).contains("BookFormat.$format")
            }

        assertThat(library).contains("\"md\", \"markdown\"")
        assertThat(library).contains("\"mobi\"")
        assertThat(library).contains("\"azw3\"")
        assertThat(library).contains("\"azw\"")
        assertThat(library).contains("\"umd\"")
        assertThat(library).contains("\"pdf\"")
    }

    @Test
    fun readerPanelsKeepSettingsPagedAndContentsSearchable() {
        val source = File(
            repo,
            "app/src/main/java/com/arjun/gander/ui/reader/ReaderPanels.kt",
        ).readText()

        assertThat(source).contains("ReaderSettingsPage.PAGE_TURN")
        assertThat(source).contains("ReaderSettingsPage.FONT")
        assertThat(source).contains("ReaderSettingsPage.LAYOUT")
        assertThat(source).contains("ReaderSettingsPage.THEME")
        assertThat(source).contains("vaultshelf_reader_contents_search")
        assertThat(source).contains("rememberLazyListState")
    }
}
