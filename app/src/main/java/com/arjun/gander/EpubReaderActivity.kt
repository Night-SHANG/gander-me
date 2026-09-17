package com.arjun.gander

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.arjun.gander.epub.EpubReaderSession
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryRepository
import com.arjun.gander.library.LocalLibraryRepository
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link

@OptIn(ExperimentalReadiumApi::class)
class EpubReaderActivity : AppCompatActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: LibraryRepository
    private var session: EpubReaderSession? = null
    private var navigator: EpubNavigatorFragment? = null
    private var bookId: String? = null

    private var fontSize = DEFAULT_FONT_SIZE
    private var readerTheme = Theme.LIGHT
    private var scrollMode = false

    private lateinit var titleView: TextView
    private lateinit var progressView: TextView
    private lateinit var containerView: View
    private lateinit var errorView: TextView
    private lateinit var themeButton: MaterialButton
    private lateinit var flowButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        // Readium fragments need a factory during Android state restoration. The publication is
        // deliberately not retained across process death, so install Readium's safe dummy factory
        // and return to the shelf instead of letting FragmentManager instantiate an invalid reader.
        if (savedInstanceState != null) {
            supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        }
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }

        setContentView(R.layout.activity_epub_reader)
        repository = LocalLibraryRepository(this)
        bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        bindViews()
        restorePreferences()
        bindControls()

        val requestedBookId = bookId
        if (requestedBookId.isNullOrBlank()) {
            showError()
            return
        }

        activityScope.launch {
            openBook(requestedBookId)
        }
    }

    private fun bindViews() {
        titleView = findViewById(R.id.epub_reader_title)
        progressView = findViewById(R.id.epub_reader_progress)
        containerView = findViewById(R.id.epub_reader_container)
        errorView = findViewById(R.id.epub_reader_error)
        themeButton = findViewById(R.id.epub_reader_theme)
        flowButton = findViewById(R.id.epub_reader_flow)
    }

    private fun bindControls() {
        findViewById<MaterialButton>(R.id.epub_reader_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.epub_reader_contents).setOnClickListener { showContents() }
        findViewById<MaterialButton>(R.id.epub_reader_font_smaller).setOnClickListener {
            fontSize = (fontSize - FONT_STEP).coerceAtLeast(MIN_FONT_SIZE)
            savePreferences()
            applyPreferences()
        }
        findViewById<MaterialButton>(R.id.epub_reader_font_larger).setOnClickListener {
            fontSize = (fontSize + FONT_STEP).coerceAtMost(MAX_FONT_SIZE)
            savePreferences()
            applyPreferences()
        }
        themeButton.setOnClickListener {
            readerTheme = when (readerTheme) {
                Theme.LIGHT -> Theme.SEPIA
                Theme.SEPIA -> Theme.DARK
                Theme.DARK -> Theme.LIGHT
            }
            savePreferences()
            updateControlLabels()
            applyPreferences()
        }
        flowButton.setOnClickListener {
            scrollMode = !scrollMode
            savePreferences()
            updateControlLabels()
            applyPreferences()
        }
        updateControlLabels()
    }

    private suspend fun openBook(id: String) {
        val book = repository.getBook(id)
        if (book == null || book.format != BookFormat.EPUB) {
            showError()
            return
        }
        titleView.text = book.title

        val file = runCatching { repository.bookFile(id) }.getOrElse {
            showError()
            return
        }
        val openedSession = EpubReaderSession.open(this, file, book.readingLocatorJson)
            .getOrElse {
                showError()
                return
            }
        session = openedSession

        supportFragmentManager.fragmentFactory = openedSession.navigatorFactory.createFragmentFactory(
            initialLocator = openedSession.initialLocator,
            initialPreferences = currentPreferences(),
        )
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.epub_reader_container,
                EpubNavigatorFragment::class.java,
                Bundle(),
                NAVIGATOR_TAG,
            )
            .commitNow()

        navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
        val currentNavigator = navigator ?: run {
            showError()
            return
        }

        containerView.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        activityScope.launch {
            currentNavigator.currentLocator.collectLatest { locator ->
                val progression = locator.locations.totalProgression?.toFloat()
                val percent = ((progression ?: 0f).coerceIn(0f, 1f) * 100f).roundToInt()
                progressView.text = getString(R.string.vaultshelf_reader_progress, percent)
                repository.updateEpubProgress(
                    id = id,
                    locatorJson = locator.toJSON().toString(),
                    publicationProgression = progression,
                )
            }
        }
    }

    private fun currentPreferences(): EpubPreferences = EpubPreferences(
        fontSize = fontSize,
        theme = readerTheme,
        scroll = scrollMode,
        publisherStyles = false,
    )

    private fun applyPreferences() {
        navigator?.submitPreferences(currentPreferences())
    }

    private fun showContents() {
        val currentSession = session ?: return
        val entries = flattenContents(currentSession.publication.tableOfContents)
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setMessage(R.string.vaultshelf_epub_no_contents)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val labels = entries.map { entry ->
            val title = entry.link.title?.takeIf { it.isNotBlank() } ?: entry.link.href.toString()
            "  ".repeat(entry.depth) + title
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.vaultshelf_reader_contents)
            .setItems(labels) { _, which ->
                val locator = currentSession.publication.locatorFromLink(entries[which].link)
                if (locator != null) navigator?.go(locator)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun flattenContents(links: List<Link>, depth: Int = 0): List<TocEntry> = buildList {
        for (link in links) {
            add(TocEntry(link, depth))
            addAll(flattenContents(link.children, depth + 1))
        }
    }

    private fun showError() {
        containerView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    private fun restorePreferences() {
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        fontSize = preferences.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE.toFloat()).toDouble()
            .coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        readerTheme = runCatching {
            Theme.valueOf(preferences.getString(KEY_THEME, Theme.LIGHT.name) ?: Theme.LIGHT.name)
        }.getOrDefault(Theme.LIGHT)
        scrollMode = preferences.getBoolean(KEY_SCROLL_MODE, false)
    }

    private fun savePreferences() {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit {
            putFloat(KEY_FONT_SIZE, fontSize.toFloat())
            putString(KEY_THEME, readerTheme.name)
            putBoolean(KEY_SCROLL_MODE, scrollMode)
        }
    }

    private fun updateControlLabels() {
        themeButton.setText(
            when (readerTheme) {
                Theme.LIGHT -> R.string.vaultshelf_epub_theme_light
                Theme.SEPIA -> R.string.vaultshelf_epub_theme_sepia
                Theme.DARK -> R.string.vaultshelf_epub_theme_dark
            },
        )
        flowButton.setText(
            if (scrollMode) {
                R.string.vaultshelf_epub_mode_scroll
            } else {
                R.string.vaultshelf_epub_mode_paged
            },
        )
    }

    override fun onDestroy() {
        activityScope.cancel()
        navigator = null
        super.onDestroy()
        session?.close()
        session = null
    }

    private data class TocEntry(val link: Link, val depth: Int)

    companion object {
        const val EXTRA_BOOK_ID = "vaultshelf.book_id"
        private const val NAVIGATOR_TAG = "vaultshelf.epub.navigator"
        private const val PREFERENCES_NAME = "vaultshelf_epub_reader"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_THEME = "theme"
        private const val KEY_SCROLL_MODE = "scroll_mode"
        private const val DEFAULT_FONT_SIZE = 1.0
        private const val MIN_FONT_SIZE = 0.75
        private const val MAX_FONT_SIZE = 1.75
        private const val FONT_STEP = 0.1
    }
}
