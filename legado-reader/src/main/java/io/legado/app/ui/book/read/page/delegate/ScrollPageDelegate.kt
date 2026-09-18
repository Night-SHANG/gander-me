/*
 * TXT-only adaptation of Legado / 阅读 3.0 ScrollPageDelegate.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import android.view.MotionEvent
import android.view.VelocityTracker
import io.legado.app.ui.book.read.page.ReadView

class ScrollPageDelegate(readView: ReadView) : PageDelegate(readView) {
    private val velocityDuration = 1000
    private val velocityTracker: VelocityTracker = VelocityTracker.obtain()
    private val slopSquare: Int get() = readView.pageSlopSquare2

    var noAnim: Boolean = false

    override fun onAnimStart(animationSpeed: Int) {
        fling(
            0,
            touchY.toInt(),
            0,
            velocityTracker.yVelocity.toInt(),
            0,
            0,
            -10 * viewHeight,
            10 * viewHeight,
        )
    }

    override fun onAnimStop() = Unit

    override fun onTouch(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val index = event.pointerCount - 1
            readView.setStartPoint(event.getX(index), event.getY(index), invalidate = false)
        } else if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            readView.setStartPoint(event.x, event.y, invalidate = false)
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                abortAnim()
                velocityTracker.clear()
                velocityTracker.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> onScroll(event)
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                velocityTracker.addMovement(event)
                velocityTracker.computeCurrentVelocity(velocityDuration)
                onAnimStart(readView.defaultAnimationSpeed)
            }
        }
    }

    override fun onScroll() {
        readView.scrollByPixels((touchY - lastY).toInt())
    }

    override fun onDraw(canvas: Canvas) = Unit

    private fun onScroll(event: MotionEvent) {
        velocityTracker.addMovement(event)
        velocityTracker.computeCurrentVelocity(velocityDuration)
        val index = event.pointerCount - 1
        val pointX = event.getX(index)
        val pointY = event.getY(index)

        if (!isMoved) {
            val deltaX = (pointX - startX).toInt()
            val deltaY = (pointY - startY).toInt()
            isMoved = deltaX * deltaX + deltaY * deltaY > slopSquare
            if (isMoved) readView.setStartPoint(pointX, pointY, invalidate = false)
        }
        if (isMoved) {
            isRunning = true
            readView.setTouchPoint(pointX, pointY, invalidate = false)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            readView.setTouchPoint(scroller.currX.toFloat(), scroller.currY.toFloat(), invalidate = false)
        } else if (isStarted) {
            onAnimStop()
            stopScroll()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        velocityTracker.recycle()
    }

    override fun abortAnim() {
        isStarted = false
        isMoved = false
        isRunning = false
        if (!scroller.isFinished) {
            readView.isAbortAnim = true
            scroller.abortAnimation()
        } else {
            readView.isAbortAnim = false
        }
    }

    override fun nextPageByAnim(animationSpeed: Int) {
        if (!hasNext()) return
        if (noAnim) {
            readView.scrollByPixels(readView.screenfulOffset(next = true))
            return
        }
        readView.setStartPoint(0f, 0f, invalidate = false)
        readView.setTouchPoint(0f, 0f, invalidate = false)
        startScroll(0, 0, 0, readView.screenfulOffset(next = true), animationSpeed)
    }

    override fun prevPageByAnim(animationSpeed: Int) {
        if (!hasPrev()) return
        if (noAnim) {
            readView.scrollByPixels(readView.screenfulOffset(next = false))
            return
        }
        readView.setStartPoint(0f, 0f, invalidate = false)
        readView.setTouchPoint(0f, 0f, invalidate = false)
        startScroll(0, 0, 0, readView.screenfulOffset(next = false), animationSpeed)
    }
}
