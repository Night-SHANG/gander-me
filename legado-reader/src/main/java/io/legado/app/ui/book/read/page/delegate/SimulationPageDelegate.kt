/*
 * Adapted from Legado / 阅读 3.0 SimulationPageDelegate.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.delegate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Region
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.MotionEvent
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

@Suppress("DEPRECATION")
class SimulationPageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {
    private var mTouchX = 0.1f
    private var mTouchY = 0.1f

    private var mCornerX = 1
    private var mCornerY = 1
    private val mPath0 = Path()
    private val mPath1 = Path()

    private val mBezierStart1 = PointF()
    private val mBezierControl1 = PointF()
    private val mBezierVertex1 = PointF()
    private var mBezierEnd1 = PointF()

    private val mBezierStart2 = PointF()
    private val mBezierControl2 = PointF()
    private val mBezierVertex2 = PointF()
    private var mBezierEnd2 = PointF()

    private var mMiddleX = 0f
    private var mMiddleY = 0f
    private var mDegrees = 0f
    private var mTouchToCornerDis = 0f

    private val mColorMatrixFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    private val mMatrix = Matrix()
    private val mMatrixArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f)

    private var mIsRtOrLb = false
    private var mMaxLength = hypot(viewWidth.toDouble(), viewHeight.toDouble()).toFloat()

    private val mBackShadowDrawableLR: GradientDrawable
    private val mBackShadowDrawableRL: GradientDrawable
    private val mFolderShadowDrawableLR: GradientDrawable
    private val mFolderShadowDrawableRL: GradientDrawable
    private val mFrontShadowDrawableHBT: GradientDrawable
    private val mFrontShadowDrawableHTB: GradientDrawable
    private val mFrontShadowDrawableVLR: GradientDrawable
    private val mFrontShadowDrawableVRL: GradientDrawable

    private val mPaint = Paint().apply { style = Paint.Style.FILL }

    private var curBitmap: Bitmap? = null
    private var prevBitmap: Bitmap? = null
    private var nextBitmap: Bitmap? = null
    private val bitmapCanvas = Canvas()

    init {
        val folderColors = intArrayOf(0x333333, -0x4fcccccd)
        mFolderShadowDrawableRL = GradientDrawable(
            GradientDrawable.Orientation.RIGHT_LEFT,
            folderColors,
        ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
        mFolderShadowDrawableLR = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            folderColors,
        ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

        val backColors = intArrayOf(-0xeeeeef, 0x111111)
        mBackShadowDrawableRL = GradientDrawable(
            GradientDrawable.Orientation.RIGHT_LEFT,
            backColors,
        ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
        mBackShadowDrawableLR = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            backColors,
        ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }

        val frontColors = intArrayOf(-0x7feeeeef, 0x111111)
        mFrontShadowDrawableVLR = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            frontColors,
        ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
        mFrontShadowDrawableVRL = GradientDrawable(
            GradientDrawable.Orientation.RIGHT_LEFT,
            frontColors,
        ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
        mFrontShadowDrawableHTB = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            frontColors,
        ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
        mFrontShadowDrawableHBT = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            frontColors,
        ).apply { gradientType = GradientDrawable.LINEAR_GRADIENT }
    }

    override fun setBitmap() {
        when (mDirection) {
            PageDirection.PREV -> {
                prevBitmap = prevPage.screenshotBitmap(prevBitmap, bitmapCanvas)
                curBitmap = curPage.screenshotBitmap(curBitmap, bitmapCanvas)
            }
            PageDirection.NEXT -> {
                nextBitmap = nextPage.screenshotBitmap(nextBitmap, bitmapCanvas)
                curBitmap = curPage.screenshotBitmap(curBitmap, bitmapCanvas)
            }
            PageDirection.NONE -> Unit
        }
    }

    override fun setViewSize(width: Int, height: Int) {
        super.setViewSize(width, height)
        mMaxLength = hypot(viewWidth.toDouble(), viewHeight.toDouble()).toFloat()
    }

    override fun onTouch(event: MotionEvent) {
        super.onTouch(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> calcCornerXY(event.x, event.y)
            MotionEvent.ACTION_MOVE -> {
                if ((startY > viewHeight / 3f && startY < viewHeight * 2f / 3f) ||
                    mDirection == PageDirection.PREV
                ) {
                    readView.touchY = viewHeight.toFloat()
                }
                if (startY > viewHeight / 3f && startY < viewHeight / 2f &&
                    mDirection == PageDirection.NEXT
                ) {
                    readView.touchY = 1f
                }
            }
        }
    }

    override fun setDirection(direction: PageDirection) {
        super.setDirection(direction)
        when (direction) {
            PageDirection.PREV -> {
                if (startX > viewWidth / 2f) {
                    calcCornerXY(startX, viewHeight.toFloat())
                } else {
                    calcCornerXY(viewWidth - startX, viewHeight.toFloat())
                }
            }
            PageDirection.NEXT -> {
                if (viewWidth / 2f > startX) {
                    calcCornerXY(viewWidth - startX, startY)
                }
            }
            PageDirection.NONE -> Unit
        }
    }

    override fun onAnimStart(animationSpeed: Int) {
        var dx: Float
        val dy: Float
        if (isCancel) {
            dx = if (mCornerX > 0 && mDirection == PageDirection.NEXT) {
                viewWidth - touchX
            } else {
                -touchX
            }
            if (mDirection != PageDirection.NEXT) {
                dx = -(viewWidth + touchX)
            }
            dy = if (mCornerY > 0) viewHeight - touchY else -touchY
        } else {
            dx = if (mCornerX > 0 && mDirection == PageDirection.NEXT) {
                -(viewWidth + touchX)
            } else {
                viewWidth - touchX
            }
            dy = if (mCornerY > 0) viewHeight - touchY else 1f - touchY
        }
        startScroll(touchX.toInt(), touchY.toInt(), dx.toInt(), dy.toInt(), animationSpeed)
    }

    override fun onAnimStop() {
        if (!isCancel) readView.fillPage(mDirection)
    }

    override fun onDraw(canvas: Canvas) {
        if (!isRunning) return
        when (mDirection) {
            PageDirection.NEXT -> {
                calcPoints()
                drawCurrentPageArea(canvas, curBitmap)
                drawNextPageAreaAndShadow(canvas, nextBitmap)
                drawCurrentPageShadow(canvas)
                drawCurrentBackArea(canvas, curBitmap)
            }
            PageDirection.PREV -> {
                calcPoints()
                drawCurrentPageArea(canvas, prevBitmap)
                drawNextPageAreaAndShadow(canvas, curBitmap)
                drawCurrentPageShadow(canvas)
                drawCurrentBackArea(canvas, prevBitmap)
            }
            PageDirection.NONE -> Unit
        }
    }

    private fun drawCurrentBackArea(canvas: Canvas, bitmap: Bitmap?) {
        bitmap ?: return
        val i = ((mBezierStart1.x + mBezierControl1.x) / 2).toInt()
        val f1 = abs(i - mBezierControl1.x)
        val i1 = ((mBezierStart2.y + mBezierControl2.y) / 2).toInt()
        val f2 = abs(i1 - mBezierControl2.y)
        val f3 = min(f1, f2)

        mPath1.reset()
        mPath1.moveTo(mBezierVertex2.x, mBezierVertex2.y)
        mPath1.lineTo(mBezierVertex1.x, mBezierVertex1.y)
        mPath1.lineTo(mBezierEnd1.x, mBezierEnd1.y)
        mPath1.lineTo(mTouchX, mTouchY)
        mPath1.lineTo(mBezierEnd2.x, mBezierEnd2.y)
        mPath1.close()

        val folderShadow: GradientDrawable
        val left: Int
        val right: Int
        if (mIsRtOrLb) {
            left = (mBezierStart1.x - 1).toInt()
            right = (mBezierStart1.x + f3 + 1).toInt()
            folderShadow = mFolderShadowDrawableLR
        } else {
            left = (mBezierStart1.x - f3 - 1).toInt()
            right = (mBezierStart1.x + 1).toInt()
            folderShadow = mFolderShadowDrawableRL
        }

        canvas.save()
        canvas.clipPath(mPath0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipPath(mPath1)
        } else {
            canvas.clipPath(mPath1, Region.Op.INTERSECT)
        }

        mPaint.colorFilter = mColorMatrixFilter
        val distance = hypot(
            mCornerX - mBezierControl1.x.toDouble(),
            mBezierControl2.y - mCornerY.toDouble(),
        ).toFloat().coerceAtLeast(0.1f)
        val f8 = (mCornerX - mBezierControl1.x) / distance
        val f9 = (mBezierControl2.y - mCornerY) / distance
        mMatrixArray[0] = 1 - 2 * f9 * f9
        mMatrixArray[1] = 2 * f8 * f9
        mMatrixArray[3] = mMatrixArray[1]
        mMatrixArray[4] = 1 - 2 * f8 * f8
        mMatrix.reset()
        mMatrix.setValues(mMatrixArray)
        mMatrix.preTranslate(-mBezierControl1.x, -mBezierControl1.y)
        mMatrix.postTranslate(mBezierControl1.x, mBezierControl1.y)
        canvas.drawColor(readView.pageBackgroundColor)
        canvas.drawBitmap(bitmap, mMatrix, mPaint)
        mPaint.colorFilter = null

        canvas.rotate(mDegrees, mBezierStart1.x, mBezierStart1.y)
        folderShadow.setBounds(
            left,
            mBezierStart1.y.toInt(),
            right,
            (mBezierStart1.y + mMaxLength).toInt(),
        )
        folderShadow.draw(canvas)
        canvas.restore()
    }

    private fun drawCurrentPageShadow(canvas: Canvas) {
        val degree = if (mIsRtOrLb) {
            Math.PI / 4 - atan2(
                mBezierControl1.y - mTouchY,
                mTouchX - mBezierControl1.x,
            )
        } else {
            Math.PI / 4 - atan2(
                mTouchY - mBezierControl1.y,
                mTouchX - mBezierControl1.x,
            )
        }
        val d1 = 25f * 1.414 * cos(degree)
        val d2 = 25f * 1.414 * sin(degree)
        val x = (mTouchX + d1).toFloat()
        val y = if (mIsRtOrLb) (mTouchY + d2).toFloat() else (mTouchY - d2).toFloat()

        mPath1.reset()
        mPath1.moveTo(x, y)
        mPath1.lineTo(mTouchX, mTouchY)
        mPath1.lineTo(mBezierControl1.x, mBezierControl1.y)
        mPath1.lineTo(mBezierStart1.x, mBezierStart1.y)
        mPath1.close()
        canvas.save()
        clipOutsideCurrentPage(canvas)
        canvas.clipPath(mPath1, Region.Op.INTERSECT)

        var leftX: Int
        var rightX: Int
        var currentShadow: GradientDrawable
        if (mIsRtOrLb) {
            leftX = mBezierControl1.x.toInt()
            rightX = (mBezierControl1.x + 25).toInt()
            currentShadow = mFrontShadowDrawableVLR
        } else {
            leftX = (mBezierControl1.x - 25).toInt()
            rightX = (mBezierControl1.x + 1).toInt()
            currentShadow = mFrontShadowDrawableVRL
        }
        var rotateDegrees = Math.toDegrees(
            atan2(
                mTouchX - mBezierControl1.x,
                mBezierControl1.y - mTouchY,
            ).toDouble(),
        ).toFloat()
        canvas.rotate(rotateDegrees, mBezierControl1.x, mBezierControl1.y)
        currentShadow.setBounds(
            leftX,
            (mBezierControl1.y - mMaxLength).toInt(),
            rightX,
            mBezierControl1.y.toInt(),
        )
        currentShadow.draw(canvas)
        canvas.restore()

        mPath1.reset()
        mPath1.moveTo(x, y)
        mPath1.lineTo(mTouchX, mTouchY)
        mPath1.lineTo(mBezierControl2.x, mBezierControl2.y)
        mPath1.lineTo(mBezierStart2.x, mBezierStart2.y)
        mPath1.close()
        canvas.save()
        clipOutsideCurrentPage(canvas)
        canvas.clipPath(mPath1)

        if (mIsRtOrLb) {
            leftX = mBezierControl2.y.toInt()
            rightX = (mBezierControl2.y + 25).toInt()
            currentShadow = mFrontShadowDrawableHTB
        } else {
            leftX = (mBezierControl2.y - 25).toInt()
            rightX = (mBezierControl2.y + 1).toInt()
            currentShadow = mFrontShadowDrawableHBT
        }
        rotateDegrees = Math.toDegrees(
            atan2(
                mBezierControl2.y - mTouchY,
                mBezierControl2.x - mTouchX,
            ).toDouble(),
        ).toFloat()
        canvas.rotate(rotateDegrees, mBezierControl2.x, mBezierControl2.y)
        val temp = if (mBezierControl2.y < 0) {
            (mBezierControl2.y - viewHeight).toDouble()
        } else {
            mBezierControl2.y.toDouble()
        }
        val hmg = hypot(mBezierControl2.x.toDouble(), temp)
        if (hmg > mMaxLength) {
            currentShadow.setBounds(
                (mBezierControl2.x - 25 - hmg).toInt(),
                leftX,
                (mBezierControl2.x + mMaxLength - hmg).toInt(),
                rightX,
            )
        } else {
            currentShadow.setBounds(
                (mBezierControl2.x - mMaxLength).toInt(),
                leftX,
                mBezierControl2.x.toInt(),
                rightX,
            )
        }
        currentShadow.draw(canvas)
        canvas.restore()
    }

    private fun drawNextPageAreaAndShadow(canvas: Canvas, bitmap: Bitmap?) {
        bitmap ?: return
        mPath1.reset()
        mPath1.moveTo(mBezierStart1.x, mBezierStart1.y)
        mPath1.lineTo(mBezierVertex1.x, mBezierVertex1.y)
        mPath1.lineTo(mBezierVertex2.x, mBezierVertex2.y)
        mPath1.lineTo(mBezierStart2.x, mBezierStart2.y)
        mPath1.lineTo(mCornerX.toFloat(), mCornerY.toFloat())
        mPath1.close()

        mDegrees = Math.toDegrees(
            atan2(
                (mBezierControl1.x - mCornerX).toDouble(),
                mBezierControl2.y - mCornerY.toDouble(),
            ),
        ).toFloat()

        val leftX: Int
        val rightX: Int
        val backShadow: GradientDrawable
        if (mIsRtOrLb) {
            leftX = mBezierStart1.x.toInt()
            rightX = (mBezierStart1.x + mTouchToCornerDis / 4).toInt()
            backShadow = mBackShadowDrawableLR
        } else {
            leftX = (mBezierStart1.x - mTouchToCornerDis / 4).toInt()
            rightX = mBezierStart1.x.toInt()
            backShadow = mBackShadowDrawableRL
        }

        canvas.save()
        canvas.clipPath(mPath0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipPath(mPath1)
        } else {
            canvas.clipPath(mPath1, Region.Op.INTERSECT)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.rotate(mDegrees, mBezierStart1.x, mBezierStart1.y)
        backShadow.setBounds(
            leftX,
            mBezierStart1.y.toInt(),
            rightX,
            (mMaxLength + mBezierStart1.y).toInt(),
        )
        backShadow.draw(canvas)
        canvas.restore()
    }

    private fun drawCurrentPageArea(canvas: Canvas, bitmap: Bitmap?) {
        bitmap ?: return
        mPath0.reset()
        mPath0.moveTo(mBezierStart1.x, mBezierStart1.y)
        mPath0.quadTo(
            mBezierControl1.x,
            mBezierControl1.y,
            mBezierEnd1.x,
            mBezierEnd1.y,
        )
        mPath0.lineTo(mTouchX, mTouchY)
        mPath0.lineTo(mBezierEnd2.x, mBezierEnd2.y)
        mPath0.quadTo(
            mBezierControl2.x,
            mBezierControl2.y,
            mBezierStart2.x,
            mBezierStart2.y,
        )
        mPath0.lineTo(mCornerX.toFloat(), mCornerY.toFloat())
        mPath0.close()

        canvas.save()
        clipOutsideCurrentPage(canvas)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.restore()
    }

    private fun clipOutsideCurrentPage(canvas: Canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutPath(mPath0)
        } else {
            canvas.clipPath(mPath0, Region.Op.XOR)
        }
    }

    private fun calcCornerXY(x: Float, y: Float) {
        mCornerX = if (x <= viewWidth / 2f) 0 else viewWidth
        mCornerY = if (y <= viewHeight / 2f) 0 else viewHeight
        mIsRtOrLb = (mCornerX == 0 && mCornerY == viewHeight) ||
            (mCornerY == 0 && mCornerX == viewWidth)
    }

    private fun calcPoints() {
        mTouchX = touchX
        mTouchY = touchY

        mMiddleX = (mTouchX + mCornerX) / 2
        mMiddleY = (mTouchY + mCornerY) / 2

        val denominatorX = (mCornerX - mMiddleX).takeUnless { it == 0f } ?: 0.1f
        mBezierControl1.x =
            mMiddleX - (mCornerY - mMiddleY) * (mCornerY - mMiddleY) / denominatorX
        mBezierControl1.y = mCornerY.toFloat()
        mBezierControl2.x = mCornerX.toFloat()

        val denominatorY = mCornerY - mMiddleY
        mBezierControl2.y = if (denominatorY == 0f) {
            mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / 0.1f
        } else {
            mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / denominatorY
        }

        mBezierStart1.x = mBezierControl1.x - (mCornerX - mBezierControl1.x) / 2
        mBezierStart1.y = mCornerY.toFloat()

        if (mTouchX > 0 && mTouchX < viewWidth &&
            (mBezierStart1.x < 0 || mBezierStart1.x > viewWidth)
        ) {
            if (mBezierStart1.x < 0) {
                mBezierStart1.x = viewWidth - mBezierStart1.x
            }
            val f1 = abs(mCornerX - mTouchX).coerceAtLeast(0.1f)
            val f2 = viewWidth * f1 / mBezierStart1.x
            mTouchX = abs(mCornerX - f2)
            val f3 = abs(mCornerX - mTouchX) * abs(mCornerY - mTouchY) / f1
            mTouchY = abs(mCornerY - f3)

            mMiddleX = (mTouchX + mCornerX) / 2
            mMiddleY = (mTouchY + mCornerY) / 2
            val adjustedX = (mCornerX - mMiddleX).takeUnless { it == 0f } ?: 0.1f
            mBezierControl1.x =
                mMiddleX - (mCornerY - mMiddleY) * (mCornerY - mMiddleY) / adjustedX
            mBezierControl1.y = mCornerY.toFloat()
            mBezierControl2.x = mCornerX.toFloat()
            val adjustedY = mCornerY - mMiddleY
            mBezierControl2.y = if (adjustedY == 0f) {
                mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / 0.1f
            } else {
                mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / adjustedY
            }
            mBezierStart1.x = mBezierControl1.x - (mCornerX - mBezierControl1.x) / 2
        }

        mBezierStart2.x = mCornerX.toFloat()
        mBezierStart2.y = mBezierControl2.y - (mCornerY - mBezierControl2.y) / 2

        mTouchToCornerDis = hypot(
            (mTouchX - mCornerX).toDouble(),
            (mTouchY - mCornerY).toDouble(),
        ).toFloat()

        mBezierEnd1 = getCross(
            PointF(mTouchX, mTouchY),
            mBezierControl1,
            mBezierStart1,
            mBezierStart2,
        )
        mBezierEnd2 = getCross(
            PointF(mTouchX, mTouchY),
            mBezierControl2,
            mBezierStart1,
            mBezierStart2,
        )

        mBezierVertex1.x =
            (mBezierStart1.x + 2 * mBezierControl1.x + mBezierEnd1.x) / 4
        mBezierVertex1.y =
            (2 * mBezierControl1.y + mBezierStart1.y + mBezierEnd1.y) / 4
        mBezierVertex2.x =
            (mBezierStart2.x + 2 * mBezierControl2.x + mBezierEnd2.x) / 4
        mBezierVertex2.y =
            (2 * mBezierControl2.y + mBezierStart2.y + mBezierEnd2.y) / 4
    }

    private fun getCross(p1: PointF, p2: PointF, p3: PointF, p4: PointF): PointF {
        val dx1 = (p2.x - p1.x).takeUnless { it == 0f } ?: 0.1f
        val dx2 = (p4.x - p3.x).takeUnless { it == 0f } ?: 0.1f
        val denominatorB1 = (p1.x - p2.x).takeUnless { it == 0f } ?: 0.1f
        val denominatorB2 = (p3.x - p4.x).takeUnless { it == 0f } ?: 0.1f
        val a1 = (p2.y - p1.y) / dx1
        val b1 = (p1.x * p2.y - p2.x * p1.y) / denominatorB1
        val a2 = (p4.y - p3.y) / dx2
        val b2 = (p3.x * p4.y - p4.x * p3.y) / denominatorB2
        val divisor = (a1 - a2).takeUnless { it == 0f } ?: 0.1f
        val x = (b2 - b1) / divisor
        return PointF(x, a1 * x + b1)
    }

    override fun onDestroy() {
        super.onDestroy()
        curBitmap?.takeUnless { it.isRecycled }?.recycle()
        prevBitmap?.takeUnless { it.isRecycled }?.recycle()
        nextBitmap?.takeUnless { it.isRecycled }?.recycle()
        curBitmap = null
        prevBitmap = null
        nextBitmap = null
    }
}
