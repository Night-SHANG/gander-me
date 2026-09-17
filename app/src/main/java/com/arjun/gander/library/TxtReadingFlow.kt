package com.arjun.gander.library

sealed interface TxtReadingBlock {
    val displayText: String
    val startOffset: Int

    data class ChapterHeading(
        override val displayText: String,
        override val startOffset: Int,
    ) : TxtReadingBlock

    data class Paragraph(
        override val displayText: String,
        override val startOffset: Int,
    ) : TxtReadingBlock
}

object TxtReadingFlow {

    fun build(
        text: String,
        chapters: List<TxtChapter> = TxtChapterParser.parse(text),
    ): List<TxtReadingBlock> = buildList {
        for (chapter in chapters) {
            chapter.title?.takeIf { it.isNotBlank() }?.let { title ->
                add(
                    TxtReadingBlock.ChapterHeading(
                        displayText = title,
                        startOffset = chapter.startOffset,
                    ),
                )
            }
            TxtChapterParser.paragraphs(text, chapter).forEach { paragraph ->
                add(
                    TxtReadingBlock.Paragraph(
                        displayText = paragraph.text,
                        startOffset = paragraph.startOffset,
                    ),
                )
            }
        }
    }

    fun indexForOffset(blocks: List<TxtReadingBlock>, offset: Int): Int {
        if (blocks.isEmpty()) return 0
        val safeOffset = offset.coerceAtLeast(0)
        return blocks.indexOfLast { it.startOffset <= safeOffset }.coerceAtLeast(0)
    }
}
