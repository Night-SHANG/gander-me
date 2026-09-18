/*
 * Adapted from Legado / 阅读 3.0.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.utils.canvasrecorder

object CanvasRecorderFactory {
    fun create(): CanvasRecorder = PictureCanvasRecorder()
}
