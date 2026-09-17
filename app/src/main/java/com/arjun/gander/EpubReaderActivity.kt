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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
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
    private var chromeHideJob: Job? = null

    private var fontSize = DEFAULT_FONT_SIZE
    private var lineHeightIndex = DEFAULT_LINE_HEIGHT_INDEX
    private var pageMarginsIndex = DEFAULT_PAGE_MARGINS_INDEX
    private var readerTheme = Theme.LIGHT
    private var scrollMode = false

    private lateinit var titleView: TextView
    private lateinit var progressView: TextView
    private lateinit var containerView: View
    private lateinit var errorView: TextView
    private lateinit var topChrome: View
    private lateinit var bottomChrome: View
    private lateinit var lineHeightButton: MaterialButton
    private lateinit var pageMarginsButton: MaterialButton
    private lateinit var themeButton: MaterialButton
    private lateinit var flowButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
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
        setChromeVisible(false)

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
        topChrome = findViewById(R.id.epub_reader_top_chrome)
        bottomChrome = findViewById(R.id.epub_reader_bottom_chrome)
        lineHeightButton = findViewById(R.id.epub_reader_line_height)
        pageMarginsButton = findViewById(R.id.epub_reader_page_margins)
        themeButton = findViewById(R.id.epub_reader_theme)
        flowButton = findViewById(R.id.epub_reader_flow)
    }

    private fun bindControls() {
        findViewById<MaterialButton>(R.id.epub_reader_back).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.epub_reader_contents).setOnClickListener {
            keepChromeVisible()
            showContents()
        }
        findViewById<MaterialButton>(R.id.epub_reader_font_smaller).setOnClickListener {
            fontSize = (fontSize - FONT_STEP).coerceAtLeast(MIN_FONT_SIZE)
            savePreferences()
            applyPreferences()
            keepChromeVisible()
        }
        findViewById<MaterialButton>(R.id.epub_reader_font_larger).setOnClickListener {
            fontSize = (fontSize + FONT_STEP).coerceAtMost(MAX_FONT_SIZE)
            savePreferences()
            applyPreferences()
            keepChromeVisible()
        }
        lineHeightButton.setOnClickListener {
            lineHeightIndex = (lineHeightIndex + 1) % LINE_HEIGHTS.size
            savePreferences()
            updateControlLabels()
            applyPreferences()
            keepChromeVisible()
        }
        pageMarginsButton.setOnClickListener {
            pageMarginsIndex = (pageMarginsIndex + 1) % PAGE_MARGINS.size
            savePreferences()
            updateControlLabels()
            applyPreferences()
            keepChromeVisible()
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
            keepChromeVisible()
        }
        flowButton.setOnClickListener {
            scrollMode = !scrollMode
            savePreferences()
            updateControlLabels()
            applyPreferences()
            keepChromeVisible()
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
        installReadingTapHandling(currentNavigator)

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

    private fun installReadingTapHandling(currentNavigator: EpubNavigatorFragment) {
        currentNavigator.addInputListener(
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    val width = currentNavigator.requireView().width.takeIf { it > 0 } ?: return false
                    val zone = event.point.x / width.toFloat()

                    return when {
                        !scrollMode && zone < LEFT_TAP_ZONE -> {
                            currentNavigator.goBackward(animated = true)
                            setChromeVisible(false)
                            true
                        }

                        !scrollMode && zone > RIGHT_TAP_ZONE -> {
                            currentNavigator.goForward(animated = true)
                            setChromeVisible(false)
                            true
                        }

                        zone in CENTER_TAP_START..CENTER_TAP_END -> {
                            toggleChrome()
                            true
                        }

                        else -> false
                    }
                }
            },
        )
    }

    private fun toggleChrome() {
        setChromeVisible(topChrome.visibility != View.VISIBLE)
    }

    private fun setChromeVisible(visible: Boolean) {
        chromeHideJob?.cancel()
        if (visible) {
            topChrome.visibility = View.VISIBLE
            bottomChrome.visibility = View.VISIBLE
            topChrome.alpha = 1f
            bottomChrome.alpha = 1f
            scheduleChromeHide()
        } else {
            topChrome.visibility = View.GONE
            bottomChrome.visibility = View.GONE
        }
    }

    private fun keepChromeVisible() {
        if (topChrome.visibility == View.VISIBLE) scheduleChromeHide()
    }

    private fun scheduleChromeHide() {
        chromeHideJob?.cancel()
        chromeHideJob = activityScope.launch {
            delay(CHROME_TIMEOUT_MS)
            setChromeVisible(false)
        }
    }

    private fun currentPreferences(): EpubPreferences = EpubPreferences(
        fontSize = fontSize,
        lineHeight = LINE_HEIGHTS[lineHeightIndex],
        pageMargins = PAGE_MARGINS[pageMarginsIndex],
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
                setChromeVisible(false)
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
        setChromeVisible(false)
        containerView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    private fun restorePreferences() {
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        fontSize = preferences.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE.toFloat()).toDouble()
            .coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        lineHeightIndex = preferences.getInt(KEY_LINE_HEIGHT, DEFAULT_LINE_HEIGHT_INDEX)
            .coerceIn(LINE_HEIGHTS.indices)
        pageMarginsIndex = preferences.getInt(KEY_PAGE_MARGINS, DEFAULT_PAGE_MARGINS_INDEX)
            .coerceIn(PAGE_MARGINS.indices)
        readerTheme = runCatching {
            Theme.valueOf(preferences.getString(KEY_THEME, Theme.LIGHT.name) ?: Theme.LIGHT.name)
        }.getOrDefault(Theme.LIGHT)
        scrollMode = preferences.getBoolean(KEY_SCROLL_MODE, false)
    }

    private fun savePreferences() {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit {
            putFloat(KEY_FONT_SIZE, fontSize.toFloat())
            putInt(KEY_LINE_HEIGHT, lineHeightIndex)
            putInt(KEY_PAGE_MARGINS, pageMarginsIndex)
            putString(KEY_THEME, readerTheme.name)
            putBoolean(KEY_SCROLL_MODE, scrollMode)
        }
    }

    private fun updateControlLabels() {
        lineHeightButton.setText(
            when (lineHeightIndex) {
                0 -> R.string.vaultshelf_epub_line_compact
                1 -> R.string.vaultshelf_epub_line_normal
                else -> R.string.vaultshelf_epub_line_relaxed
            },
        )
        pageMarginsButton.setText(
            when (pageMarginsIndex) {
                0 -> R.string.vaultshelf_epub_margin_narrow
                1 -> R.string.vaultshelf_epub_margin_normal
                else -> R.string.vaultshelf_epub_margin_wide
            },
        )
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
        chromeHideJob?.cancel()
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
        private const val KEY_LINE_HEIGHT = "line_height"
        private const val KEY_PAGE_MARGINS = "page_margins"
        private const val KEY_THEME = "theme"
        private const val KEY_SCROLL_MODE = "scroll_mode"
        private const val DEFAULT_FONT_SIZE = 1.0
        private const val MIN_FONT_SIZE = 0.75
        private const val MAX_FONT_SIZE = 1.75
        private const val FONT_STEP = 0.1
        private const val DEFAULT_LINE_HEIGHT_INDEX = 1
        private const val DEFAULT_PAGE_MARGINS_INDEX = 1
        private const val CHROME_TIMEOUT_MS = 3_500L
        private const val LEFT_TAP_ZONE = 0.25f
        private const val RIGHT_TAP_ZONE = 0.75f
        private const val CENTER_TAP_START = 0.25f
        private const val CENTER_TAP_END = 0.75f
        private val LINE_HEIGHTS = doubleArrayOf(1.2, 1.5, 1.8)
        private val PAGE_MARGINS = doubleArrayOf(0.6, 1.0, 1.4)
    }
}
