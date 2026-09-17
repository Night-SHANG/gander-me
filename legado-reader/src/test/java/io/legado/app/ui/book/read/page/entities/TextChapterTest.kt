package io.legado.app.ui.book.read.page.entities

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextChapterTest {

    private val chapter = TextChapter(
        position = 0,
        title = "第一章",
        pages = listOf(
            TextPage(index = 0, text = "abcd", pageSize = 3, chapterSize = 1, chapterIndex = 0),
            TextPage(index = 1, text = "efghi", pageSize = 3, chapterSize = 1, chapterIndex = 0),
            TextPage(index = 2, text = "jkl", pageSize = 3, chapterSize = 1, chapterIndex = 0),
        ),
        chaptersSize = 1,
    )

    @Test
    fun characterOffsetResolvesToLegadoPage() {
        assertThat(chapter.getPageIndexByCharIndex(0)).isEqualTo(0)
        assertThat(chapter.getPageIndexByCharIndex(4)).isEqualTo(1)
        assertThat(chapter.getPageIndexByCharIndex(9)).isEqualTo(2)
    }

    @Test
    fun unreadTextBeginsAtRequestedPage() {
        assertThat(chapter.getUnRead(1)).isEqualTo("efghijkl")
    }

    @Test
    fun readLengthMatchesPageBoundary() {
        assertThat(chapter.getReadLength(0)).isEqualTo(0)
        assertThat(chapter.getReadLength(1)).isEqualTo(4)
        assertThat(chapter.getReadLength(2)).isEqualTo(9)
    }
}
