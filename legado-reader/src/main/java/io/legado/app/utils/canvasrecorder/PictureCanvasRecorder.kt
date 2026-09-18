/*
 * Picture-backed adaptation of Legado's CanvasRecorderApi23Impl.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.utils.canvasrecorder

import android.graphics.Canvas
import android.graphics.Picture

class PictureCanvasRecorder : BaseCanvasRecorder() {
    private var picture: Picture? = null

    override val width: Int get() = picture?.width ?: -1
    override val height: Int get() = picture?.height ?: -1

    override fun beginRecording(width: Int, height: Int): Canvas {
        markRecordingStarted()
        val target = (picture ?: Picture().also { picture = it })
        return target.beginRecording(width, height)
    }

    override fun endRecording() {
        picture?.endRecording()
        super.endRecording()
    }

    override fun draw(canvas: Canvas) {
        picture?.let(canvas::drawPicture)
    }

    override fun recycle() {
        super.recycle()
        picture = null
    }
}
