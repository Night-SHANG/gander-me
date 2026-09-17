/*
 * TXT-only adaptation of Legado / 阅读 3.0 PageView + ContentTextView drawing paths.
 * Sources:
 * - https://github.com/LegadoTeam/legado
 * - https://github.com/TsaiYongChuan/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.view.View
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ReaderLayoutConfig
import io.legado.app.utils.canvasrecorder.CanvasRecorder

class PageView(context: Context) : View(context) {
    var textPage: TextPage = TextPage()
        private set

    private var layoutConfig = ReaderLayoutConfig(
        contentTextSizePx = 38f,
        titleTextSizePx = 46f,
    )
    private var textColor: Int = Color.rgb(35, 35, 35)
    private var titleColor: Int = textColor
    private var selectionColor: Int = Color.argb(72, 0, 95, 184)

    private val contentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        applyPaints()
    }

    fun configure(
        config: ReaderLayoutConfig,
        textColor: Int,
        titleColor: Int = textColor,
        selectionColor: Int = Color.argb(72, 0, 95, 184),
    ) {
        this.layoutConfig = config
        this.textColor = textColor
        this.titleColor = titleColor
        this.selectionColor = selectionColor
        applyPaints()
        invalidate()
    }

    fun setContent(page: TextPage) {
        textPage = page
        contentDescription = buildString {
            if (page.title.isNotBlank()) append(page.title).append('\n')
            append(page.text)
        }
        invalidate()
    }

    fun resetPageOffset() = Unit

    fun screenshot(recorder: CanvasRecorder) {
        if (width <= 0 || height <= 0) return
        val canvas = recorder.beginRecording(width, height)
        draw(canvas)
        recorder.endRecording()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        selectionPaint.color = selectionColor
        textPage.textLines.forEach { line ->
            val paint = if (line.isTitle) titlePaint else contentPaint
            line.textChars.forEach { char ->
                if (char.selected) {
                    canvas.drawRect(
                        char.start,
                        line.lineTop,
                        char.end,
                        line.lineBottom,
                        selectionPaint,
                    )
                }
                canvas.drawText(char.charData, char.start, line.lineBase, paint)
            }
        }
    }

    private fun applyPaints() {
        contentPaint.apply {
            textSize = layoutConfig.contentTextSizePx
            typeface = layoutConfig.typeface
            color = textColor
            isFakeBoldText = false
        }
        titlePaint.apply {
            textSize = layoutConfig.titleTextSizePx
            typeface = layoutConfig.typeface
            color = titleColor
            isFakeBoldText = true
        }
    }
}
