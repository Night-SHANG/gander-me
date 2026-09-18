/*
 * VaultShelf adapter for Legado-style image-backed reading pages.
 * Licensed under GNU GPL v3. VaultShelf modifications: 2026-09-17.
 */
package io.legado.app.ui.book.read.page

import android.graphics.Bitmap

/** Supplies already-resolved local book images to PageView. */
fun interface ReaderImageProvider {
    fun load(source: String): Bitmap?
}
