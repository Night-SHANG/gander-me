package io.legado.app.ui.book.read.page.provider

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RichChapterPaginatorTest {

    @Test
    fun imageBlockBecomesImageLineAndSharesPageModel() {
        val chapter = RichChapterPaginator.paginate(
            title = "第一章",
            blocks = listOf(
                ReaderBlock.Text("图片前的正文。"),
                ReaderBlock.Image(
                    source = "images/picture.png",
                    intrinsicWidthPx = 800,
                    intrinsicHeightPx = 600,
                ),
                ReaderBlock.Text("图片后的正文。"),
            ),
            chapterIndex = 0,
            chapterCount = 1,
            viewportWidthPx = 1080,
            viewportHeightPx = 1920,
            config = ReaderLayoutConfig(
                contentTextSizePx = 42f,
                titleTextSizePx = 52f,
                paddingLeftPx = 48,
                paddingTopPx = 64,
                paddingRightPx = 48,
                paddingBottomPx = 64,
            ),
        )

        val imageLines = chapter.pages.flatMap { it.textLines }.filter { it.isImage }
        assertThat(imageLines).hasSize(1)
        assertThat(imageLines.single().imageSource).isEqualTo("images/picture.png")
        assertThat(imageLines.single().imageWidth).isGreaterThan(0f)
        assertThat(imageLines.single().imageHeight).isGreaterThan(0f)
        assertThat(chapter.pages.joinToString { it.text }).contains("\uFFFC")
    }
}
