/*
 * Adapted from Legado / 阅读 3.0 HorizontalPageDelegate.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.delegate

import android.view.MotionEvent
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory

abstract class HorizontalPageDelegate(readView: ReadView) : PageDelegate(readView) {
    protected var curRecorder = CanvasRecorderFactory.create()
    protected var prevRecorder = CanvasRecorderFactory.create()
    protected var nextRecorder = CanvasRecorderFactory.create()
    private val slopSquare: Int get() = readView.pageSlopSquare2

    override fun setDirection(direction: PageDirection) {
        super.setDirection(direction)
        setBitmap()
    }

    open fun setBitmap() {
        when (mDirection) {
            PageDirection.PREV -> {
                prevPage.screenshot(prevRecorder)
                curPage.screenshot(curRecorder)
            }
            PageDirection.NEXT -> {
                nextPage.screenshot(nextRecorder)
                curPage.screenshot(curRecorder)
            }
            PageDirection.NONE -> Unit
        }
    }

    fun updateRecorders() {
        curRecorder.recycle()
        prevRecorder.recycle()
        nextRecorder.recycle()
        curRecorder = CanvasRecorderFactory.create()
        prevRecorder = CanvasRecorderFactory.create()
        nextRecorder = CanvasRecorderFactory.create()
    }

    override fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> abortAnim()
            MotionEvent.ACTION_MOVE -> onScroll(event)
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> onAnimStart(readView.defaultAnimationSpeed)
        }
    }

    private fun onScroll(event: MotionEvent) {
        val pointerUp = event.actionMasked == MotionEvent.ACTION_POINTER_UP
        val skipIndex = if (pointerUp) event.actionIndex else -1
        var sumX = 0f
        var sumY = 0f
        val count = event.pointerCount
        for (i in 0 until count) {
            if (skipIndex == i) continue
            sumX += event.getX(i)
            sumY += event.getY(i)
        }
        val divisor = if (pointerUp) count - 1 else count
        if (divisor <= 0) return
        val focusX = sumX / divisor
        val focusY = sumY / divisor

        if (!isMoved) {
            val deltaX = (focusX - startX).toInt()
            val deltaY = (focusY - startY).toInt()
            val distance = deltaX * deltaX + deltaY * deltaY
            isMoved = distance > slopSquare
            if (isMoved) {
                if (focusX - startX > 0) {
                    if (!hasPrev()) {
                        noNext = true
                        return
                    }
                    setDirection(PageDirection.PREV)
                } else {
                    if (!hasNext()) {
                        noNext = true
                        return
                    }
                    setDirection(PageDirection.NEXT)
                }
                readView.setStartPoint(focusX, focusY, invalidate = false)
            }
        }

        if (isMoved) {
            val delta = focusX - startX
            if (mDirection == PageDirection.NEXT && delta > 0) {
                if (!hasPrev()) {
                    noNext = true
                    return
                }
                setDirection(PageDirection.PREV)
                readView.setStartPoint(focusX, focusY, invalidate = false)
            } else if (mDirection == PageDirection.PREV && delta < 0) {
                if (!hasNext()) {
                    noNext = true
                    return
                }
                setDirection(PageDirection.NEXT)
                readView.setStartPoint(focusX, focusY, invalidate = false)
            }
            isCancel = if (mDirection == PageDirection.NEXT) focusX > lastX else focusX < lastX
            isRunning = true
            readView.setTouchPoint(focusX, focusY)
        }
    }

    override fun abortAnim() {
        isStarted = false
        isMoved = false
        isRunning = false
        if (!scroller.isFinished) {
            readView.isAbortAnim = true
            scroller.abortAnimation()
            if (!isCancel) {
                readView.fillPage(mDirection)
                readView.invalidate()
            }
        } else {
            readView.isAbortAnim = false
        }
    }

    override fun nextPageByAnim(animationSpeed: Int) {
        abortAnim()
        if (!hasNext()) return
        setDirection(PageDirection.NEXT)
        val y = if (startY > viewHeight / 2f) viewHeight * 0.9f else 1f
        readView.setStartPoint(viewWidth * 0.9f, y, invalidate = false)
        readView.setTouchPoint(viewWidth * 0.9f, y, invalidate = false)
        onAnimStart(animationSpeed)
    }

    override fun prevPageByAnim(animationSpeed: Int) {
        abortAnim()
        if (!hasPrev()) return
        setDirection(PageDirection.PREV)
        readView.setStartPoint(0f, viewHeight.toFloat(), invalidate = false)
        readView.setTouchPoint(0f, viewHeight.toFloat(), invalidate = false)
        onAnimStart(animationSpeed)
    }

    override fun onDestroy() {
        super.onDestroy()
        prevRecorder.recycle()
        curRecorder.recycle()
        nextRecorder.recycle()
    }
}
