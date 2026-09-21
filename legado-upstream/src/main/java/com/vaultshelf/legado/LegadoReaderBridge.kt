package com.vaultshelf.legado

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.github.liuyueyi.quick.transfer.constants.TransType
import com.jeremyliao.liveeventbus.LiveEventBus
import com.script.rhino.ReadOnlyJavaObject
import com.script.rhino.RhinoScriptEngine
import com.script.rhino.RhinoWrapFactory
import io.legado.app.constant.AppConst
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.ReadRecord
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.help.DefaultData
import io.legado.app.help.LifecycleHelp
import io.legado.app.help.RuleBigDataHelp
import io.legado.app.help.book.ResourceThemeGeneration
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ReadRecordCoverCache
import io.legado.app.help.book.readProgress
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig.applyDayNight
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
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import splitties.init.injectAsAppCtx
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Thin VaultShelf boundary around the pinned upstream Legado local-reader subsystem.
 *
 * The reader UI, pagination, TOC, styles, bookmarks, highlights, search and local-book
 * handling remain the original Legado implementation. This bridge only performs the
 * application-level initialization that Legado's own App normally performs and maps
 * VaultShelf's app-private books to Legado bookUrl values.
 */
object LegadoReaderBridge {

    private const val TRANSIENT_ORIGIN = "vaultshelf::transient-local-book"
    // One-time migration only: development builds before the database marker stored raw
    // temporary URIs here. New sessions never write this SharedPreferences registry.
    private const val LEGACY_TRANSIENT_PREFS = "vaultshelf_legado_transient"
    private const val LEGACY_TRANSIENT_URLS = "book_urls"
    private const val TRANSIENT_METADATA_KEY_ALIAS = "vaultshelf_legado_transient_metadata_v1"
    private const val TRANSIENT_METADATA_VERSION = 1
    private const val GCM_TAG_BITS = 128
    private const val TXT_TOC_RULE_VERSION_KEY = "txtTocRuleVersion"
    private const val TXT_TOC_RULE_VERSION = 3
    private const val VAULTSHELF_SOFT_WHITE_PRESET = "VaultShelf 柔和白"

    data class TransientBookSession(
        val bookUrl: String,
        val title: String,
    )

    data class TransientReadingPosition(
        val chapterIndex: Int,
        val chapterPosition: Int,
        val updatedAtEpochMillis: Long,
    )

    private data class TransientMetadataSnapshot(
        val bookName: String,
        val bookAuthor: String,
        val bookmarks: List<Bookmark>,
        val readRecords: List<ReadRecord>,
    )

    data class LocalBookSnapshot(
        val bookUrl: String,
        val title: String,
        val author: String,
        val coverPath: String?,
        val progress: Float,
        val chapterIndex: Int,
        val chapterPosition: Int,
        val chapterUpdatedAtEpochMillis: Long,
        val totalChapters: Int,
    )

    private val initialized = AtomicBoolean(false)
    private val transientStartupCleanupComplete = AtomicBoolean(false)
    private const val OPEN_READER_SETTINGS = "vaultshelf.open_reader_settings"

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        // GanderStartupProvider has a high initOrder and can run before AndroidX
        // Startup's Splitties AppCtxInitializer. Legado reads appCtx immediately
        // through AppConfig/appDb, so establish the exact Application context first.
        appContext.injectAsAppCtx()

        if (!initialized.compareAndSet(false, true)) return
        ensureVaultShelfReadingPreset(appContext)
        val configuration = Configuration(appContext.resources.configuration)
        var observedNightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

        // Same local reader/theme lifecycle initialization used by Legado App.onCreate().
        ResourceThemeGeneration.observeSystemNight(
            observedNightMode == Configuration.UI_MODE_NIGHT_YES,
            AppConfig.themeMode !in listOf("1", "2", "3"),
        )
        WallpaperTheme.syncWithPreferences(appContext)
        applyDayNightInit(appContext)
        (appContext as? Application)?.registerActivityLifecycleCallbacks(LifecycleHelp)
        appContext.registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    val nightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    if (nightMode == observedNightMode) return
                    observedNightMode = nightMode
                    ResourceThemeGeneration.observeSystemNight(
                        nightMode == Configuration.UI_MODE_NIGHT_YES,
                        AppConfig.themeMode !in listOf("1", "2", "3"),
                    )
                    applyDayNight(appContext)
                }

                override fun onLowMemory() = Unit
            },
        )
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
            // Online Cronet/WebDAV/source sync/auto-task initialization is omitted.
            // TXT rules are initialized by a synchronous reader-entry barrier below so an
            // external "Open with VaultShelf" intent cannot race the database seed.
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

    /**
     * Prime the local-reader data path after app startup without blocking launcher UI.
     *
     * Accessing TXT rules opens Legado's Room database and seeds its built-in TOC rules,
     * which are the expensive one-time pieces that should not be paid by the first book.
     */
    fun warmUpLocalReader(context: Context) {
        initialize(context)
        ensureLocalTxtTocRules()
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

    /**
     * Keep VaultShelf's softer light reading palette available without shifting Legado's
     * persisted preset indexes. When Legado has no saved reading-style preference yet,
     * VaultShelf uses this existing preset as the product default.
     */
    private fun ensureVaultShelfReadingPreset(context: Context) {
        var index = ReadBookConfig.configList.indexOfFirst {
            it.name == VAULTSHELF_SOFT_WHITE_PRESET
        }
        if (index < 0) {
            ReadBookConfig.configList.add(
                ReadBookConfig.Config(
                    name = VAULTSHELF_SOFT_WHITE_PRESET,
                    bgStr = "#F3F3F3",
                    bgStrNight = "#202020",
                    textColor = "#1B1B1B",
                    textColorNight = "#F5F5F5",
                    textAccentColor = "#005FB8",
                    textAccentColorNight = "#60CDFF",
                    bgType = 0,
                    bgTypeNight = 0,
                ),
            )
            index = ReadBookConfig.configList.lastIndex
            ReadBookConfig.save()
        }

        val preferences = context.defaultSharedPreferences
        if (!preferences.contains(PreferKey.readStyleSelect)) {
            ReadBookConfig.readStyleSelect = index
        }
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

    @Synchronized
    private fun ensureLocalTxtTocRules() {
        runBlocking(Dispatchers.IO) {
            val storedVersion = LocalConfig.getInt(TXT_TOC_RULE_VERSION_KEY, 0)
            val ruleCount = appDb.txtTocRuleDao.count
            if (storedVersion >= TXT_TOC_RULE_VERSION && ruleCount > 0) {
                return@runBlocking
            }

            // Do not use Legado's upstream TOC-rule version helper here: its getter advances the
            // version marker before the database import succeeds.
            DefaultData.importDefaultTocRules()
            check(
                LocalConfig.edit()
                    .putInt(TXT_TOC_RULE_VERSION_KEY, TXT_TOC_RULE_VERSION)
                    .commit(),
            ) {
                "Unable to persist Legado TXT TOC rule version"
            }
        }
    }

    /**
     * Keep a normal Files URI in Legado when VaultShelf has durable read access to it.
     * This intentionally preserves Legado's own progress, bookmarks, highlights and history.
     */
    fun ensurePersistentUriBook(
        context: Context,
        uri: Uri,
    ): LocalBookSnapshot {
        initialize(context)
        ensureLocalTxtTocRules()
        val preview = LocalBook.previewImportFile(uri)
        val book = appDb.bookDao.getBook(preview.bookUrl)
            ?: LocalBook.importFile(uri, preview)
        return book.snapshot()
    }

    /**
     * Register a DroidFS temporary content URI as an ephemeral Legado local book.
     *
     * previewImportFile reads the original metadata first. Passing that preview back into
     * importFile uses Legado's own collision handling instead of replacing a durable book.
     */
    fun createTransientBookSession(
        context: Context,
        uri: Uri,
    ): TransientBookSession {
        initialize(context)
        ensureTransientStartupCleanup(context)
        ensureLocalTxtTocRules()
        val preview = LocalBook.previewImportFile(uri)
        val existing = appDb.bookDao.getBook(preview.bookUrl)
        check(existing == null || existing.origin == TRANSIENT_ORIGIN) {
            "Refusing to turn a durable Legado book into a transient session"
        }
        val existedBeforeImport = existing != null
        val transientPreview = preview.copy(origin = TRANSIENT_ORIGIN)
        val book = LocalBook.importFile(uri, transientPreview)
        try {
            rememberTransientMetadataSnapshot(context, book)
        } catch (error: Throwable) {
            // The reader has not opened yet, so no transient bookmark/history can exist.
            // Roll a newly inserted row back instead of entering a session whose later
            // cleanup could not distinguish pre-existing shared-identity metadata.
            if (!existedBeforeImport) {
                LocalBook.withParserCacheInvalidated(book) {
                    BookHelp.clearCache(book)
                    BookHelp.clearEpubContentUriCache(book)
                    appDb.bookMemoDao.delete(book.bookUrl)
                    RuleBigDataHelp.clearBook(book.bookUrl)
                    book.removeLocalUriCache()
                    LocalBook.deleteBook(book, deleteOriginal = false)
                    appDb.bookDao.delete(book)
                }
            }
            throw error
        }
        return TransientBookSession(book.bookUrl, book.name)
    }

    /**
     * Remove every plaintext artifact that the original Legado reader may have produced
     * for a temporary vault book: EPUB chapter cache, cover, TOC rows, highlights,
     * bookmarks, memo, reading history, parser/URI caches and the temporary book row.
     */
    fun restoreReadingPosition(
        context: Context,
        bookUrl: String,
        chapterIndex: Int,
        chapterPosition: Int,
        updatedAtEpochMillis: Long = System.currentTimeMillis(),
    ) {
        initialize(context)
        val book = appDb.bookDao.getBook(bookUrl) ?: return
        book.durChapterIndex = chapterIndex.coerceAtLeast(0)
        book.durChapterPos = chapterPosition.coerceAtLeast(0)
        book.durChapterTime = updatedAtEpochMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        appDb.bookDao.update(book)
    }

    fun restoreTransientReadingPosition(
        context: Context,
        bookUrl: String,
        chapterIndex: Int,
        chapterPosition: Int,
    ) = restoreReadingPosition(context, bookUrl, chapterIndex, chapterPosition)

    fun readingPosition(
        context: Context,
        bookUrl: String,
    ): TransientReadingPosition? {
        initialize(context)
        val book = appDb.bookDao.getBook(bookUrl) ?: return null
        return TransientReadingPosition(
            chapterIndex = book.durChapterIndex.coerceAtLeast(0),
            chapterPosition = book.durChapterPos.coerceAtLeast(0),
            updatedAtEpochMillis = book.durChapterTime,
        )
    }

    fun transientReadingPosition(
        context: Context,
        bookUrl: String,
    ): TransientReadingPosition? = readingPosition(context, bookUrl)

    fun cleanupTransientBookSession(
        context: Context,
        bookUrl: String,
    ) {
        initialize(context)
        val metadataSnapshot = transientMetadataSnapshot(context, bookUrl)
        val book = appDb.bookDao.getBook(bookUrl)
        if (book == null) {
            metadataSnapshot?.let(::restoreTransientMetadataSnapshot)
            forgetTransientMetadataSnapshot(context, bookUrl)
            return
        }

        if (ReadBook.book?.bookUrl == bookUrl) {
            ReadBook.book = null
        }

        LocalBook.withParserCacheInvalidated(book) {
            BookHelp.clearCache(book)
            BookHelp.clearEpubContentUriCache(book)
            appDb.bookMemoDao.delete(bookUrl)
            RuleBigDataHelp.clearBook(bookUrl)

            appDb.bookHighlightDao.getByBook(bookUrl)
                .takeIf { it.isNotEmpty() }
                ?.toTypedArray()
                ?.let { appDb.bookHighlightDao.delete(*it) }

            // Bookmark/read-history rows are keyed by book name + author rather than
            // bookUrl. A deleted ordinary book can therefore have historical rows with
            // the same identity as this transient vault book. Only wipe this shared
            // identity when we have the pre-session snapshot needed to restore it.
            if (metadataSnapshot != null &&
                metadataSnapshot.bookName == book.name &&
                metadataSnapshot.bookAuthor == book.author
            ) {
                appDb.bookmarkDao.getByBook(book.name, book.author)
                    .takeIf { it.isNotEmpty() }
                    ?.toTypedArray()
                    ?.let { appDb.bookmarkDao.delete(*it) }
                appDb.readRecordDao.deleteByBook(book.name, book.author)
            }

            book.removeLocalUriCache()
            LocalBook.deleteBook(book, deleteOriginal = false)
            appDb.bookDao.delete(book)
        }

        metadataSnapshot?.let(::restoreTransientMetadataSnapshot)
        ReadRecordCoverCache.prune()
        forgetTransientMetadataSnapshot(context, bookUrl)
    }

    @Synchronized
    private fun ensureTransientStartupCleanup(context: Context) {
        if (transientStartupCleanupComplete.get()) return
        cleanupOrphanedTransientSessions(context)
        transientStartupCleanupComplete.set(true)
    }

    private fun cleanupOrphanedTransientSessions(context: Context) {
        val markedUrls = appDb.bookDao.all
            .asSequence()
            .filter { it.origin == TRANSIENT_ORIGIN }
            .map { it.bookUrl }
            .toSet()

        // Migrate any development-build registry once, then erase it. This path is
        // intentionally read-only for compatibility; createTransientBookSession never
        // writes raw temporary URIs outside Legado's transient Book row.
        val legacyPrefs = context.getSharedPreferences(
            LEGACY_TRANSIENT_PREFS,
            Context.MODE_PRIVATE,
        )
        val legacyUrls = legacyPrefs
            .getStringSet(LEGACY_TRANSIENT_URLS, emptySet())
            .orEmpty()
            .toSet()

        (markedUrls + legacyUrls).forEach { bookUrl ->
            runCatching { cleanupTransientBookSession(context, bookUrl) }
        }
        legacyPrefs.edit().clear().apply()
    }

    private fun rememberTransientMetadataSnapshot(context: Context, book: Book) {
        val snapshot = TransientMetadataSnapshot(
            bookName = book.name,
            bookAuthor = book.author,
            bookmarks = appDb.bookmarkDao.getByBook(book.name, book.author),
            readRecords = appDb.readRecordDao.getRecords(book.name, book.author),
        )
        val plain = GSON.toJson(snapshot).toByteArray(Charsets.UTF_8)
        val encrypted = encryptTransientMetadata(book.bookUrl, plain)
        val atomic = transientMetadataFile(context, book.bookUrl)
        val output = atomic.startWrite()
        try {
            output.write(encrypted)
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        } finally {
            plain.fill(0)
        }
    }

    private fun transientMetadataSnapshot(
        context: Context,
        bookUrl: String,
    ): TransientMetadataSnapshot? = runCatching {
        val plain = decryptTransientMetadata(
            bookUrl,
            transientMetadataFile(context, bookUrl).readFully(),
        )
        try {
            GSON.fromJson(
                String(plain, Charsets.UTF_8),
                TransientMetadataSnapshot::class.java,
            )
        } finally {
            plain.fill(0)
        }
    }.getOrNull()

    private fun restoreTransientMetadataSnapshot(snapshot: TransientMetadataSnapshot) {
        appDb.runInTransaction {
            if (snapshot.bookmarks.isNotEmpty()) {
                appDb.bookmarkDao.insert(*snapshot.bookmarks.toTypedArray())
            }
            if (snapshot.readRecords.isNotEmpty()) {
                appDb.readRecordDao.insert(*snapshot.readRecords.toTypedArray())
            }
        }
    }

    @Synchronized
    private fun transientMetadataKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(TRANSIENT_METADATA_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    TRANSIENT_METADATA_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun encryptTransientMetadata(bookUrl: String, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, transientMetadataKey())
        cipher.updateAAD(bookUrl.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        check(iv.isNotEmpty() && iv.size <= 255)
        val encrypted = cipher.doFinal(plain)
        return ByteBuffer.allocate(2 + iv.size + encrypted.size).apply {
            put(TRANSIENT_METADATA_VERSION.toByte())
            put(iv.size.toByte())
            put(iv)
            put(encrypted)
        }.array()
    }

    private fun decryptTransientMetadata(bookUrl: String, payload: ByteArray): ByteArray {
        require(payload.size >= 3)
        val buffer = ByteBuffer.wrap(payload)
        val version = buffer.get().toInt() and 0xff
        require(version == TRANSIENT_METADATA_VERSION)
        val ivSize = buffer.get().toInt() and 0xff
        require(ivSize > 0 && buffer.remaining() > ivSize)

        val iv = ByteArray(ivSize).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            transientMetadataKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv),
        )
        cipher.updateAAD(bookUrl.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encrypted)
    }

    private fun transientMetadataFile(context: Context, bookUrl: String): AtomicFile {
        val directory = File(
            context.noBackupFilesDir,
            "vaultshelf_legado_transient_metadata",
        )
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create transient Legado metadata directory"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bookUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$digest.bin"))
    }

    private fun forgetTransientMetadataSnapshot(context: Context, bookUrl: String) {
        transientMetadataFile(context, bookUrl).delete()
        // Development builds before encrypted snapshots used a .json file. Never read
        // that plaintext legacy form; erase it opportunistically if one is present.
        transientMetadataLegacyFile(context, bookUrl).delete()
    }

    private fun transientMetadataLegacyFile(context: Context, bookUrl: String): File {
        val directory = File(
            context.noBackupFilesDir,
            "vaultshelf_legado_transient_metadata",
        )
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bookUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.json")
    }

    @Synchronized
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

    fun readerSettingsIntent(
        context: Context,
        bookUrl: String,
    ): Intent = readerIntent(context, bookUrl)
        .putExtra(OPEN_READER_SETTINGS, true)

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
        chapterUpdatedAtEpochMillis = durChapterTime,
        totalChapters = totalChapterNum,
    )
}
