/*
 * VaultShelf adapter around Legado's reader layout configuration.
 * Legado source concepts are GPL-3.0; VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page.provider

import android.graphics.Typeface

data class ReaderLayoutConfig(
    val contentTextSizePx: Float,
    val titleTextSizePx: Float,
    val lineSpacingMultiplier: Float = 1.55f,
    val paragraphSpacingPx: Float = 10f,
    val paddingLeftPx: Int = 32,
    val paddingTopPx: Int = 40,
    val paddingRightPx: Int = 32,
    val paddingBottomPx: Int = 40,
    val paragraphIndent: String = "　　",
    val useZhLayout: Boolean = true,
    val fullJustify: Boolean = true,
    val showChapterTitle: Boolean = true,
    val centerChapterTitle: Boolean = false,
    val titleTopSpacingPx: Float = 24f,
    val titleBottomSpacingPx: Float = 28f,
    val typeface: Typeface = Typeface.DEFAULT,
)
