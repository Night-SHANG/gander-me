/*
 * Adapted from Legado / 阅读 3.0.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.utils.canvasrecorder

import android.graphics.Canvas

interface CanvasRecorder {
    val width: Int
    val height: Int
    fun beginRecording(width: Int, height: Int): Canvas
    fun endRecording()
    fun draw(canvas: Canvas)
    fun invalidate()
    fun recycle()
    fun isDirty(): Boolean
    fun isLocked(): Boolean
    fun needRecord(): Boolean
}
