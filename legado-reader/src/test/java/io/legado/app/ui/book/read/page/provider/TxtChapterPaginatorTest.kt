package io.legado.app.ui.book.read.page.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TxtChapterPaginatorTest {

    private val config = ReaderLayoutConfig(
        contentTextSizePx = 36f,
        titleTextSizePx = 44f,
        lineSpacingMultiplier = 1.4f,
        paragraphSpacingPx = 16f,
        paddingLeftPx = 32,
        paddingTopPx = 40,
        paddingRightPx = 32,
        paddingBottomPx = 40,
    )

    @Test
    fun longChineseChapterIsSplitIntoRealPages() {
        val paragraph = "这是用于验证阅读分页的中文段落。标点不会被随意挤到不合理的位置。"
        val chapter = TxtChapterPaginator.paginate(
            title = "第一章 开始",
            paragraphs = List(40) { paragraph },
            chapterIndex = 0,
            chapterCount = 3,
            viewportWidthPx = 720,
            viewportHeightPx = 1080,
            config = config,
        )

        assertThat(chapter.pages.size).isGreaterThan(1)
        chapter.pages.forEachIndexed { index, page ->
            assertThat(page.index).isEqualTo(index)
            assertThat(page.pageSize).isEqualTo(chapter.pages.size)
            assertThat(page.textLines).isNotEmpty()
            assertThat(page.height).isAtLeast(1080f)
        }
    }

    @Test
    fun firstPageContainsChapterTitleGeometry() {
        val chapter = TxtChapterPaginator.paginate(
            title = "第一章 开始",
            paragraphs = listOf("正文第一段。"),
            chapterIndex = 0,
            chapterCount = 1,
            viewportWidthPx = 720,
            viewportHeightPx = 1080,
            config = config,
        )

        assertThat(chapter.pages.first().textLines.first().isTitle).isTrue()
        assertThat(chapter.pages.first().textLines.first().text).contains("第一章")
    }

    @Test
    fun generatedCharacterGeometryStaysInsideHorizontalMargins() {
        val chapter = TxtChapterPaginator.paginate(
            title = "",
            paragraphs = listOf("一段用于检查每个字符横向坐标的文本。"),
            chapterIndex = 0,
            chapterCount = 1,
            viewportWidthPx = 720,
            viewportHeightPx = 1080,
            config = config.copy(showChapterTitle = false),
        )

        val chars = chapter.pages.first().textLines.flatMap { it.textChars }
        assertThat(chars).isNotEmpty()
        assertThat(chars.minOf { it.start }).isAtLeast(config.paddingLeftPx.toFloat())
        assertThat(chars.maxOf { it.end }).isAtMost((720 - config.paddingRightPx).toFloat() + 1f)
    }
}
