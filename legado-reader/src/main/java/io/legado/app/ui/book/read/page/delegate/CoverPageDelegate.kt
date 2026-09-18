/*
 * Adapted from Legado / 阅读 3.0 CoverPageDelegate.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection

class CoverPageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {
    private val shadowDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0x66111111, 0x00000000),
    ).apply {
        gradientType = GradientDrawable.LINEAR_GRADIENT
    }

    override fun onDraw(canvas: Canvas) {
        if (!isRunning) return
        val offsetX = touchX - startX
        if ((mDirection == PageDirection.NEXT && offsetX > 0) ||
            (mDirection == PageDirection.PREV && offsetX < 0)
        ) return

        val distanceX = if (offsetX > 0) offsetX - viewWidth else offsetX + viewWidth
        if (mDirection == PageDirection.PREV) {
            if (offsetX <= viewWidth) {
                canvas.withTranslation(distanceX) {
                    prevRecorder.draw(this)
                }
                addShadow(distanceX, canvas)
            } else {
                prevRecorder.draw(canvas)
            }
        } else if (mDirection == PageDirection.NEXT) {
            val width = nextRecorder.width.toFloat().coerceAtLeast(viewWidth.toFloat())
            val height = nextRecorder.height.toFloat().coerceAtLeast(viewHeight.toFloat())
            canvas.withClip(width + offsetX, 0f, width, height) {
                nextRecorder.draw(this)
            }
            canvas.withTranslation(distanceX - viewWidth) {
                curRecorder.draw(this)
            }
            addShadow(distanceX, canvas)
        }
    }

    override fun setBitmap() {
        when (mDirection) {
            PageDirection.PREV -> prevPage.screenshot(prevRecorder)
            PageDirection.NEXT -> {
                nextPage.screenshot(nextRecorder)
                curPage.screenshot(curRecorder)
            }
            PageDirection.NONE -> Unit
        }
    }

    private fun addShadow(left: Float, canvas: Canvas) {
        if (left == 0f) return
        val dx = if (left < 0) left + viewWidth else left
        canvas.withTranslation(dx) {
            shadowDrawable.draw(this)
        }
    }

    override fun setViewSize(width: Int, height: Int) {
        super.setViewSize(width, height)
        shadowDrawable.setBounds(0, 0, 30, viewHeight)
    }

    override fun onAnimStop() {
        if (!isCancel) readView.fillPage(mDirection)
    }

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
}
