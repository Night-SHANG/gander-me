/*
 * Adapted from Legado / 阅读 3.0 SlidePageDelegate.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import androidx.core.graphics.withTranslation
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection

class SlidePageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {

    override fun onAnimStart(animationSpeed: Int) {
        val distanceX = when (mDirection) {
            PageDirection.NEXT -> if (isCancel) {
                var distance = viewWidth - startX + touchX
                if (distance > viewWidth) distance = viewWidth.toFloat()
                viewWidth - distance
            } else {
                -(touchX + (viewWidth - startX))
            }
            else -> if (isCancel) {
                -(touchX - startX)
            } else {
                viewWidth - (touchX - startX)
            }
        }
        startScroll(touchX.toInt(), 0, distanceX.toInt(), 0, animationSpeed)
    }

    override fun onDraw(canvas: Canvas) {
        val offsetX = touchX - startX
        if ((mDirection == PageDirection.NEXT && offsetX > 0) ||
            (mDirection == PageDirection.PREV && offsetX < 0)
        ) return
        val distanceX = if (offsetX > 0) offsetX - viewWidth else offsetX + viewWidth
        if (!isRunning) return

        if (mDirection == PageDirection.PREV) {
            canvas.withTranslation(distanceX + viewWidth) {
                curRecorder.draw(this)
            }
            canvas.withTranslation(distanceX) {
                prevRecorder.draw(this)
            }
        } else if (mDirection == PageDirection.NEXT) {
            canvas.withTranslation(distanceX) {
                nextRecorder.draw(this)
            }
            canvas.withTranslation(distanceX - viewWidth) {
                curRecorder.draw(this)
            }
        }
    }

    override fun onAnimStop() {
        if (!isCancel) readView.fillPage(mDirection)
    }
}
