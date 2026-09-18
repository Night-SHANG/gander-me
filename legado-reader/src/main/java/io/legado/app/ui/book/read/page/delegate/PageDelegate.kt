/*
 * Adapted from Legado / 阅读 3.0 PageDelegate.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.delegate

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import androidx.annotation.CallSuper
import io.legado.app.ui.book.read.page.PageView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import kotlin.math.abs

abstract class PageDelegate(protected val readView: ReadView) {
    protected val context: Context = readView.context

    protected val startX: Float get() = readView.startX
    protected val startY: Float get() = readView.startY
    protected val lastX: Float get() = readView.lastX
    protected val lastY: Float get() = readView.lastY
    protected val touchX: Float get() = readView.touchX
    protected val touchY: Float get() = readView.touchY

    protected val nextPage: PageView get() = readView.nextPage
    protected val curPage: PageView get() = readView.curPage
    protected val prevPage: PageView get() = readView.prevPage

    protected var viewWidth: Int = readView.width
    protected var viewHeight: Int = readView.height

    protected val scroller: Scroller by lazy {
        Scroller(readView.context, LinearInterpolator())
    }

    var isMoved = false
    var noNext = true
    var mDirection = PageDirection.NONE
    var isCancel = false
    var isRunning = false
    var isStarted = false

    init {
        curPage.resetPageOffset()
    }

    open fun fling(
        startX: Int,
        startY: Int,
        velocityX: Int,
        velocityY: Int,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ) {
        scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY)
        isRunning = true
        isStarted = true
        readView.invalidate()
    }

    protected fun startScroll(
        startX: Int,
        startY: Int,
        dx: Int,
        dy: Int,
        animationSpeed: Int,
    ) {
        val duration = when {
            dx != 0 && viewWidth > 0 -> (animationSpeed * abs(dx)) / viewWidth
            dy != 0 && viewHeight > 0 -> (animationSpeed * abs(dy)) / viewHeight
            else -> 0
        }.coerceAtLeast(1)
        scroller.startScroll(startX, startY, dx, dy, duration)
        isRunning = true
        isStarted = true
        readView.invalidate()
    }

    protected fun stopScroll() {
        isStarted = false
        readView.post {
            isMoved = false
            isRunning = false
            readView.invalidate()
        }
    }

    @CallSuper
    open fun setViewSize(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
    }

    open fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            readView.setTouchPoint(scroller.currX.toFloat(), scroller.currY.toFloat())
        } else if (isStarted) {
            onAnimStop()
            stopScroll()
        }
    }

    open fun onScroll() = Unit

    abstract fun abortAnim()
    abstract fun onAnimStart(animationSpeed: Int)
    abstract fun onDraw(canvas: Canvas)
    abstract fun onAnimStop()
    abstract fun nextPageByAnim(animationSpeed: Int)
    abstract fun prevPageByAnim(animationSpeed: Int)

    open fun keyTurnPage(direction: PageDirection) {
        if (isRunning) return
        when (direction) {
            PageDirection.NEXT -> nextPageByAnim(100)
            PageDirection.PREV -> prevPageByAnim(100)
            PageDirection.NONE -> Unit
        }
    }

    @CallSuper
    open fun setDirection(direction: PageDirection) {
        mDirection = direction
    }

    abstract fun onTouch(event: MotionEvent)

    fun onDown() {
        isMoved = false
        noNext = false
        isRunning = false
        isCancel = false
        setDirection(PageDirection.NONE)
    }

    fun hasPrev(): Boolean {
        val hasPrev = readView.pageFactory.hasPrev()
        if (!hasPrev) readView.notifyBoundary(PageDirection.PREV)
        return hasPrev
    }

    fun hasNext(): Boolean {
        val hasNext = readView.pageFactory.hasNext()
        if (!hasNext) readView.notifyBoundary(PageDirection.NEXT)
        return hasNext
    }

    open fun onDestroy() = Unit
}
