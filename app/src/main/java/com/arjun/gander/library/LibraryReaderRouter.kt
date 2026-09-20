package com.arjun.gander.library

import android.content.Context
import android.content.Intent
import com.arjun.gander.BookReadingPositions
import com.arjun.gander.Positions
import com.arjun.gander.ViewerActivity
import com.vaultshelf.legado.LegadoReaderBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReaderLaunchPlan(
    val intent: Intent,
    val legadoBookUrl: String? = null,
)

suspend fun createReaderLaunchPlan(
    context: Context,
    repository: LibraryRepository,
    book: LibraryBook,
): ReaderLaunchPlan = withContext(Dispatchers.IO) {
    val bookFile = repository.bookFile(book.id)
    val readingKey = Positions.keyFor(bookFile)
    when (book.format) {
        BookFormat.TXT,
        BookFormat.EPUB,
        BookFormat.UMD,
        BookFormat.MOBI,
        BookFormat.AZW3,
        BookFormat.AZW -> {
            val snapshot = LegadoReaderBridge.ensureLocalBook(
                context,
                bookFile,
                book.title,
            )
            readingKey?.let { key ->
                val shared = BookReadingPositions.get(context, key)
                if (shared != null && shared.updatedAtEpochMillis > snapshot.chapterUpdatedAtEpochMillis) {
                    LegadoReaderBridge.restoreReadingPosition(
                        context,
                        snapshot.bookUrl,
                        shared.chapterIndex,
                        shared.chapterPosition,
                        shared.updatedAtEpochMillis,
                    )
                } else {
                    BookReadingPositions.save(
                        context,
                        key,
                        snapshot.chapterIndex,
                        snapshot.chapterPosition,
                        snapshot.chapterUpdatedAtEpochMillis,
                    )
                }
            }
            ReaderLaunchPlan(
                intent = LegadoReaderBridge.readerIntent(context, snapshot.bookUrl),
                legadoBookUrl = snapshot.bookUrl,
            )
        }

        BookFormat.MARKDOWN,
        BookFormat.PDF -> {
            repository.updateProgress(book.id, book.readingOffset)
            ReaderLaunchPlan(
                intent = Intent(context, ViewerActivity::class.java)
                    .putExtra(ViewerActivity.EXTRA_PATH, bookFile.absolutePath)
                    .putExtra(ViewerActivity.EXTRA_LIBRARY_BOOK_ID, book.id),
            )
        }
    }
}

suspend fun syncLegadoReaderProgress(
    context: Context,
    repository: LibraryRepository,
    bookId: String,
    legadoBookUrl: String,
): LibraryBook? = withContext(Dispatchers.IO) {
    val snapshot = LegadoReaderBridge.snapshot(context, legadoBookUrl)
        ?: return@withContext repository.getBook(bookId)
    Positions.keyFor(repository.bookFile(bookId))?.let { key ->
        BookReadingPositions.save(
            context,
            key,
            snapshot.chapterIndex,
            snapshot.chapterPosition,
            snapshot.chapterUpdatedAtEpochMillis,
        )
    }
    repository.updateLegadoProgress(
        id = bookId,
        legadoBookUrl = snapshot.bookUrl,
        progressFraction = snapshot.progress,
    )
}
