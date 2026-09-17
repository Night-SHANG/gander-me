package com.arjun.gander

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LibraryRepository
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.ui.theme.VaultShelfTheme
import io.legado.app.constant.PageAnim
import io.legado.app.ui.book.read.epub.LegadoEpubDocument
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ReaderLayoutConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class EpubReaderActivity : AppCompatActivity() {

    private var documentToClose: LegadoEpubDocument? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        val repository = LocalLibraryRepository(applicationContext)
        val root = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VaultShelfTheme {
                    if (bookId == null) {
                        EpubReaderMessageScreen(onBack = { finishReader() })
                    } else {
                        MatureEpubReaderScreen(
                            bookId = bookId,
                            repository = repository,
                            onDocumentOpened = { document ->
                                documentToClose?.takeIf { it !== document }?.close()
                                documentToClose = document
                            },
                            onBack = { finishReader() },
                        )
                    }
                }
            }
        }
        setContentView(root)
    }

    override fun onDestroy() {
        documentToClose?.close()
        documentToClose = null
        super.onDestroy()
    }

    private fun finishReader() {
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        const val EXTRA_BOOK_ID = "vaultshelf.book_id"
    }
}

private sealed interface EpubLoadState {
    data object Loading : EpubLoadState
    data object Error : EpubLoadState
    data class Ready(
        val book: LibraryBook,
        val document: LegadoEpubDocument,
    ) : EpubLoadState
}

private enum class EpubThemeMode(val key: String) {
    LIGHT("light"),
    SEPIA("sepia"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): EpubThemeMode = entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

private data class EpubReaderPalette(
    val background: Color,
    val text: Color,
    val chrome: Color,
)

@Composable
private fun MatureEpubReaderScreen(
    bookId: String,
    repository: LibraryRepository,
    onDocumentOpened: (LegadoEpubDocument) -> Unit,
    onBack: () -> Unit,
) {
    var state by remember(bookId) { mutableStateOf<EpubLoadState>(EpubLoadState.Loading) }

    LaunchedEffect(bookId, repository) {
        state = withContext(Dispatchers.IO) {
            runCatching {
                val book = repository.getBook(bookId) ?: error("Book metadata missing")
                require(book.format == BookFormat.EPUB) { "Not an EPUB library item" }
                val file = repository.bookFile(bookId)
                val document = LegadoEpubDocument.open(file).getOrThrow()
                onDocumentOpened(document)
                EpubLoadState.Ready(book = book, document = document)
            }.getOrElse { EpubLoadState.Error }
        }
    }

    when (val current = state) {
        EpubLoadState.Loading -> EpubReaderLoadingScreen()
        EpubLoadState.Error -> EpubReaderMessageScreen(onBack = onBack)
        is EpubLoadState.Ready -> MatureEpubReader(
            state = current,
            repository = repository,
            onBack = onBack,
        )
    }
}

@Composable
private fun MatureEpubReader(
    state: EpubLoadState.Ready,
    repository: LibraryRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val preferences = remember {
        context.getSharedPreferences(EPUB_READER_PREFERENCES, Context.MODE_PRIVATE)
    }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var readView by remember { mutableStateOf<ReadView?>(null) }
    var pages by remember { mutableStateOf<List<TextChapter>?>(null) }
    var currentProgression by rememberSaveable(state.book.id) {
        mutableFloatStateOf(state.book.publicationProgression ?: 0f)
    }
    var selectedChapter by rememberSaveable(state.book.id) { mutableIntStateOf(0) }
    var fontSizeSp by rememberSaveable(state.book.id) {
        mutableFloatStateOf(preferences.getFloat(PREF_FONT_SIZE, DEFAULT_FONT_SIZE))
    }
    var lineSpacing by rememberSaveable(state.book.id) {
        mutableFloatStateOf(preferences.getFloat(PREF_LINE_SPACING, DEFAULT_LINE_SPACING))
    }
    var marginDp by rememberSaveable(state.book.id) {
        mutableIntStateOf(preferences.getInt(PREF_MARGIN_DP, DEFAULT_MARGIN_DP))
    }
    var pageAnimation by rememberSaveable(state.book.id) {
        mutableIntStateOf(preferences.getInt(PREF_PAGE_ANIMATION, PageAnim.slidePageAnim))
    }
    var themeMode by rememberSaveable(state.book.id) {
        mutableStateOf(EpubThemeMode.fromKey(preferences.getString(PREF_THEME, EpubThemeMode.LIGHT.key)))
    }
    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    var chromeEpoch by rememberSaveable { mutableIntStateOf(0) }
    var showContents by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val palette = when (themeMode) {
        EpubThemeMode.LIGHT -> EpubReaderPalette(
            background = Color(0xFFF6F3EC),
            text = Color(0xFF27231F),
            chrome = Color(0xFFFDFBF7),
        )
        EpubThemeMode.SEPIA -> EpubReaderPalette(
            background = Color(0xFFECE0C6),
            text = Color(0xFF3D3023),
            chrome = Color(0xFFF4E8D0),
        )
        EpubThemeMode.DARK -> EpubReaderPalette(
            background = Color(0xFF171717),
            text = Color(0xFFE8E3D8),
            chrome = Color(0xFF242424),
        )
    }

    val layoutConfig = remember(viewport, fontSizeSp, lineSpacing, marginDp, density) {
        ReaderLayoutConfig(
            contentTextSizePx = with(density) { fontSizeSp.sp.toPx() },
            titleTextSizePx = with(density) { (fontSizeSp * 1.28f).sp.toPx() },
            lineSpacingMultiplier = lineSpacing,
            paragraphSpacingPx = with(density) { 8.dp.toPx() },
            paddingLeftPx = with(density) { marginDp.dp.roundToPx() },
            paddingTopPx = with(density) { 18.dp.roundToPx() },
            paddingRightPx = with(density) { marginDp.dp.roundToPx() },
            paddingBottomPx = with(density) { 18.dp.roundToPx() },
            paragraphIndent = "　　",
            useZhLayout = true,
            fullJustify = true,
            showChapterTitle = true,
            titleTopSpacingPx = with(density) { 22.dp.toPx() },
            titleBottomSpacingPx = with(density) { 26.dp.toPx() },
        )
    }

    fun revealChrome() {
        chromeVisible = true
        chromeEpoch++
    }

    LaunchedEffect(chromeVisible, chromeEpoch) {
        if (chromeVisible && !showSettings && !showContents) {
            delay(CHROME_AUTO_HIDE_MS)
            chromeVisible = false
        }
    }

    LaunchedEffect(state.document, viewport, layoutConfig) {
        if (viewport.width <= 0 || viewport.height <= 0) return@LaunchedEffect
        val progressionToRestore = currentProgression
        val paginated = withContext(Dispatchers.Default) {
            state.document.paginate(
                viewportWidthPx = viewport.width,
                viewportHeightPx = viewport.height,
                config = layoutConfig,
            )
        }
        pages = paginated
        val position = state.document.positionForProgression(paginated, progressionToRestore)
        selectedChapter = position.chapterIndex
        readView?.apply {
            imageProvider = state.document
            configurePages(
                config = layoutConfig,
                textColor = palette.text.toArgb(),
                titleColor = palette.text.toArgb(),
                backgroundColor = palette.background.toArgb(),
            )
            setPageAnimation(pageAnimation)
            setBook(paginated, position.chapterIndex, position.pageIndex)
        }
    }

    LaunchedEffect(readView, pages, pageAnimation, palette, layoutConfig) {
        val view = readView ?: return@LaunchedEffect
        val paginated = pages ?: return@LaunchedEffect
        if (paginated.isEmpty()) return@LaunchedEffect
        val position = state.document.positionForProgression(paginated, currentProgression)
        view.imageProvider = state.document
        view.configurePages(
            config = layoutConfig,
            textColor = palette.text.toArgb(),
            titleColor = palette.text.toArgb(),
            backgroundColor = palette.background.toArgb(),
        )
        view.setPageAnimation(pageAnimation)
        view.setBook(paginated, position.chapterIndex, position.pageIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .safeDrawingPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewport = it },
        ) {
            val paginated = pages
            if (paginated == null || viewport.width <= 0 || viewport.height <= 0) {
                EpubReaderLoadingScreen()
            } else if (paginated.isEmpty()) {
                EpubReaderMessageScreen(onBack = onBack)
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { androidContext ->
                        ReadView(androidContext).also { view ->
                            readView = view
                            view.imageProvider = state.document
                            view.callback = epubReadViewCallback(
                                document = state.document,
                                pages = paginated,
                                repository = repository,
                                book = state.book,
                                scope = scope,
                                onMenuRequested = {
                                    if (chromeVisible) chromeVisible = false else revealChrome()
                                },
                                onProgression = { progression, chapterIndex ->
                                    currentProgression = progression
                                    selectedChapter = chapterIndex
                                },
                                onInteraction = {
                                    if (chromeVisible) chromeEpoch++
                                },
                            )
                            val position = state.document.positionForProgression(
                                paginated,
                                currentProgression,
                            )
                            view.configurePages(
                                config = layoutConfig,
                                textColor = palette.text.toArgb(),
                                titleColor = palette.text.toArgb(),
                                backgroundColor = palette.background.toArgb(),
                            )
                            view.setPageAnimation(pageAnimation)
                            view.setBook(paginated, position.chapterIndex, position.pageIndex)
                        }
                    },
                    update = { view ->
                        view.imageProvider = state.document
                        view.callback = epubReadViewCallback(
                            document = state.document,
                            pages = paginated,
                            repository = repository,
                            book = state.book,
                            scope = scope,
                            onMenuRequested = {
                                if (chromeVisible) chromeVisible = false else revealChrome()
                            },
                            onProgression = { progression, chapterIndex ->
                                currentProgression = progression
                                selectedChapter = chapterIndex
                            },
                            onInteraction = {
                                if (chromeVisible) chromeEpoch++
                            },
                        )
                    },
                )
            }

            val progress = (currentProgression.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)
            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                EpubReaderTopChrome(
                    title = state.book.title,
                    progress = progress,
                    chromeColor = palette.chrome,
                    textColor = palette.text,
                    onBack = onBack,
                )
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                EpubReaderBottomChrome(
                    chromeColor = palette.chrome,
                    textColor = palette.text,
                    onContents = {
                        showContents = true
                        revealChrome()
                    },
                    onSettings = {
                        showSettings = true
                        revealChrome()
                    },
                )
            }
        }
    }

    if (showContents) {
        EpubContentsDialog(
            document = state.document,
            selectedIndex = selectedChapter,
            onSelect = { chapterIndex ->
                showContents = false
                val paginated = pages ?: return@EpubContentsDialog
                val view = readView ?: return@EpubContentsDialog
                val safeChapter = chapterIndex.coerceIn(0, paginated.lastIndex)
                selectedChapter = safeChapter
                currentProgression = state.document.progressionForPosition(
                    paginated,
                    safeChapter,
                    0,
                )
                view.setBook(paginated, safeChapter, 0)
                scope.launch {
                    repository.updateEpubProgress(
                        id = state.book.id,
                        locatorJson = epubLocatorJson(safeChapter, 0),
                        publicationProgression = currentProgression,
                    )
                }
                revealChrome()
            },
            onDismiss = {
                showContents = false
                revealChrome()
            },
        )
    }

    if (showSettings) {
        EpubReaderSettingsSheet(
            pageAnimation = pageAnimation,
            fontSizeSp = fontSizeSp,
            lineSpacing = lineSpacing,
            marginDp = marginDp,
            themeMode = themeMode,
            onPageAnimation = { animation ->
                pageAnimation = animation
                preferences.edit().putInt(PREF_PAGE_ANIMATION, animation).apply()
                readView?.setPageAnimation(animation)
            },
            onFontSize = { size ->
                fontSizeSp = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
                preferences.edit().putFloat(PREF_FONT_SIZE, fontSizeSp).apply()
            },
            onLineSpacing = { spacing ->
                lineSpacing = spacing
                preferences.edit().putFloat(PREF_LINE_SPACING, spacing).apply()
            },
            onMargin = { margin ->
                marginDp = margin
                preferences.edit().putInt(PREF_MARGIN_DP, margin).apply()
            },
            onTheme = { mode ->
                themeMode = mode
                preferences.edit().putString(PREF_THEME, mode.key).apply()
            },
            onDismiss = {
                showSettings = false
                revealChrome()
            },
        )
    }
}

private fun epubReadViewCallback(
    document: LegadoEpubDocument,
    pages: List<TextChapter>,
    repository: LibraryRepository,
    book: LibraryBook,
    scope: kotlinx.coroutines.CoroutineScope,
    onMenuRequested: () -> Unit,
    onProgression: (Float, Int) -> Unit,
    onInteraction: () -> Unit,
): ReadView.Callback = object : ReadView.Callback {
    override fun onMenuRequested() = onMenuRequested.invoke()

    override fun onPositionChanged(chapterIndex: Int, pageIndex: Int, page: TextPage) {
        val progression = document.progressionForPosition(pages, chapterIndex, pageIndex)
        onProgression(progression, chapterIndex)
        scope.launch {
            repository.updateEpubProgress(
                id = book.id,
                locatorJson = epubLocatorJson(chapterIndex, pageIndex),
                publicationProgression = progression,
            )
        }
    }

    override fun onBoundary(direction: PageDirection) = Unit

    override fun onInteraction() = onInteraction.invoke()
}

private fun epubLocatorJson(chapterIndex: Int, pageIndex: Int): String = JSONObject()
    .put("engine", "legado")
    .put("chapter", chapterIndex)
    .put("page", pageIndex)
    .toString()

@Composable
private fun EpubReaderLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EpubReaderMessageScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.vaultshelf_reader_load_failed),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
    }
}

@Composable
private fun EpubReaderTopChrome(
    title: String,
    progress: Int,
    chromeColor: Color,
    textColor: Color,
    onBack: () -> Unit,
) {
    Surface(
        color = chromeColor.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back), color = textColor)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.vaultshelf_reader_progress, progress),
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun EpubReaderBottomChrome(
    chromeColor: Color,
    textColor: Color,
    onContents: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(
        color = chromeColor.copy(alpha = 0.98f),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onContents) {
                Text(stringResource(R.string.vaultshelf_reader_contents), color = textColor)
            }
            TextButton(onClick = onSettings) {
                Text(stringResource(R.string.vaultshelf_reader_settings), color = textColor)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpubReaderSettingsSheet(
    pageAnimation: Int,
    fontSizeSp: Float,
    lineSpacing: Float,
    marginDp: Int,
    themeMode: EpubThemeMode,
    onPageAnimation: (Int) -> Unit,
    onFontSize: (Float) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onMargin: (Int) -> Unit,
    onTheme: (EpubThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.vaultshelf_reader_settings), style = MaterialTheme.typography.titleLarge)
            EpubSettingsLabel(stringResource(R.string.vaultshelf_reader_turn_mode))
            EpubPageModeRows(pageAnimation = pageAnimation, onPageAnimation = onPageAnimation)
            HorizontalDivider()

            EpubSettingsLabel(stringResource(R.string.vaultshelf_reader_font_size))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onFontSize(fontSizeSp - 1f) }) {
                    Text(stringResource(R.string.vaultshelf_reader_font_smaller))
                }
                Text(
                    text = stringResource(R.string.vaultshelf_reader_font_value, fontSizeSp.toInt()),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(onClick = { onFontSize(fontSizeSp + 1f) }) {
                    Text(stringResource(R.string.vaultshelf_reader_font_larger))
                }
            }

            EpubSettingsLabel(stringResource(R.string.vaultshelf_reader_line_spacing))
            EpubChoiceRow(
                choices = listOf(
                    stringResource(R.string.vaultshelf_reader_compact) to 1.35f,
                    stringResource(R.string.vaultshelf_reader_standard) to 1.55f,
                    stringResource(R.string.vaultshelf_reader_relaxed) to 1.78f,
                ),
                selected = lineSpacing,
                onSelected = onLineSpacing,
            )

            EpubSettingsLabel(stringResource(R.string.vaultshelf_reader_margins))
            EpubChoiceRow(
                choices = listOf(
                    stringResource(R.string.vaultshelf_reader_narrow) to 16,
                    stringResource(R.string.vaultshelf_reader_standard) to 24,
                    stringResource(R.string.vaultshelf_reader_wide) to 36,
                ),
                selected = marginDp,
                onSelected = onMargin,
            )

            EpubSettingsLabel(stringResource(R.string.vaultshelf_reader_theme))
            EpubChoiceRow(
                choices = listOf(
                    stringResource(R.string.vaultshelf_epub_theme_light) to EpubThemeMode.LIGHT,
                    stringResource(R.string.vaultshelf_epub_theme_sepia) to EpubThemeMode.SEPIA,
                    stringResource(R.string.vaultshelf_epub_theme_dark) to EpubThemeMode.DARK,
                ),
                selected = themeMode,
                onSelected = onTheme,
            )
        }
    }
}

@Composable
private fun EpubPageModeRows(
    pageAnimation: Int,
    onPageAnimation: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EpubChoiceRow(
            choices = listOf(
                stringResource(R.string.vaultshelf_reader_mode_cover) to PageAnim.coverPageAnim,
                stringResource(R.string.vaultshelf_reader_mode_slide) to PageAnim.slidePageAnim,
                stringResource(R.string.vaultshelf_reader_mode_simulation) to PageAnim.simulationPageAnim,
            ),
            selected = pageAnimation,
            onSelected = onPageAnimation,
        )
        EpubChoiceRow(
            choices = listOf(
                stringResource(R.string.vaultshelf_reader_mode_scroll) to PageAnim.scrollPageAnim,
                stringResource(R.string.vaultshelf_reader_mode_none) to PageAnim.noAnim,
            ),
            selected = pageAnimation,
            onSelected = onPageAnimation,
        )
    }
}

@Composable
private fun EpubSettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> EpubChoiceRow(
    choices: List<Pair<String, T>>,
    selected: T,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        choices.forEach { (label, value) ->
            val active = value == selected
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onSelected(value) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EpubContentsDialog(
    document: LegadoEpubDocument,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vaultshelf_reader_contents)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(document.chapters) { index, chapter ->
                    TextButton(
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = chapter.title,
                            color = if (index == selectedIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}

private const val EPUB_READER_PREFERENCES = "vaultshelf_reader"
private const val DEFAULT_FONT_SIZE = 19f
private const val MIN_FONT_SIZE = 14f
private const val MAX_FONT_SIZE = 32f
private const val DEFAULT_LINE_SPACING = 1.55f
private const val DEFAULT_MARGIN_DP = 24
private const val CHROME_AUTO_HIDE_MS = 4_000L
private const val PREF_FONT_SIZE = "font_size_sp"
private const val PREF_LINE_SPACING = "line_spacing"
private const val PREF_MARGIN_DP = "margin_dp"
private const val PREF_PAGE_ANIMATION = "page_animation"
private const val PREF_THEME = "theme"
