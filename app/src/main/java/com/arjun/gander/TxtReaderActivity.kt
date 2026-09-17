package com.arjun.gander

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LibraryRepository
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.library.TxtChapter
import com.arjun.gander.library.TxtChapterParser
import com.arjun.gander.ui.theme.VaultShelfTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class TxtReaderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            ReaderLoadState.Ready(
                book = updatedBook,
                text = text,
                chapters = TxtChapterParser.parse(text),
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
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ReaderMessageScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
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
    val initialChapter = remember(state.book.id, state.book.readingOffset, state.chapters) {
        TxtChapterParser.chapterIndexForOffset(state.chapters, state.book.readingOffset)
    }
    var chapterIndex by rememberSaveable(state.book.id) { mutableStateOf(initialChapter) }
    var currentOffset by rememberSaveable(state.book.id) { mutableStateOf(state.book.readingOffset) }
    var fontSize by rememberSaveable(state.book.id) {
        mutableStateOf(preferences.getFloat("font_size_sp", DEFAULT_FONT_SIZE))
    }
    var showContents by rememberSaveable { mutableStateOf(false) }

    val safeChapterIndex = chapterIndex.coerceIn(0, state.chapters.lastIndex)
    val chapter = state.chapters[safeChapterIndex]
    val progress = if (state.book.totalCharacters <= 0) {
        0
    } else {
        ((currentOffset.coerceIn(0, state.book.totalCharacters).toFloat() /
            state.book.totalCharacters.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    fun moveToChapter(index: Int) {
        val safeIndex = index.coerceIn(0, state.chapters.lastIndex)
        val offset = state.chapters[safeIndex].contentStartOffset
        currentOffset = offset
        chapterIndex = safeIndex
        scope.launch { repository.updateProgress(state.book.id, offset) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ReaderTopBar(
                title = state.book.title,
                progress = progress,
                onBack = onBack,
            )
        },
        bottomBar = {
            ReaderControls(
                chapterIndex = safeChapterIndex,
                chapterCount = state.chapters.size,
                onPrevious = { moveToChapter(safeChapterIndex - 1) },
                onNext = { moveToChapter(safeChapterIndex + 1) },
                onContents = { showContents = true },
                onDecreaseFont = {
                    fontSize = (fontSize - 1f).coerceAtLeast(MIN_FONT_SIZE)
                    preferences.edit().putFloat("font_size_sp", fontSize).apply()
                },
                onIncreaseFont = {
                    fontSize = (fontSize + 1f).coerceAtMost(MAX_FONT_SIZE)
                    preferences.edit().putFloat("font_size_sp", fontSize).apply()
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = chapter.title ?: stringResource(R.string.vaultshelf_reader_start),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            key(safeChapterIndex) {
                ReaderChapterContent(
                    text = state.text,
                    chapter = chapter,
                    initialOffset = currentOffset,
                    fontSize = fontSize,
                    onOffsetChanged = { offset ->
                        currentOffset = offset
                        scope.launch { repository.updateProgress(state.book.id, offset) }
                    },
                )
            }
        }
    }

    if (showContents) {
        ChapterContentsDialog(
            chapters = state.chapters,
            selectedIndex = safeChapterIndex,
            onSelect = {
                showContents = false
                moveToChapter(it)
            },
            onDismiss = { showContents = false },
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    progress: Int,
    onBack: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.back))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
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
private fun ReaderChapterContent(
    text: String,
    chapter: TxtChapter,
    initialOffset: Int,
    fontSize: Float,
    onOffsetChanged: (Int) -> Unit,
) {
    val paragraphs = remember(text, chapter) { TxtChapterParser.paragraphs(text, chapter) }
    val initialParagraph = remember(paragraphs, initialOffset) {
        paragraphs.indexOfLast { it.startOffset <= initialOffset }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialParagraph)

    LaunchedEffect(listState, paragraphs) {
        if (paragraphs.isNotEmpty()) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { index ->
                    paragraphs.getOrNull(index)?.let { onOffsetChanged(it.startOffset) }
                }
        }
    }

    if (paragraphs.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = paragraphs,
                key = { _, paragraph -> paragraph.startOffset },
            ) { _, paragraph ->
                Text(
                    text = paragraph.text,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.65f).sp,
                )
            }
        }
    }
}

@Composable
private fun ReaderControls(
    chapterIndex: Int,
    chapterCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onContents: () -> Unit,
    onDecreaseFont: () -> Unit,
    onIncreaseFont: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPrevious, enabled = chapterIndex > 0) {
                    Text(stringResource(R.string.vaultshelf_reader_previous_chapter))
                }
                Text(
                    text = stringResource(
                        R.string.vaultshelf_reader_chapter_position,
                        chapterIndex + 1,
                        chapterCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onNext, enabled = chapterIndex < chapterCount - 1) {
                    Text(stringResource(R.string.vaultshelf_reader_next_chapter))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
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