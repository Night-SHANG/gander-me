/*
 * Adapted from Legado / 阅读 3.0 NoAnimPageDelegate.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import io.legado.app.ui.book.read.page.ReadView

class NoAnimPageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {
    override fun onAnimStart(animationSpeed: Int) {
        if (!isCancel) readView.fillPage(mDirection)
        stopScroll()
    }

    override fun setBitmap() = Unit
    override fun onDraw(canvas: Canvas) = Unit
    override fun onAnimStop() = Unit
}
