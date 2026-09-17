package com.arjun.gander.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TxtReadingFlowTest {

    @Test
    fun flattensAllChaptersIntoOneContinuousSequence() {
        val text = "序言\n\n第一章 开始\n第一段\n第二章 继续\n第二段"
        val chapters = TxtChapterParser.parse(text)

        val blocks = TxtReadingFlow.build(text, chapters)

        assertThat(blocks.map { it.displayText }).containsExactly(
            "序言",
            "第一章 开始",
            "第一段",
            "第二章 继续",
            "第二段",
        ).inOrder()
        assertThat(blocks.map { it.startOffset }).isInOrder()
    }

    @Test
    fun restoreIndexCanLandInsideLaterChapter() {
        val text = "第一章 A\n第一段\n第二章 B\n第二段\n第三段"
        val blocks = TxtReadingFlow.build(text, TxtChapterParser.parse(text))
        val target = text.indexOf("第三段")

        val index = TxtReadingFlow.indexForOffset(blocks, target)

        assertThat(blocks[index].displayText).isEqualTo("第三段")
        assertThat(blocks[index].startOffset).isEqualTo(target)
    }

    @Test
    fun chapterHeadingKeepsWholeBookOffsetForTocJump() {
        val text = "第一章 A\n正文A\n第二章 B\n正文B"
        val chapters = TxtChapterParser.parse(text)
        val blocks = TxtReadingFlow.build(text, chapters)
        val secondChapter = chapters[1]

        val index = TxtReadingFlow.indexForOffset(blocks, secondChapter.startOffset)

        assertThat(blocks[index]).isInstanceOf(TxtReadingBlock.ChapterHeading::class.java)
        assertThat(blocks[index].startOffset).isEqualTo(secondChapter.startOffset)
    }
}
