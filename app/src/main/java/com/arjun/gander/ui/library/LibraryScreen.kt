@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.arjun.gander.ui.library

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.arjun.gander.R
import com.arjun.gander.ViewerActivity
import com.arjun.gander.library.BookCoverStyle
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LibraryRepository
import com.arjun.gander.library.createReaderLaunchPlan
import com.arjun.gander.library.syncLegadoReaderProgress
import com.arjun.gander.transfer.TransferBehaviorPreferences
import com.arjun.gander.transfer.TransferRoute
import com.arjun.gander.transfer.TransferSourceDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList

private enum class ShelfViewMode { GRID, LIST }

private const val SHELF_UI_PREFERENCES = "vaultshelf_library_ui"
private const val PREF_GRID_COLUMNS = "grid_columns"
private const val DEFAULT_GRID_COLUMNS = 3

private enum class ShelfSort(@StringRes val labelRes: Int) {
    LAST_ACTIVITY(R.string.vaultshelf_library_sort_recent),
    TITLE(R.string.vaultshelf_library_sort_title),
    ADDED(R.string.vaultshelf_library_sort_added),
    PROGRESS(R.string.vaultshelf_library_sort_progress),
}

@Composable
fun LibraryScreen(
    repository: LibraryRepository,
    modifier: Modifier = Modifier,
    externalRevision: Int = 0,
    onImportToVaultFiles: ((List<LibraryBook>) -> Unit)? = null,
    onImportToVaultLibrary: ((List<LibraryBook>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shelfPreferences = remember {
        context.getSharedPreferences(SHELF_UI_PREFERENCES, Context.MODE_PRIVATE)
    }
    var books by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var importFailed by remember { mutableStateOf(false) }
    var viewModeName by rememberSaveable { mutableStateOf(ShelfViewMode.GRID.name) }
    var sortName by rememberSaveable { mutableStateOf(ShelfSort.LAST_ACTIVITY.name) }
    var gridColumns by rememberSaveable {
        mutableStateOf(
            shelfPreferences.getInt(PREF_GRID_COLUMNS, DEFAULT_GRID_COLUMNS).coerceIn(2, 6),
        )
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var exportBookIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var exportedSourceIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var bookToRename by remember { mutableStateOf<LibraryBook?>(null) }
    var bookToDelete by remember { mutableStateOf<LibraryBook?>(null) }
    var bookToInspect by remember { mutableStateOf<LibraryBook?>(null) }
    var pendingLegadoBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLegadoBookUrl by rememberSaveable { mutableStateOf<String?>(null) }

    val viewMode = runCatching { ShelfViewMode.valueOf(viewModeName) }.getOrDefault(ShelfViewMode.GRID)
    val sort = runCatching { ShelfSort.valueOf(sortName) }.getOrDefault(ShelfSort.LAST_ACTIVITY)
    val visibleBooks = remember(books, searchQuery, sort) {
        filterAndSortBooks(books, searchQuery, sort)
    }

    fun refresh() {
        scope.launch {
            loading = true
            books = repository.listBooks()
            selectedIds = selectedIds.intersect(books.mapTo(mutableSetOf()) { it.id })
            loading = false
        }
    }

    fun toggleSelection(book: LibraryBook) {
        selectedIds = selectedIds.toMutableSet().apply {
            if (!add(book.id)) remove(book.id)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { rootUri ->
        val ids = exportBookIds
        exportBookIds = emptyList()
        if (rootUri != null && ids.isNotEmpty()) {
            scope.launch {
                val selectedBooks = books.filter { it.id in ids }
                val exported = withContext(Dispatchers.IO) {
                    exportLibraryBooksToTree(context, repository, selectedBooks, rootUri)
                }
                if (exported.isEmpty()) {
                    importFailed = true
                } else {
                    when (
                        TransferBehaviorPreferences.automaticDecision(
                            context,
                            TransferRoute.EXTERNAL_LIBRARY_TO_EXTERNAL_FILES,
                        )
                    ) {
                        TransferSourceDecision.KEEP -> selectedIds = emptySet()
                        TransferSourceDecision.DELETE -> {
                            withContext(Dispatchers.IO) {
                                exported.forEach { repository.deleteBook(it) }
                            }
                            selectedIds = emptySet()
                            books = repository.listBooks()
                        }
                        null -> exportedSourceIds = exported
                    }
                    if (exported.size != selectedBooks.size) importFailed = true
                }
            }
        }
    }

    val readerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val legadoId = pendingLegadoBookId
        val legadoUrl = pendingLegadoBookUrl
        if (legadoId != null && legadoUrl != null) {
            pendingLegadoBookId = null
            pendingLegadoBookUrl = null
            scope.launch {
                syncLegadoReaderProgress(context, repository, legadoId, legadoUrl)
                refresh()
            }
            return@rememberLauncherForActivityResult
        }

        val data = result.data
        val bookId = data?.getStringExtra(ViewerActivity.EXTRA_LIBRARY_BOOK_ID)
        val hasProgress = data?.hasExtra(ViewerActivity.EXTRA_LIBRARY_PROGRESS) == true
        if (result.resultCode == android.app.Activity.RESULT_OK && bookId != null && hasProgress) {
            val progress = data?.getFloatExtra(ViewerActivity.EXTRA_LIBRARY_PROGRESS, 0f) ?: 0f
            scope.launch {
                repository.updateViewerProgress(bookId, progress)
                refresh()
            }
        } else {
            refresh()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            importFailed = false
            scope.launch {
                var failed = false
                uris.forEach { uri ->
                    runCatching {
                        val persistFlags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                persistFlags,
                            )
                        }.recoverCatching {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                        when (val format = detectBookFormat(context, uri)) {
                            BookFormat.TXT -> repository.importTxt(uri)
                            BookFormat.EPUB -> repository.importEpub(uri)
                            BookFormat.MARKDOWN -> repository.importMarkdown(uri)
                            BookFormat.PDF -> repository.importPdf(uri)
                            BookFormat.UMD -> repository.importUmd(uri)
                            BookFormat.MOBI, BookFormat.AZW3, BookFormat.AZW ->
                                repository.importMobi(uri, format)
                            null -> error("Unsupported library format")
                        }
                    }.onFailure { failed = true }
                }
                books = repository.listBooks()
                importFailed = failed
            }
        }
    }

    fun openBook(book: LibraryBook) {
        scope.launch {
            runCatching {
                createReaderLaunchPlan(context, repository, book)
            }.onSuccess { plan ->
                if (plan.legadoBookUrl != null) {
                    pendingLegadoBookId = book.id
                    pendingLegadoBookUrl = plan.legadoBookUrl
                }
                readerLauncher.launch(plan.intent)
            }.onFailure {
                // Cross-zone transfers can delete this shelf item while this Activity is
                // underneath the destination flow. Refresh instead of misreporting it as
                // an unsupported import when the user taps a stale in-memory card.
                refresh()
            }
        }
    }

    fun onBookClick(book: LibraryBook) {
        if (selectedIds.isEmpty()) {
            openBook(book)
        } else {
            toggleSelection(book)
        }
    }

    LaunchedEffect(repository, externalRevision) {
        books = repository.listBooks()
        selectedIds = selectedIds.intersect(books.mapTo(mutableSetOf()) { it.id })
        loading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectedIds.isNotEmpty()) {
            SelectionHeader(
                selectedCount = selectedIds.size,
                allVisibleSelected = visibleBooks.isNotEmpty() && visibleBooks.all { it.id in selectedIds },
                onClose = { selectedIds = emptySet() },
                onSelectAll = {
                    selectedIds = if (visibleBooks.isNotEmpty() && visibleBooks.all { it.id in selectedIds }) {
                        selectedIds - visibleBooks.mapTo(mutableSetOf()) { it.id }
                    } else {
                        selectedIds + visibleBooks.map { it.id }
                    }
                },
                onExportToFiles = {
                    exportBookIds = selectedIds.toList()
                    exportLauncher.launch(null)
                },
                onImportToVaultFiles = onImportToVaultFiles?.let { callback ->
                    {
                        val selectedBooks = books.filter { it.id in selectedIds }
                        if (selectedBooks.isNotEmpty()) callback(selectedBooks)
                        selectedIds = emptySet()
                    }
                },
                onImportToVaultLibrary = onImportToVaultLibrary?.let { callback ->
                    {
                        val selectedBooks = books.filter { it.id in selectedIds }
                        if (selectedBooks.isNotEmpty()) callback(selectedBooks)
                        selectedIds = emptySet()
                    }
                },
                onDelete = { confirmBatchDelete = true },
            )
        } else {
            ShelfHeader(
                bookCount = books.size,
                visibleCount = visibleBooks.size,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                sort = sort,
                onSortChange = { sortName = it.name },
                viewMode = viewMode,
                onViewModeChange = {
                    viewModeName = if (viewMode == ShelfViewMode.GRID) {
                        ShelfViewMode.LIST.name
                    } else {
                        ShelfViewMode.GRID.name
                    }
                },
                gridColumns = gridColumns,
                onGridColumnsChange = { columns ->
                    gridColumns = columns.coerceIn(2, 6)
                    shelfPreferences.edit { putInt(PREF_GRID_COLUMNS, gridColumns) }
                },
                onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
            )
        }

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

            visibleBooks.isEmpty() -> EmptySearch(
                onClear = { searchQuery = "" },
                modifier = Modifier.padding(16.dp),
            )

            viewMode == ShelfViewMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                gridItems(visibleBooks, key = { it.id }) { book ->
                    BookGridItem(
                        book = book,
                        repository = repository,
                        selected = book.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty(),
                        onOpen = { onBookClick(book) },
                        onLongPress = { toggleSelection(book) },
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
                items(visibleBooks, key = { it.id }) { book ->
                    BookListItem(
                        book = book,
                        repository = repository,
                        sizeLabel = Formatter.formatShortFileSize(context, book.sizeBytes),
                        selected = book.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty(),
                        onOpen = { onBookClick(book) },
                        onLongPress = { toggleSelection(book) },
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                repository.deleteBook(book.id)
                                books = repository.listBooks()
                                selectedIds = selectedIds - book.id
                                bookToDelete = null
                            }
                        },
                    ) {
                        Text(stringResource(R.string.vault_transfer_delete_library_only))
                    }
                    if (book.sourceUri != null) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val sourceDeleted = repository.deleteOriginalSource(book.id)
                                    if (sourceDeleted) repository.deleteBook(book.id)
                                    books = repository.listBooks()
                                    selectedIds = selectedIds - book.id
                                    bookToDelete = null
                                }
                            },
                        ) {
                            Text(stringResource(R.string.vault_transfer_delete_library_and_file))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (confirmBatchDelete) {
        val selectedBooks = books.filter { it.id in selectedIds }
        val hasLinkedSources = selectedBooks.any { it.sourceUri != null }
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text(stringResource(R.string.vaultshelf_library_batch_delete_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.vaultshelf_library_batch_delete_message,
                        selectedIds.size,
                        selectedIds.size,
                    ),
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                selectedIds.forEach { repository.deleteBook(it) }
                                selectedIds = emptySet()
                                books = repository.listBooks()
                                confirmBatchDelete = false
                            }
                        },
                    ) {
                        Text(stringResource(R.string.vault_transfer_delete_library_only))
                    }
                    if (hasLinkedSources) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    selectedBooks.forEach { book ->
                                        if (book.sourceUri == null || repository.deleteOriginalSource(book.id)) {
                                            repository.deleteBook(book.id)
                                        }
                                    }
                                    selectedIds = emptySet()
                                    books = repository.listBooks()
                                    confirmBatchDelete = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.vault_transfer_delete_library_and_file))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (exportedSourceIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { exportedSourceIds = emptyList() },
            title = { Text(stringResource(R.string.vault_transfer_done_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.vault_transfer_external_library_to_files_done,
                        exportedSourceIds.size,
                        exportedSourceIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = exportedSourceIds
                        exportedSourceIds = emptyList()
                        scope.launch {
                            ids.forEach { repository.deleteBook(it) }
                            selectedIds = emptySet()
                            books = repository.listBooks()
                        }
                    },
                ) {
                    Text(stringResource(R.string.vault_transfer_delete_source))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        exportedSourceIds = emptyList()
                        selectedIds = emptySet()
                    },
                ) {
                    Text(stringResource(R.string.vault_transfer_keep_source))
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

private fun filterAndSortBooks(
    books: List<LibraryBook>,
    query: String,
    sort: ShelfSort,
): List<LibraryBook> {
    val normalizedQuery = query.trim()
    val filtered = if (normalizedQuery.isEmpty()) {
        books
    } else {
        books.filter { it.title.contains(normalizedQuery, ignoreCase = true) }
    }

    return when (sort) {
        ShelfSort.LAST_ACTIVITY -> filtered.sortedWith(
            compareByDescending<LibraryBook> {
                if (it.lastOpenedAtEpochMillis > 0L) it.lastOpenedAtEpochMillis else it.addedAtEpochMillis
            }.thenBy { it.title.lowercase() },
        )
        ShelfSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
        ShelfSort.ADDED -> filtered.sortedByDescending { it.addedAtEpochMillis }
        ShelfSort.PROGRESS -> filtered.sortedWith(
            compareByDescending<LibraryBook> { it.progressFraction }
                .thenByDescending { it.lastOpenedAtEpochMillis },
        )
    }
}

@Composable
private fun ShelfHeader(
    bookCount: Int,
    visibleCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sort: ShelfSort,
    onSortChange: (ShelfSort) -> Unit,
    viewMode: ShelfViewMode,
    onViewModeChange: () -> Unit,
    gridColumns: Int,
    onGridColumnsChange: (Int) -> Unit,
    onImport: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var gridMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    text = if (searchQuery.isBlank()) {
                        stringResource(R.string.vaultshelf_library_book_count, bookCount)
                    } else {
                        pluralStringResource(
                            R.plurals.vaultshelf_library_search_result_count,
                            bookCount,
                            visibleCount,
                            bookCount,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.vaultshelf_library_search_hint)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Text(
                        stringResource(
                            R.string.vaultshelf_library_sort_button,
                            stringResource(sort.labelRes),
                        ),
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    ShelfSort.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(stringResource(item.labelRes)) },
                            onClick = {
                                sortMenuExpanded = false
                                onSortChange(item)
                            },
                        )
                    }
                }
            }
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
            if (viewMode == ShelfViewMode.GRID) {
                Box {
                    TextButton(onClick = { gridMenuExpanded = true }) {
                        Text(
                            pluralStringResource(
                                R.plurals.vaultshelf_library_grid_columns,
                                gridColumns,
                                gridColumns,
                            ),
                        )
                    }
                    DropdownMenu(
                        expanded = gridMenuExpanded,
                        onDismissRequest = { gridMenuExpanded = false },
                    ) {
                        (2..6).forEach { columns ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        pluralStringResource(
                                            R.plurals.vaultshelf_library_grid_columns,
                                            columns,
                                            columns,
                                        ),
                                    )
                                },
                                onClick = {
                                    gridMenuExpanded = false
                                    onGridColumnsChange(columns)
                                },
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.vaultshelf_library_manage_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    allVisibleSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onExportToFiles: () -> Unit,
    onImportToVaultFiles: (() -> Unit)?,
    onImportToVaultLibrary: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.vaultshelf_library_manage_done))
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.vaultshelf_library_selected_count,
                        selectedCount,
                        selectedCount,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSelectAll) {
                    Text(
                        stringResource(
                            if (allVisibleSelected) {
                                R.string.vaultshelf_library_unselect_all
                            } else {
                                R.string.vaultshelf_library_select_all
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionAction(
                    text = stringResource(R.string.vault_transfer_external_library_to_files),
                    onClick = onExportToFiles,
                    modifier = Modifier.weight(1f),
                )
                if (onImportToVaultFiles != null) {
                    SelectionAction(
                        text = stringResource(R.string.vault_transfer_to_vault_files),
                        onClick = onImportToVaultFiles,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onImportToVaultLibrary != null) {
                    SelectionAction(
                        text = stringResource(R.string.vault_transfer_to_vault_library),
                        onClick = onImportToVaultLibrary,
                        modifier = Modifier.weight(1f),
                    )
                }
                SelectionAction(
                    text = stringResource(R.string.vaultshelf_library_delete),
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    error = true,
                )
            }
        }
    }
}

@Composable
private fun SelectionAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
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
private fun EmptySearch(
    onClear: () -> Unit,
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
                text = stringResource(R.string.vaultshelf_library_search_empty_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.vaultshelf_library_search_empty_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.vaultshelf_library_search_clear))
            }
        }
    }
}

@Composable
private fun BookGridItem(
    book: LibraryBook,
    repository: LibraryRepository,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
) {
    val selectedBorder = if (selected) {
        Modifier.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(12.dp),
        )
    } else {
        Modifier
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            BookCover(
                book = book,
                repository = repository,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .then(selectedBorder)
                    .combinedClickable(
                        onClick = onOpen,
                        onLongClick = onLongPress,
                    ),
            )
            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else if (!selectionMode) {
                BookMenuButton(
                    onOpen = onOpen,
                    onRename = onRename,
                    onDelete = onDelete,
                    onInfo = onInfo,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            LinearProgressIndicator(
                progress = { book.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            minLines = 2,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.vaultshelf_library_grid_meta,
                book.format.name,
                book.progressPercent,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BookListItem(
    book: LibraryBook,
    repository: LibraryRepository,
    sizeLabel: String,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress,
            ),
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
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
                    fontWeight = FontWeight.Medium,
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
            if (selected) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            } else if (!selectionMode) {
                BookMenuButton(
                    onOpen = onOpen,
                    onRename = onRename,
                    onDelete = onDelete,
                    onInfo = onInfo,
                )
            }
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
        style.title.length > 30 -> 12.sp
        style.title.length > 18 -> 14.sp
        else -> 16.sp
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
            maxLines = 4,
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

private suspend fun exportLibraryBooksToTree(
    context: Context,
    repository: LibraryRepository,
    books: List<LibraryBook>,
    rootUri: Uri,
): List<String> {
    val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
    val exported = ArrayList<String>()
    books.forEach { book ->
        val source = runCatching { repository.bookFile(book.id) }
            .getOrNull()
            ?: return@forEach
        val extension = BookFormat.extension(book.format)
        val baseName = book.title
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .ifBlank { "book" }
        var fileName = "$baseName.$extension"
        var index = 2
        while (root.findFile(fileName) != null) {
            fileName = "$baseName ($index).$extension"
            index += 1
        }
        val target = root.createFile("application/octet-stream", fileName) ?: return@forEach
        val copied = runCatching {
            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                source.inputStream().buffered().use { input -> input.copyTo(output) }
            } ?: error("Unable to open export destination")
        }.isSuccess
        if (copied) exported += book.id else runCatching { target.delete() }
    }
    return exported
}

private fun detectBookFormat(context: Context, uri: Uri): BookFormat? {
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

    when (displayName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()) {
        "txt" -> return BookFormat.TXT
        "epub" -> return BookFormat.EPUB
        "md", "markdown" -> return BookFormat.MARKDOWN
        "pdf" -> return BookFormat.PDF
        "umd" -> return BookFormat.UMD
        "mobi" -> return BookFormat.MOBI
        "azw3" -> return BookFormat.AZW3
        "azw" -> return BookFormat.AZW
    }

    return when (context.contentResolver.getType(uri)?.lowercase()) {
        "application/epub+zip" -> BookFormat.EPUB
        "text/markdown" -> BookFormat.MARKDOWN
        "application/pdf" -> BookFormat.PDF
        "application/mobi", "application/x-mobipocket-ebook" -> BookFormat.MOBI
        "application/azw3", "application/x-mobi8-ebook" -> BookFormat.AZW3
        "application/azw" -> BookFormat.AZW
        "text/plain" -> BookFormat.TXT
        else -> null
    }
}

private val IMPORT_MIME_TYPES = arrayOf("*/*")

private val COVER_COLORS = listOf(
    Color(0xFF315A7D),
    Color(0xFF73536E),
    Color(0xFF49635A),
    Color(0xFF745640),
    Color(0xFF4E5878),
    Color(0xFF6A5E45),
)
