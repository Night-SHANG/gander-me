package com.arjun.gander.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TxtChapterParserTest {

    @Test
    fun detectsChineseChaptersAndPreface() {
        val text = "序言内容\n\n第一章 初见\n第一段\n第二段\n\n第2章 再会\n第三段"

        val chapters = TxtChapterParser.parse(text)

        assertThat(chapters).hasSize(3)
        assertThat(chapters[0].title).isNull()
        assertThat(chapters[1].title).isEqualTo("第一章 初见")
        assertThat(chapters[2].title).isEqualTo("第2章 再会")
        assertThat(text.substring(chapters[1].contentStartOffset, chapters[1].endOffset))
            .startsWith("第一段")
    }

    @Test
    fun detectsEnglishChaptersIgnoringCase() {
        val text = "CHAPTER I Arrival\nOne\nChapter 2 Leaving\nTwo"

        val chapters = TxtChapterParser.parse(text)

        assertThat(chapters.map { it.title }).containsExactly(
            "CHAPTER I Arrival",
            "Chapter 2 Leaving",
        ).inOrder()
    }

    @Test
    fun fallsBackToOneChapterWhenThereAreNoHeadings() {
        val text = "第一段\n第二段"

        val chapters = TxtChapterParser.parse(text)

        assertThat(chapters).containsExactly(TxtChapter(null, 0, 0, text.length))
    }

    @Test
    fun restoresChapterAndParagraphFromCharacterOffset() {
        val text = "第一章 A\n第一段\n第二段\n第二章 B\n第三段"
        val chapters = TxtChapterParser.parse(text)
        val secondChapter = chapters[1]
        val paragraphs = TxtChapterParser.paragraphs(text, secondChapter)
        val targetOffset = text.indexOf("第三段")

        assertThat(TxtChapterParser.chapterIndexForOffset(chapters, targetOffset)).isEqualTo(1)
        assertThat(paragraphs).containsExactly(ReaderParagraph("第三段", targetOffset))
    }
}