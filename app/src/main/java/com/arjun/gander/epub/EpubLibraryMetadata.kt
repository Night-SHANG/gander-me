package com.arjun.gander.epub

import android.graphics.Bitmap
import io.legado.app.ui.book.read.epub.LegadoEpubDocument
import java.io.File

data class EpubLibraryMetadata(
    val title: String?,
    val cover: Bitmap?,
)

object EpubLibraryMetadataReader {
    suspend fun read(file: File): Result<EpubLibraryMetadata> = runCatching {
        LegadoEpubDocument.open(file).getOrThrow().use { document ->
            EpubLibraryMetadata(
                title = document.title.takeIf { it.isNotBlank() },
                cover = document.coverBitmap(COVER_WIDTH, COVER_HEIGHT),
            )
        }
    }

    private const val COVER_WIDTH = 600
    private const val COVER_HEIGHT = 900
}
