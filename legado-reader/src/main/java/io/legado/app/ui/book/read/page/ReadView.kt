/*
 * TXT-only adaptation of Legado / 阅读 3.0 ReadView.
 * Sources:
 * - https://github.com/LegadoTeam/legado
 * - https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import io.legado.app.constant.PageAnim
import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.delegate.CoverPageDelegate
import io.legado.app.ui.book.read.page.delegate.NoAnimPageDelegate
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.delegate.ScrollPageDelegate
import io.legado.app.ui.book.read.page.delegate.SimulationPageDelegate
import io.legado.app.ui.book.read.page.delegate.SlidePageDelegate
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ReaderLayoutConfig
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import kotlin.math.abs

class ReadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), DataSource {

    interface Callback {
        fun onMenuRequested()
        fun onPositionChanged(chapterIndex: Int, pageIndex: Int, page: TextPage)
        fun onBoundary(direction: PageDirection)
        fun onInteraction() = Unit
    }

    var callback: Callback? = null
    var tapZones: ReaderTapZones = ReaderTapZones()
    val pageFactory: TextPageFactory = TextPageFactory(this)

    var pageDelegate: PageDelegate? = null
        private set(value) {
            field?.onDestroy()
            field = value
            value?.setViewSize(width, height)
        }

    override var isScroll: Boolean = false
        private set

    val prevPage = PageView(context)
    val curPage = PageView(context)
    val nextPage = PageView(context)

    val defaultAnimationSpeed = 300
    var startX: Float = 0f
    var startY: Float = 0f
    var lastX: Float = 0f
    var lastY: Float = 0f
    var touchX: Float = 0f
    var touchY: Float = 0f
    var isAbortAnim = false

    var pageBackgroundColor: Int = Color.WHITE
        private set

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    val pageSlopSquare2: Int = touchSlop * touchSlop

    private val tapRects = Array(9) { RectF() }
    private var moved = false
    private var pressed = false
    private var verticalOffset = 0f

    private var chapters: List<TextChapter> = emptyList()
    private var chapterCursor: Int = 0
    override var pageIndex: Int = 0
        private set

    private var layoutConfig = ReaderLayoutConfig(
        contentTextSizePx = 38f,
        titleTextSizePx = 46f,
    )
    private var textColor: Int = Color.rgb(35, 35, 35)
    private var titleColor: Int = textColor
    private var selectionColor: Int = Color.argb(72, 0, 95, 184)

    init {
        clipChildren = false
        addView(prevPage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(curPage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(nextPage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setBackgroundColor(pageBackgroundColor)
        setWillNotDraw(false)
        setPageAnimation(PageAnim.slidePageAnim)
    }

    fun setBook(
        chapters: List<TextChapter>,
        chapterIndex: Int = 0,
        pageIndex: Int = 0,
    ) {
        this.chapters = chapters
        chapterCursor = chapterIndex.coerceIn(0, chapters.lastIndex.coerceAtLeast(0))
        this.pageIndex = pageIndex.coerceIn(0, (currentChapter?.lastIndex ?: 0).coerceAtLeast(0))
        verticalOffset = 0f
        updateContent(notify = true)
    }

    fun configurePages(
        config: ReaderLayoutConfig,
        textColor: Int,
        titleColor: Int = textColor,
        selectionColor: Int = Color.argb(72, 0, 95, 184),
        backgroundColor: Int = Color.WHITE,
    ) {
        layoutConfig = config
        this.textColor = textColor
        this.titleColor = titleColor
        this.selectionColor = selectionColor
        this.pageBackgroundColor = backgroundColor
        setBackgroundColor(backgroundColor)
        listOf(prevPage, curPage, nextPage).forEach {
            it.configure(config, textColor, titleColor, selectionColor, backgroundColor)
        }
        invalidateRecorders()
    }

    fun setPageAnimation(@PageAnim.Anim animation: Int) {
        isScroll = animation == PageAnim.scrollPageAnim
        pageDelegate = when (animation) {
            PageAnim.coverPageAnim -> CoverPageDelegate(this)
            PageAnim.slidePageAnim -> SlidePageDelegate(this)
            PageAnim.simulationPageAnim -> SimulationPageDelegate(this)
            PageAnim.scrollPageAnim -> ScrollPageDelegate(this)
            PageAnim.noAnim -> NoAnimPageDelegate(this)
            else -> SlidePageDelegate(this)
        }
        verticalOffset = 0f
        updateChildPositions()
        updateContent(notify = false)
    }

    override val currentChapter: TextChapter?
        get() = chapters.getOrNull(chapterCursor)

    override val nextChapter: TextChapter?
        get() = chapters.getOrNull(chapterCursor + 1)

    override val prevChapter: TextChapter?
        get() = chapters.getOrNull(chapterCursor - 1)

    override fun setPageIndex(index: Int) {
        pageIndex = index.coerceIn(0, (currentChapter?.lastIndex ?: 0).coerceAtLeast(0))
    }

    override fun hasNextChapter(): Boolean = chapterCursor < chapters.lastIndex

    override fun hasPrevChapter(): Boolean = chapterCursor > 0

    override fun moveToNextChapter(): Boolean {
        if (!hasNextChapter()) return false
        chapterCursor++
        pageIndex = 0
        return true
    }

    override fun moveToPrevChapter(toLastPage: Boolean): Boolean {
        if (!hasPrevChapter()) return false
        chapterCursor--
        pageIndex = if (toLastPage) currentChapter?.lastIndex?.coerceAtLeast(0) ?: 0 else 0
        return true
    }

    override fun upContent(relativePosition: Int, resetPageOffset: Boolean) {
        if (resetPageOffset) verticalOffset = 0f
        updateContent(notify = true)
    }

    fun fillPage(direction: PageDirection): Boolean {
        val movedPage = when (direction) {
            PageDirection.PREV -> pageFactory.moveToPrev(upContent = false)
            PageDirection.NEXT -> pageFactory.moveToNext(upContent = false)
            PageDirection.NONE -> false
        }
        if (movedPage) {
            verticalOffset = 0f
            updateContent(notify = true)
        }
        return movedPage
    }

    fun notifyBoundary(direction: PageDirection) {
        callback?.onBoundary(direction)
    }

    fun setStartPoint(x: Float, y: Float, invalidate: Boolean = true) {
        startX = x
        startY = y
        lastX = x
        lastY = y
        touchX = x
        touchY = y
        if (invalidate) invalidate()
    }

    fun setTouchPoint(x: Float, y: Float, invalidate: Boolean = true) {
        lastX = touchX
        lastY = touchY
        touchX = x
        touchY = y
        if (invalidate) invalidate()
        pageDelegate?.onScroll()
    }

    fun scrollByPixels(deltaY: Int) {
        if (!isScroll || height <= 0 || deltaY == 0) return
        verticalOffset += deltaY

        while (verticalOffset <= -height && pageFactory.hasNext()) {
            verticalOffset += height
            pageFactory.moveToNext(upContent = false)
            updateContent(notify = true)
        }
        while (verticalOffset >= height && pageFactory.hasPrev()) {
            verticalOffset -= height
            pageFactory.moveToPrev(upContent = false)
            updateContent(notify = true)
        }

        if (!pageFactory.hasPrev() && verticalOffset > 0f) verticalOffset = 0f
        if (!pageFactory.hasNext() && verticalOffset < 0f) verticalOffset = 0f
        updateChildPositions()
        invalidate()
    }

    fun screenfulOffset(next: Boolean): Int = if (next) -height else height

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setTapRects(w, h)
        pageDelegate?.setViewSize(w, h)
        updateChildPositions()
        invalidateRecorders()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!isScroll) pageDelegate?.onDraw(canvas)
    }

    override fun computeScroll() {
        pageDelegate?.computeScroll()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        callback?.onInteraction()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                moved = false
                setStartPoint(event.x, event.y, invalidate = false)
                pageDelegate?.onTouch(event)
                pageDelegate?.onDown()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved) {
                    moved = abs(startX - event.x) > touchSlop || abs(startY - event.y) > touchSlop
                }
                if (moved) pageDelegate?.onTouch(event)
            }
            MotionEvent.ACTION_UP -> {
                if (!pressed) return true
                pressed = false
                if (moved) pageDelegate?.onTouch(event) else handleTap(event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL -> {
                if (pressed && moved) pageDelegate?.onTouch(event)
                pressed = false
            }
        }
        return true
    }

    fun destroy() {
        pageDelegate?.onDestroy()
        pageDelegate = null
    }

    private fun updateContent(notify: Boolean) {
        if (chapters.isEmpty()) {
            val empty = TextPage()
            prevPage.setContent(empty)
            curPage.setContent(empty)
            nextPage.setContent(empty)
            return
        }
        prevPage.setContent(pageFactory.prevPage)
        curPage.setContent(pageFactory.curPage)
        nextPage.setContent(pageFactory.nextPage)
        updateChildPositions()
        invalidateRecorders()
        if (notify) callback?.onPositionChanged(chapterCursor, pageIndex, pageFactory.curPage)
    }

    private fun invalidateRecorders() {
        (pageDelegate as? io.legado.app.ui.book.read.page.delegate.HorizontalPageDelegate)
            ?.updateRecorders()
    }

    private fun updateChildPositions() {
        if (width <= 0 || height <= 0) return
        if (isScroll) {
            prevPage.x = 0f
            curPage.x = 0f
            nextPage.x = 0f
            prevPage.translationY = verticalOffset - height
            curPage.translationY = verticalOffset
            nextPage.translationY = verticalOffset + height
        } else {
            prevPage.x = -width.toFloat()
            curPage.x = 0f
            nextPage.x = width.toFloat()
            prevPage.translationY = 0f
            curPage.translationY = 0f
            nextPage.translationY = 0f
        }
    }

    private fun setTapRects(width: Int, height: Int) {
        val thirdW = width / 3f
        val thirdH = height / 3f
        for (row in 0..2) {
            for (column in 0..2) {
                tapRects[row * 3 + column].set(
                    column * thirdW,
                    row * thirdH,
                    if (column == 2) width.toFloat() else (column + 1) * thirdW,
                    if (row == 2) height.toFloat() else (row + 1) * thirdH,
                )
            }
        }
    }

    private fun handleTap(x: Float, y: Float) {
        val index = tapRects.indexOfFirst { it.contains(x, y) }
        val action = when (index) {
            0 -> tapZones.topLeft
            1 -> tapZones.topCenter
            2 -> tapZones.topRight
            3 -> tapZones.middleLeft
            4 -> tapZones.middleCenter
            5 -> tapZones.middleRight
            6 -> tapZones.bottomLeft
            7 -> tapZones.bottomCenter
            8 -> tapZones.bottomRight
            else -> ReaderTapAction.NONE
        }
        when (action) {
            ReaderTapAction.MENU -> callback?.onMenuRequested()
            ReaderTapAction.NEXT_PAGE -> pageDelegate?.nextPageByAnim(defaultAnimationSpeed)
            ReaderTapAction.PREV_PAGE -> pageDelegate?.prevPageByAnim(defaultAnimationSpeed)
            ReaderTapAction.NEXT_CHAPTER -> {
                if (moveToNextChapter()) updateContent(notify = true)
                else notifyBoundary(PageDirection.NEXT)
            }
            ReaderTapAction.PREV_CHAPTER -> {
                if (moveToPrevChapter(toLastPage = false)) updateContent(notify = true)
                else notifyBoundary(PageDirection.PREV)
            }
            ReaderTapAction.NONE -> Unit
        }
    }
}
