package com.arjun.gander.ui.library

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.gander.EpubReaderActivity
import com.arjun.gander.R
import com.arjun.gander.TxtReaderActivity
import com.arjun.gander.library.BookCoverStyle
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ShelfViewMode { GRID, LIST }

@Composable
fun LibraryScreen(
    repository: LibraryRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var importFailed by remember { mutableStateOf(false) }
    var viewModeName by rememberSaveable { mutableStateOf(ShelfViewMode.GRID.name) }
    val viewMode = runCatching { ShelfViewMode.valueOf(viewModeName) }.getOrDefault(ShelfViewMode.GRID)
    var bookToRename by remember { mutableStateOf<LibraryBook?>(null) }
    var bookToDelete by remember { mutableStateOf<LibraryBook?>(null) }
    var bookToInspect by remember { mutableStateOf<LibraryBook?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            books = repository.listBooks()
            loading = false
        }
    }

    val readerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        refresh()
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importFailed = false
            scope.launch {
                runCatching {
                    when (detectBookFormat(context, uri)) {
                        BookFormat.TXT -> repository.importTxt(uri)
                        BookFormat.EPUB -> repository.importEpub(uri)
                        null -> error("Unsupported library format")
                    }
                }
                    .onSuccess { books = repository.listBooks() }
                    .onFailure { importFailed = true }
            }
        }
    }

    fun openBook(book: LibraryBook) {
        val intent = when (book.format) {
            BookFormat.TXT -> Intent(context, TxtReaderActivity::class.java)
                .putExtra(TxtReaderActivity.EXTRA_BOOK_ID, book.id)

            BookFormat.EPUB -> Intent(context, EpubReaderActivity::class.java)
                .putExtra(EpubReaderActivity.EXTRA_BOOK_ID, book.id)
        }
        readerLauncher.launch(intent)
    }

    LaunchedEffect(repository) {
        books = repository.listBooks()
        loading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        ShelfHeader(
            bookCount = books.size,
            viewMode = viewMode,
            onViewModeChange = {
                viewModeName = if (viewMode == ShelfViewMode.GRID) {
                    ShelfViewMode.LIST.name
                } else {
                    ShelfViewMode.GRID.name
                }
            },
            onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
        )

        if (importFailed) {
            Text(
                text = stringResource(R.string.vaultshelf_library_import_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when {
            loading -> LoadingLibrary()
            books.isEmpty() -> EmptyLibrary(
                onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
                modifier = Modifier.padding(16.dp),
            )

            viewMode == ShelfViewMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                gridItems(books, key = { it.id }) { book ->
                    BookGridItem(
                        book = book,
                        repository = repository,
                        onOpen = { openBook(book) },
                        onRename = { bookToRename = book },
                        onDelete = { bookToDelete = book },
                        onInfo = { bookToInspect = book },
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(books, key = { it.id }) { book ->
                    BookListItem(
                        book = book,
                        repository = repository,
                        sizeLabel = Formatter.formatShortFileSize(context, book.sizeBytes),
                        onOpen = { openBook(book) },
                        onRename = { bookToRename = book },
                        onDelete = { bookToDelete = book },
                        onInfo = { bookToInspect = book },
                    )
                }
            }
        }
    }

    bookToRename?.let { book ->
        RenameBookDialog(
            book = book,
            onDismiss = { bookToRename = null },
            onConfirm = { title ->
                scope.launch {
                    repository.renameBook(book.id, title)
                    books = repository.listBooks()
                    bookToRename = null
                }
            },
        )
    }

    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text(stringResource(R.string.vaultshelf_library_delete_title)) },
            text = {
                Text(stringResource(R.string.vaultshelf_library_delete_message, book.title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteBook(book.id)
                            books = repository.listBooks()
                            bookToDelete = null
                        }
                    },
                ) {
                    Text(stringResource(R.string.vaultshelf_library_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    bookToInspect?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToInspect = null },
            title = { Text(book.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.vaultshelf_library_info_format, book.format.name))
                    Text(
                        stringResource(
                            R.string.vaultshelf_library_info_size,
                            Formatter.formatShortFileSize(context, book.sizeBytes),
                        ),
                    )
                    Text(stringResource(R.string.vaultshelf_library_progress, book.progressPercent))
                }
            },
            confirmButton = {
                TextButton(onClick = { bookToInspect = null }) {
                    Text(stringResource(R.string.about_close))
                }
            },
        )
    }
}

@Composable
private fun ShelfHeader(
    bookCount: Int,
    viewMode: ShelfViewMode,
    onViewModeChange: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.vaultshelf_library_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.vaultshelf_library_book_count, bookCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onViewModeChange) {
                Text(
                    stringResource(
                        if (viewMode == ShelfViewMode.GRID) {
                            R.string.vaultshelf_library_view_list
                        } else {
                            R.string.vaultshelf_library_view_grid
                        },
                    ),
                )
            }
            Button(
                onClick = onImport,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(stringResource(R.string.vaultshelf_library_import_short))
            }
        }
    }
}

@Composable
private fun LoadingLibrary() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyLibrary(
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.vaultshelf_library_empty_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.vaultshelf_library_empty_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onImport, shape = RoundedCornerShape(10.dp)) {
                Text(stringResource(R.string.vaultshelf_library_import_book))
            }
        }
    }
}

@Composable
private fun BookGridItem(
    book: LibraryBook,
    repository: LibraryRepository,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            BookCover(
                book = book,
                repository = repository,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clickable(onClick = onOpen),
            )
            BookMenuButton(
                onOpen = onOpen,
                onRename = onRename,
                onDelete = onDelete,
                onInfo = onInfo,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        LinearProgressIndicator(
            progress = { book.progressFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.vaultshelf_library_progress, book.progressPercent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookListItem(
    book: LibraryBook,
    repository: LibraryRepository,
    sizeLabel: String,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                book = book,
                repository = repository,
                modifier = Modifier
                    .fillMaxWidth(0.18f)
                    .aspectRatio(0.68f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.vaultshelf_library_list_meta,
                        book.format.name,
                        sizeLabel,
                        book.progressPercent,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BookMenuButton(
                onOpen = onOpen,
                onRename = onRename,
                onDelete = onDelete,
                onInfo = onInfo,
            )
        }
    }
}

@Composable
private fun BookCover(
    book: LibraryBook,
    repository: LibraryRepository,
    modifier: Modifier = Modifier,
) {
    val embeddedCover by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = book.id,
        key2 = book.coverFileName,
    ) {
        value = withContext(Dispatchers.IO) {
            repository.coverFile(book.id)
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                ?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        shadowElevation = 3.dp,
    ) {
        if (embeddedCover != null) {
            Image(
                bitmap = embeddedCover!!,
                contentDescription = stringResource(R.string.vaultshelf_library_cover_description, book.title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            GeneratedBookCover(book = book)
        }
    }
}

@Composable
private fun GeneratedBookCover(book: LibraryBook) {
    val style = remember(book.id, book.title, book.format) { BookCoverStyle.from(book) }
    val background = COVER_COLORS[style.paletteIndex % COVER_COLORS.size]
    val titleSize = when {
        style.title.length > 36 -> 13.sp
        style.title.length > 22 -> 15.sp
        else -> 17.sp
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = style.formatLabel,
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = style.title,
            color = Color.White,
            fontSize = titleSize,
            lineHeight = (titleSize.value * 1.25f).sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.vaultshelf_app_name),
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun BookMenuButton(
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Text(
                text = "⋯",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vaultshelf_library_continue_reading)) },
                onClick = {
                    expanded = false
                    onOpen()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vaultshelf_library_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vaultshelf_library_info)) },
                onClick = {
                    expanded = false
                    onInfo()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vaultshelf_library_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun RenameBookDialog(
    book: LibraryBook,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vaultshelf_library_rename_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = false,
                maxLines = 3,
                label = { Text(stringResource(R.string.vaultshelf_library_rename_label)) },
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title) },
            ) {
                Text(stringResource(R.string.vaultshelf_library_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private fun detectBookFormat(context: Context, uri: Uri): BookFormat? {
    when (context.contentResolver.getType(uri)?.lowercase()) {
        "application/epub+zip" -> return BookFormat.EPUB
        "text/plain" -> return BookFormat.TXT
    }

    val displayName = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment

    return when (displayName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()) {
        "txt" -> BookFormat.TXT
        "epub" -> BookFormat.EPUB
        else -> null
    }
}

private val IMPORT_MIME_TYPES = arrayOf("text/plain", "application/epub+zip")

private val COVER_COLORS = listOf(
    Color(0xFF315A7D),
    Color(0xFF73536E),
    Color(0xFF49635A),
    Color(0xFF745640),
    Color(0xFF4E5878),
    Color(0xFF6A5E45),
)
