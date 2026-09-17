/*
 * Adapted from Legado / 阅读 3.0 TextPageFactory.
 * Sources:
 * - https://github.com/LegadoTeam/legado
 * - https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.provider

import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.api.PageFactory
import io.legado.app.ui.book.read.page.entities.TextPage

class TextPageFactory(dataSource: DataSource) : PageFactory<TextPage>(dataSource) {

    override fun hasPrev(): Boolean = with(dataSource) {
        hasPrevChapter() || pageIndex > 0
    }

    override fun hasNext(): Boolean = with(dataSource) {
        hasNextChapter() || currentChapter?.isLastIndex(pageIndex) != true
    }

    override fun hasNextPlus(): Boolean = with(dataSource) {
        hasNextChapter() || pageIndex < (currentChapter?.pageSize ?: 1) - 2
    }

    override fun moveToFirst() {
        dataSource.setPageIndex(0)
    }

    override fun moveToLast() = with(dataSource) {
        val last = currentChapter?.lastIndex ?: 0
        setPageIndex(last.coerceAtLeast(0))
    }

    override fun moveToNext(upContent: Boolean): Boolean = with(dataSource) {
        if (!hasNext()) return false
        if (currentChapter?.isLastIndex(pageIndex) == true) {
            if (!moveToNextChapter()) return false
            setPageIndex(0)
        } else {
            setPageIndex(pageIndex + 1)
        }
        if (upContent) upContent(resetPageOffset = false)
        return true
    }

    override fun moveToPrev(upContent: Boolean): Boolean = with(dataSource) {
        if (!hasPrev()) return false
        if (pageIndex <= 0) {
            if (!moveToPrevChapter(toLastPage = true)) return false
            setPageIndex(currentChapter?.lastIndex?.coerceAtLeast(0) ?: 0)
        } else {
            setPageIndex(pageIndex - 1)
        }
        if (upContent) upContent(resetPageOffset = false)
        return true
    }

    override val curPage: TextPage
        get() = with(dataSource) {
            currentChapter?.page(pageIndex) ?: TextPage(title = currentChapter?.title.orEmpty())
        }

    override val nextPage: TextPage
        get() = with(dataSource) {
            currentChapter?.let { chapter ->
                if (pageIndex < chapter.pageSize - 1) {
                    return@with chapter.page(pageIndex + 1) ?: TextPage(title = chapter.title)
                }
            }
            if (!hasNextChapter()) return@with TextPage(text = "")
            nextChapter?.page(0) ?: TextPage(title = nextChapter?.title.orEmpty())
        }

    override val prevPage: TextPage
        get() = with(dataSource) {
            if (pageIndex > 0) {
                currentChapter?.let { chapter ->
                    return@with chapter.page(pageIndex - 1) ?: TextPage(title = chapter.title)
                }
            }
            prevChapter?.lastPage ?: TextPage(title = prevChapter?.title.orEmpty())
        }

    override val nextPlusPage: TextPage
        get() = with(dataSource) {
            currentChapter?.let { chapter ->
                if (pageIndex < chapter.pageSize - 2) {
                    return@with chapter.page(pageIndex + 2) ?: TextPage(title = chapter.title)
                }
                nextChapter?.let { next ->
                    if (pageIndex < chapter.pageSize - 1) {
                        return@with next.page(0) ?: TextPage(title = next.title)
                    }
                    return@with next.page(1) ?: next.page(0) ?: TextPage(title = next.title)
                }
            }
            TextPage()
        }
}
