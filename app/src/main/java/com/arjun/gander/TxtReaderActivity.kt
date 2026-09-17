package com.arjun.gander

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LibraryRepository
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.library.TxtChapter
import com.arjun.gander.library.TxtChapterParser
import com.arjun.gander.library.TxtReadingBlock
import com.arjun.gander.library.TxtReadingFlow
import com.arjun.gander.ui.theme.VaultShelfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class TxtReaderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.statusBars())

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        val repository = LocalLibraryRepository(applicationContext)

        val root = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VaultShelfTheme {
                    if (bookId == null) {
                        ReaderMessageScreen(onBack = { finishReader() })
                    } else {
                        TxtReaderScreen(
                            bookId = bookId,
                            repository = repository,
                            onBack = { finishReader() },
                        )
                    }
                }
            }
        }
        setContentView(root)
    }

    private fun finishReader() {
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        const val EXTRA_BOOK_ID = "vaultshelf.book_id"
    }
}

private sealed interface ReaderLoadState {
    data object Loading : ReaderLoadState
    data object Error : ReaderLoadState
    data class Ready(
        val book: LibraryBook,
        val text: String,
        val chapters: List<TxtChapter>,
        val blocks: List<TxtReadingBlock>,
    ) : ReaderLoadState
}

@Composable
private fun TxtReaderScreen(
    bookId: String,
    repository: LibraryRepository,
    onBack: () -> Unit,
) {
    var state by remember(bookId) { mutableStateOf<ReaderLoadState>(ReaderLoadState.Loading) }

    LaunchedEffect(bookId, repository) {
        state = runCatching {
            val book = repository.getBook(bookId) ?: error("Book metadata missing")
            val text = repository.readText(bookId)
            val updatedBook = repository.updateProgress(bookId, book.readingOffset) ?: book
            val chapters = TxtChapterParser.parse(text)
            ReaderLoadState.Ready(
                book = updatedBook,
                text = text,
                chapters = chapters,
                blocks = TxtReadingFlow.build(text, chapters),
            )
        }.getOrElse { ReaderLoadState.Error }
    }

    when (val current = state) {
        ReaderLoadState.Loading -> ReaderLoadingScreen()
        ReaderLoadState.Error -> ReaderMessageScreen(onBack = onBack)
        is ReaderLoadState.Ready -> ReadyReader(
            state = current,
            repository = repository,
            onBack = onBack,
        )
    }
}

@Composable
private fun ReaderLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ReaderMessageScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
private fun ReadyReader(
    state: ReaderLoadState.Ready,
    repository: LibraryRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember {
        context.getSharedPreferences("vaultshelf_reader", android.content.Context.MODE_PRIVATE)
    }
    var fontSize by rememberSaveable(state.book.id) {
        mutableFloatStateOf(preferences.getFloat("font_size_sp", DEFAULT_FONT_SIZE))
    }
    var currentOffset by rememberSaveable(state.book.id) { mutableIntStateOf(state.book.readingOffset) }
    var showContents by rememberSaveable { mutableStateOf(false) }
    var chromeVisible by rememberSaveable { mutableStateOf(false) }
    var chromeEpoch by rememberSaveable { mutableIntStateOf(0) }
    var readerSize by remember { mutableStateOf(IntSize.Zero) }

    val blocks = state.blocks
    val initialIndex = remember(blocks, state.book.readingOffset) {
        TxtReadingFlow.indexForOffset(blocks, state.book.readingOffset)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val progress = if (state.book.totalCharacters <= 0) {
        0
    } else {
        ((currentOffset.coerceIn(0, state.book.totalCharacters).toFloat() /
            state.book.totalCharacters.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    fun revealChrome() {
        chromeVisible = true
        chromeEpoch++
    }

    LaunchedEffect(chromeVisible, chromeEpoch) {
        if (chromeVisible) {
            delay(CHROME_AUTO_HIDE_MS)
            chromeVisible = false
        }
    }

    LaunchedEffect(listState, blocks) {
        if (blocks.isNotEmpty()) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { index ->
                    blocks.getOrNull(index)?.let { block ->
                        currentOffset = block.startOffset
                        repository.updateProgress(state.book.id, block.startOffset)
                    }
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { readerSize = it }
            .pointerInput(blocks, readerSize, chromeVisible) {
                detectTapGestures { point ->
                    val width = readerSize.width.takeIf { it > 0 } ?: return@detectTapGestures
                    when {
                        point.x < width * 0.27f -> {
                            scope.launch {
                                listState.animateScrollBy(-listState.layoutInfo.viewportSize.height * 0.9f)
                            }
                        }
                        point.x > width * 0.73f -> {
                            scope.launch {
                                listState.animateScrollBy(listState.layoutInfo.viewportSize.height * 0.9f)
                            }
                        }
                        else -> {
                            if (chromeVisible) chromeVisible = false else revealChrome()
                        }
                    }
                }
            },
    ) {
        if (blocks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.vaultshelf_reader_empty_chapter),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                itemsIndexed(
                    items = blocks,
                    key = { _, block -> "${block.startOffset}:${block::class.simpleName}" },
                ) { _, block ->
                    when (block) {
                        is TxtReadingBlock.ChapterHeading -> Text(
                            text = block.displayText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                        )
                        is TxtReadingBlock.Paragraph -> Text(
                            text = block.displayText,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.65f).sp,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopChrome(
                title = state.book.title,
                progress = progress,
                onBack = onBack,
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomChrome(
                onContents = {
                    revealChrome()
                    showContents = true
                },
                onDecreaseFont = {
                    fontSize = (fontSize - 1f).coerceAtLeast(MIN_FONT_SIZE)
                    preferences.edit().putFloat("font_size_sp", fontSize).apply()
                    revealChrome()
                },
                onIncreaseFont = {
                    fontSize = (fontSize + 1f).coerceAtMost(MAX_FONT_SIZE)
                    preferences.edit().putFloat("font_size_sp", fontSize).apply()
                    revealChrome()
                },
            )
        }
    }

    if (showContents) {
        ChapterContentsDialog(
            chapters = state.chapters,
            selectedIndex = TxtChapterParser.chapterIndexForOffset(state.chapters, currentOffset),
            onSelect = { chapterIndex ->
                showContents = false
                val offset = state.chapters[chapterIndex].startOffset
                currentOffset = offset
                scope.launch {
                    listState.scrollToItem(TxtReadingFlow.indexForOffset(blocks, offset))
                    repository.updateProgress(state.book.id, offset)
                }
            },
            onDismiss = { showContents = false },
        )
    }
}

@Composable
private fun ReaderTopChrome(
    title: String,
    progress: Int,
    onBack: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.vaultshelf_reader_progress, progress),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReaderBottomChrome(
    onContents: () -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onContents) {
                Text(stringResource(R.string.vaultshelf_reader_contents))
            }
            TextButton(onClick = onDecreaseFont) {
                Text(stringResource(R.string.vaultshelf_reader_font_smaller))
            }
            TextButton(onClick = onIncreaseFont) {
                Text(stringResource(R.string.vaultshelf_reader_font_larger))
            }
        }
    }
}

@Composable
private fun ChapterContentsDialog(
    chapters: List<TxtChapter>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vaultshelf_reader_contents)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(chapters) { index, chapter ->
                    TextButton(
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = chapter.title ?: stringResource(R.string.vaultshelf_reader_start),
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

private const val DEFAULT_FONT_SIZE = 19f
private const val MIN_FONT_SIZE = 14f
private const val MAX_FONT_SIZE = 30f
private const val CHROME_AUTO_HIDE_MS = 3_500L
