/*
 * Adapted from Legado / 阅读 3.0.
 * Source: https://github.com/LegadoTeam/legado
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.utils.canvasrecorder

import java.util.concurrent.atomic.AtomicLong

abstract class BaseCanvasRecorder : CanvasRecorder {
    private val state = CanvasRecorderState()

    override fun invalidate() = state.invalidate()

    override fun recycle() = state.invalidate()

    protected fun markRecordingStarted() = state.markRecordingStarted()

    override fun endRecording() = state.markRecordingFinished()

    override fun isDirty(): Boolean = state.isDirty()

    override fun isLocked(): Boolean = false

    override fun needRecord(): Boolean = isDirty() && !isLocked()
}

private class CanvasRecorderState {
    private val invalidationVersion = AtomicLong()

    @Volatile
    private var recordingVersion = Long.MIN_VALUE

    @Volatile
    private var renderedVersion = Long.MIN_VALUE

    fun invalidate() {
        invalidationVersion.incrementAndGet()
    }

    fun markRecordingStarted() {
        recordingVersion = invalidationVersion.get()
    }

    fun markRecordingFinished() {
        renderedVersion = recordingVersion
    }

    fun isDirty(): Boolean = renderedVersion != invalidationVersion.get()
}
