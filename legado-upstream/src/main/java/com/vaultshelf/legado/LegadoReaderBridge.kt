package com.vaultshelf.legado

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.script.rhino.RhinoScriptEngine
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.DefaultData
import io.legado.app.help.book.readProgress
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.book.read.ReadBookActivity
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin VaultShelf boundary around the pinned upstream Legado local-reader subsystem.
 *
 * All actual reading UI, pagination, TOC, styles, bookmarks, highlights and search stay
 * inside the original Legado sources. VaultShelf only imports its private local copy into
 * Legado's local-book database and exchanges a stable bookUrl/progress snapshot.
 */
object LegadoReaderBridge {

    data class LocalBookSnapshot(
        val bookUrl: String,
        val title: String,
        val author: String,
        val coverPath: String?,
        val progress: Float,
        val chapterIndex: Int,
        val chapterPosition: Int,
        val totalChapters: Int,
    )

    private val initialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        RhinoScriptEngine.initialize()

        // Seeds the same built-in TXT TOC/default reader data the upstream app uses.
        // This intentionally avoids Legado App.onCreate(), which also starts online
        // source/WebDAV/Cronet/background features that VaultShelf does not use.
        DefaultData.upVersion()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(
                    AppConst.channelIdReadAloud,
                    context.getString(io.legado.app.R.string.read_aloud),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    enableLights(false)
                    enableVibration(false)
                    setSound(null, null)
                },
            )
        }
    }

    fun ensureLocalBook(
        context: Context,
        file: File,
        displayTitle: String? = null,
    ): LocalBookSnapshot {
        initialize(context)
        require(file.isFile) { "Local book file does not exist: $file" }

        val uri = Uri.fromFile(file)
        val preview = LocalBook.previewImportFile(uri)
        var book = appDb.bookDao.getBook(preview.bookUrl)
            ?: LocalBook.importFile(uri)

        val title = displayTitle?.trim().orEmpty()
        if (title.isNotEmpty() && title != book.name) {
            book = book.copy(name = title)
            appDb.bookDao.update(book)
        }

        return book.snapshot()
    }

    fun snapshot(context: Context, bookUrl: String): LocalBookSnapshot? {
        initialize(context)
        return appDb.bookDao.getBook(bookUrl)?.snapshot()
    }

    fun readerIntent(
        context: Context,
        bookUrl: String,
    ): Intent {
        initialize(context)
        return Intent(context, ReadBookActivity::class.java)
            .putExtra("bookUrl", bookUrl)
            .putExtra("inBookshelf", true)
    }

    fun forgetLocalBook(context: Context, bookUrl: String) {
        initialize(context)
        val book = appDb.bookDao.getBook(bookUrl) ?: return
        LocalBook.deleteBook(book, deleteOriginal = false)
        appDb.bookDao.delete(book)
    }

    private fun Book.snapshot() = LocalBookSnapshot(
        bookUrl = bookUrl,
        title = name,
        author = author,
        coverPath = getDisplayCover(),
        progress = readProgress() ?: 0f,
        chapterIndex = durChapterIndex,
        chapterPosition = durChapterPos,
        totalChapters = totalChapterNum,
    )
}
