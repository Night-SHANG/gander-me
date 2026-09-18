package com.vaultshelf.legado

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import com.github.liuyueyi.quick.transfer.constants.TransType
import com.jeremyliao.liveeventbus.LiveEventBus
import com.script.rhino.ReadOnlyJavaObject
import com.script.rhino.RhinoScriptEngine
import com.script.rhino.RhinoWrapFactory
import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.help.DefaultData
import io.legado.app.help.LifecycleHelp
import io.legado.app.help.book.ResourceThemeGeneration
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ReadRecordCoverCache
import io.legado.app.help.book.readProgress
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig.applyDayNightInit
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.rhino.NativeBaseSource
import io.legado.app.lib.theme.WallpaperTheme
import io.legado.app.model.BookCover
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.help.book.removeLocalUriCache
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.defaultSharedPreferences
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin VaultShelf boundary around the pinned upstream Legado local-reader subsystem.
 *
 * The reader UI, pagination, TOC, styles, bookmarks, highlights, search and local-book
 * handling remain the original Legado implementation. This bridge only performs the
 * application-level initialization that Legado's own App normally performs and maps
 * VaultShelf's app-private books to Legado bookUrl values.
 */
object LegadoReaderBridge {

    private const val TRANSIENT_PREFS = "vaultshelf_legado_transient"
    private const val TRANSIENT_URLS = "book_urls"

    data class TransientBookSession(
        val bookUrl: String,
        val title: String,
    )

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

        val appContext = context.applicationContext
        val configuration = Configuration(appContext.resources.configuration)

        // Same local reader/theme lifecycle initialization used by Legado App.onCreate().
        ResourceThemeGeneration.observeSystemNight(
            configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES,
            AppConfig.themeMode !in listOf("1", "2", "3"),
        )
        WallpaperTheme.syncWithPreferences(appContext)
        applyDayNightInit(appContext)
        (appContext as? Application)?.registerActivityLifecycleCallbacks(LifecycleHelp)
        appContext.defaultSharedPreferences
            .registerOnSharedPreferenceChangeListener(AppConfig)

        initializeRhino()

        LiveEventBus.config()
            .lifecycleObserverAlwaysActive(true)
            .autoClear(false)
            .enableLogger(false)

        createReadAloudChannel(appContext)

        // Legado performs these local data/cache initializers asynchronously too.
        // Online Cronet/WebDAV/source sync/auto-task initialization is intentionally omitted.
        Coroutine.async {
            DefaultData.upVersion()
            cleanupOrphanedTransientSessions(appContext)
            BookCover.toString()
            ReadBookConfig.clearBgAndCache()
            when (AppConfig.chineseConverterType) {
                1 -> {
                    ChineseUtils.fixT2sDict()
                    ChineseUtils.preLoad(true, TransType.TRADITIONAL_TO_SIMPLE)
                }

                2 -> ChineseUtils.preLoad(true, TransType.SIMPLE_TO_TRADITIONAL)
            }
        }
    }

    private fun initializeRhino() {
        RhinoScriptEngine.initialize()
        RhinoWrapFactory.register(BookSource::class.java, NativeBaseSource.factory)
        RhinoWrapFactory.register(RssSource::class.java, NativeBaseSource.factory)
        RhinoWrapFactory.register(HttpTTS::class.java, NativeBaseSource.factory)
        RhinoWrapFactory.register(ExploreRule::class.java, ReadOnlyJavaObject.factory)
        RhinoWrapFactory.register(SearchRule::class.java, ReadOnlyJavaObject.factory)
        RhinoWrapFactory.register(BookInfoRule::class.java, ReadOnlyJavaObject.factory)
        RhinoWrapFactory.register(ContentRule::class.java, ReadOnlyJavaObject.factory)
        RhinoWrapFactory.register(BookChapter::class.java, ReadOnlyJavaObject.factory)
        RhinoWrapFactory.register(Book.ReadConfig::class.java, ReadOnlyJavaObject.factory)
    }

    private fun createReadAloudChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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

    /**
     * Register a DroidFS temporary content URI as an ephemeral Legado local book.
     *
     * previewImportFile reads the original metadata first. Passing that preview back into
     * importFile uses Legado's own collision handling (it renames the temporary identity
     * when a normal bookshelf book has the same name/author) instead of replacing it.
     */
    fun createTransientBookSession(
        context: Context,
        uri: Uri,
    ): TransientBookSession {
        initialize(context)
        val preview = LocalBook.previewImportFile(uri)
        val book = LocalBook.importFile(uri, preview)
        rememberTransientUrl(context, book.bookUrl)
        return TransientBookSession(book.bookUrl, book.name)
    }

    /**
     * Remove every plaintext artifact that the original Legado reader may have produced
     * for a temporary vault book: EPUB chapter cache, cover, TOC rows, highlights,
     * bookmarks, memo, reading history, parser/URI caches and the temporary book row.
     */
    fun cleanupTransientBookSession(
        context: Context,
        bookUrl: String,
    ) {
        initialize(context)
        val book = appDb.bookDao.getBook(bookUrl)
        if (book == null) {
            forgetTransientUrl(context, bookUrl)
            return
        }

        if (ReadBook.book?.bookUrl == bookUrl) {
            ReadBook.book = null
        }

        LocalBook.withParserCacheInvalidated(book) {
            BookHelp.clearCache(book)

            appDb.bookHighlightDao.getByBook(bookUrl)
                .takeIf { it.isNotEmpty() }
                ?.toTypedArray()
                ?.let(appDb.bookHighlightDao::delete)

            appDb.bookmarkDao.getByBook(book.name, book.author)
                .takeIf { it.isNotEmpty() }
                ?.toTypedArray()
                ?.let(appDb.bookmarkDao::delete)

            appDb.readRecordDao.deleteByBook(book.name, book.author)
            book.removeLocalUriCache()

            // Uses Legado's own local-book cleanup for its generated cover/cache files.
            LocalBook.deleteBook(book, deleteOriginal = false)
            appDb.bookDao.delete(book)
        }

        ReadRecordCoverCache.prune()
        forgetTransientUrl(context, bookUrl)
    }

    private fun cleanupOrphanedTransientSessions(context: Context) {
        transientUrls(context).forEach { bookUrl ->
            runCatching { cleanupTransientBookSession(context, bookUrl) }
        }
    }

    private fun transientPrefs(context: Context) =
        context.getSharedPreferences(TRANSIENT_PREFS, Context.MODE_PRIVATE)

    private fun transientUrls(context: Context): Set<String> =
        transientPrefs(context).getStringSet(TRANSIENT_URLS, emptySet()).orEmpty().toSet()

    private fun rememberTransientUrl(context: Context, bookUrl: String) {
        val urls = transientUrls(context).toMutableSet().apply { add(bookUrl) }
        transientPrefs(context).edit().putStringSet(TRANSIENT_URLS, urls).apply()
    }

    private fun forgetTransientUrl(context: Context, bookUrl: String) {
        val urls = transientUrls(context).toMutableSet().apply { remove(bookUrl) }
        transientPrefs(context).edit().putStringSet(TRANSIENT_URLS, urls).apply()
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
