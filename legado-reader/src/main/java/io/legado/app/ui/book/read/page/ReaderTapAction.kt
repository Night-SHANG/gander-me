/*
 * VaultShelf representation of Legado / 阅读 3.0's configurable nine-zone click actions.
 * Legado concepts are GPL-3.0; VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page

enum class ReaderTapAction {
    MENU,
    NEXT_PAGE,
    PREV_PAGE,
    NEXT_CHAPTER,
    PREV_CHAPTER,
    NONE,
}

data class ReaderTapZones(
    val topLeft: ReaderTapAction = ReaderTapAction.PREV_PAGE,
    val topCenter: ReaderTapAction = ReaderTapAction.MENU,
    val topRight: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
    val middleLeft: ReaderTapAction = ReaderTapAction.PREV_PAGE,
    val middleCenter: ReaderTapAction = ReaderTapAction.MENU,
    val middleRight: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
    val bottomLeft: ReaderTapAction = ReaderTapAction.PREV_PAGE,
    val bottomCenter: ReaderTapAction = ReaderTapAction.MENU,
    val bottomRight: ReaderTapAction = ReaderTapAction.NEXT_PAGE,
)
