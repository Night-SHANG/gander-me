@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.arjun.gander.vault

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.getBottom
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import com.arjun.gander.library.BookCoverStyle
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.transfer.TransferBehaviorPreferences
import com.arjun.gander.transfer.TransferRoute
import com.arjun.gander.transfer.TransferSourceDecision
import com.arjun.gander.ui.shell.VaultShelfBottomBar
import com.arjun.gander.ui.shell.VaultShelfDestination
import com.arjun.gander.ui.shell.VaultShelfExternalDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayList

private enum class VaultModeDestination {
    HOME,
    LIBRARY,
    SETTINGS,
}

private enum class VaultLibraryCompletedTransfer {
    EXTERNAL_FILES,
    EXTERNAL_LIBRARY,
}

private enum class VaultLibraryViewMode { GRID, LIST }

private enum class VaultLibrarySort(@StringRes val labelRes: Int) {
    LAST_ACTIVITY(R.string.vaultshelf_library_sort_recent),
    TITLE(R.string.vaultshelf_library_sort_title),
    ADDED(R.string.vaultshelf_library_sort_added),
}

private const val VAULT_LIBRARY_UI_PREFERENCES = "vaultshelf_vault_library_ui"
private const val VAULT_LIBRARY_GRID_COLUMNS = "grid_columns"
private const val DEFAULT_VAULT_LIBRARY_GRID_COLUMNS = 3

@Composable
fun VaultModeShell(
    volumeName: String,
    fileRepository: VaultFileRepository,
    libraryStore: VaultLibraryStore,
    externalRevision: Int,
    onOpenFile: (VaultFileItem) -> Unit,
    onOpenFiles: () -> Unit,
    onExportLibraryToVaultFiles: (List<VaultLibraryEntry>) -> Unit,
    onOpenVaultSettings: () -> Unit,
    onOpenVaultBackup: () -> Unit,
    onLockVault: () -> Unit,
    onOpenVaultSwitcher: () -> Unit,
    onOpenExternalDestination: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialDestinationName: String = VaultModeDestination.HOME.name,
) {
    var selectedName by rememberSaveable {
        mutableStateOf(
            VaultModeDestination.entries
                .firstOrNull { it.name == initialDestinationName }
                ?.name
                ?: VaultModeDestination.HOME.name,
        )
    }
    var revision by remember { mutableIntStateOf(0) }
    val selected = VaultModeDestination.entries
        .firstOrNull { it.name == selectedName }
        ?: VaultModeDestination.HOME
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!imeVisible) {
                VaultShelfBottomBar(
                    destinations = VaultShelfExternalDestinations,
                    selected = VaultShelfDestination.VAULT,
                    onSelected = { destination ->
                        if (destination == VaultShelfDestination.VAULT) {
                            onOpenVaultSwitcher()
                        } else {
                            onOpenExternalDestination(destination.name)
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (selected) {
            VaultModeDestination.HOME -> VaultHomeScreen(
                volumeName = volumeName,
                libraryStore = libraryStore,
                revision = revision + externalRevision,
                onOpenLibrary = { selectedName = VaultModeDestination.LIBRARY.name },
                onOpenFiles = onOpenFiles,
                onOpenExternalDestination = onOpenExternalDestination,
                onOpenBook = { entry ->
                    libraryStore.markOpened(entry.id)
                    fileRepository.volume.getAttr(entry.path)?.let { stat ->
                        onOpenFile(
                            VaultFileItem(
                                name = File(entry.path).name,
                                path = entry.path,
                                sizeBytes = stat.size.coerceAtLeast(0L),
                                modifiedAtEpochMillis = stat.mTime.coerceAtLeast(0L),
                                isDirectory = false,
                            ),
                        )
                        revision += 1
                    }
                },
                modifier = contentModifier,
            )

            VaultModeDestination.LIBRARY -> VaultLibraryScreen(
                fileRepository = fileRepository,
                libraryStore = libraryStore,
                revision = revision + externalRevision,
                onOpen = { entry ->
                    libraryStore.markOpened(entry.id)
                    fileRepository.volume.getAttr(entry.path)?.let { stat ->
                        onOpenFile(
                            VaultFileItem(
                                name = File(entry.path).name,
                                path = entry.path,
                                sizeBytes = stat.size.coerceAtLeast(0L),
                                modifiedAtEpochMillis = stat.mTime.coerceAtLeast(0L),
                                isDirectory = false,
                            ),
                        )
                        revision += 1
                    }
                },
                onOpenFiles = onOpenFiles,
                onExportToVaultFiles = onExportLibraryToVaultFiles,
                onRevision = { revision += 1 },
                modifier = contentModifier,
            )

            VaultModeDestination.SETTINGS -> VaultSettingsScreen(
                volumeName = volumeName,
                onOpenVaultSettings = onOpenVaultSettings,
                onOpenVaultBackup = onOpenVaultBackup,
                onLockVault = onLockVault,
                onOpenVaultSwitcher = onOpenVaultSwitcher,
                onOpenExternalDestination = onOpenExternalDestination,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun VaultHomeScreen(
    volumeName: String,
    libraryStore: VaultLibraryStore,
    revision: Int,
    onOpenLibrary: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenExternalDestination: (String) -> Unit,
    onOpenBook: (VaultLibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var books by remember { mutableStateOf<List<VaultLibraryEntry>>(emptyList()) }
    LaunchedEffect(revision) {
        books = withContext(Dispatchers.IO) { libraryStore.listBooks() }
    }
    val recent = books.filter { it.lastOpenedAtEpochMillis > 0L }.take(6)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = stringResource(R.string.vault_mode_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = volumeName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = onOpenLibrary, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.vaultshelf_nav_library))
            }
            Button(onClick = onOpenFiles, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.vaultshelf_nav_files))
            }
        }
        TextButton(
            onClick = { onOpenExternalDestination("HOME") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.vault_return_external_home))
        }
        Text(
            text = stringResource(R.string.vault_home_recent),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (recent.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp,
            ) {
                Text(
                    text = stringResource(R.string.vault_home_recent_empty),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(recent, key = { it.id }) { entry ->
                    Column(
                        modifier = Modifier
                            .size(width = 108.dp, height = 190.dp)
                            .combinedClickable(
                                onClick = { onOpenBook(entry) },
                                onLongClick = { onOpenLibrary() },
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        VaultGeneratedBookCover(
                            entry = entry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.68f),
                        )
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultLibraryScreen(
    fileRepository: VaultFileRepository,
    libraryStore: VaultLibraryStore,
    revision: Int,
    onOpen: (VaultLibraryEntry) -> Unit,
    onOpenFiles: () -> Unit,
    onExportToVaultFiles: (List<VaultLibraryEntry>) -> Unit,
    onRevision: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val externalRepository = remember { LocalLibraryRepository(context.applicationContext) }
    val shelfPreferences = remember(fileRepository.volumeUuid) {
        context.getSharedPreferences(
            "$VAULT_LIBRARY_UI_PREFERENCES:${fileRepository.volumeUuid}",
            Context.MODE_PRIVATE,
        )
    }
    var books by remember { mutableStateOf<List<VaultLibraryEntry>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(VaultLibrarySort.LAST_ACTIVITY.name) }
    var viewModeName by rememberSaveable { mutableStateOf(VaultLibraryViewMode.GRID.name) }
    var gridColumns by rememberSaveable(fileRepository.volumeUuid) {
        mutableIntStateOf(
            shelfPreferences.getInt(
                VAULT_LIBRARY_GRID_COLUMNS,
                DEFAULT_VAULT_LIBRARY_GRID_COLUMNS,
            ).coerceIn(2, 6),
        )
    }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var exportToExternalIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var completedTransfer by remember { mutableStateOf<VaultLibraryCompletedTransfer?>(null) }
    var completedSourceIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val sort = runCatching { VaultLibrarySort.valueOf(sortName) }
        .getOrDefault(VaultLibrarySort.LAST_ACTIVITY)
    val viewMode = runCatching { VaultLibraryViewMode.valueOf(viewModeName) }
        .getOrDefault(VaultLibraryViewMode.GRID)
    val visibleBooks = remember(books, searchQuery, sort) {
        filterAndSortVaultBooks(books, searchQuery, sort)
    }

    fun refresh() {
        scope.launch {
            books = withContext(Dispatchers.IO) { libraryStore.listBooks() }
            selectedIds = selectedIds.intersect(books.mapTo(mutableSetOf()) { it.id })
            onRevision()
        }
    }

    fun completeExternalTransfer(
        route: TransferRoute,
        transfer: VaultLibraryCompletedTransfer,
        ids: List<String>,
    ) {
        when (TransferBehaviorPreferences.automaticDecision(context, route)) {
            TransferSourceDecision.KEEP -> selectedIds = emptySet()
            TransferSourceDecision.DELETE -> {
                scope.launch(Dispatchers.IO) {
                    ids.forEach { libraryStore.remove(it) }
                    withContext(Dispatchers.Main) {
                        selectedIds = emptySet()
                        refresh()
                    }
                }
            }
            null -> {
                completedTransfer = transfer
                completedSourceIds = ids
            }
        }
    }

    val exportExternal = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { rootUri ->
        val ids = exportToExternalIds
        exportToExternalIds = emptyList()
        if (rootUri != null && ids.isNotEmpty()) {
            scope.launch {
                val selected = books.filter { it.id in ids }
                val exported = withContext(Dispatchers.IO) {
                    exportVaultLibraryBooksToTree(
                        context,
                        fileRepository,
                        selected,
                        rootUri,
                    )
                }
                if (exported.isNotEmpty()) {
                    completeExternalTransfer(
                        TransferRoute.VAULT_LIBRARY_TO_EXTERNAL_FILES,
                        VaultLibraryCompletedTransfer.EXTERNAL_FILES,
                        exported,
                    )
                }
            }
        }
    }

    LaunchedEffect(revision) {
        books = withContext(Dispatchers.IO) { libraryStore.listBooks() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectedIds.isNotEmpty()) {
            VaultLibrarySelectionHeader(
                selectedCount = selectedIds.size,
                allSelected = visibleBooks.isNotEmpty() &&
                    visibleBooks.all { it.id in selectedIds },
                onClose = { selectedIds = emptySet() },
                onSelectAll = {
                    selectedIds = if (
                        visibleBooks.isNotEmpty() &&
                        visibleBooks.all { it.id in selectedIds }
                    ) {
                        selectedIds - visibleBooks.mapTo(mutableSetOf()) { it.id }
                    } else {
                        selectedIds + visibleBooks.map { it.id }
                    }
                },
                onExportExternalFiles = {
                    if (!VaultSecurityPolicy.allowPlaintextExport(context)) {
                        Toast.makeText(context, R.string.vault_export_blocked, Toast.LENGTH_SHORT).show()
                    } else {
                        exportToExternalIds = selectedIds.toList()
                        exportExternal.launch(null)
                    }
                },
                onExportExternalLibrary = {
                    if (!VaultSecurityPolicy.allowPlaintextExport(context)) {
                        Toast.makeText(context, R.string.vault_export_blocked, Toast.LENGTH_SHORT).show()
                    } else {
                    val selected = books.filter { it.id in selectedIds }
                    scope.launch {
                        val exported = withContext(Dispatchers.IO) {
                            selected.mapNotNull { entry ->
                                runCatching {
                                    libraryStore.exportToExternalLibrary(entry, externalRepository)
                                    entry.id
                                }.getOrNull()
                            }
                        }
                        if (exported.isNotEmpty()) {
                            completeExternalTransfer(
                                TransferRoute.VAULT_LIBRARY_TO_EXTERNAL_LIBRARY,
                                VaultLibraryCompletedTransfer.EXTERNAL_LIBRARY,
                                exported,
                            )
                        }
                    }
                    }
                },
                onExportVaultFiles = {
                    val selected = books.filter { it.id in selectedIds }
                    if (selected.isNotEmpty()) {
                        onExportToVaultFiles(selected)
                        selectedIds = emptySet()
                    }
                },
                onDelete = { confirmDelete = true },
            )
        } else {
            VaultLibraryHeader(
                itemCount = books.size,
                visibleCount = visibleBooks.size,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                sort = sort,
                onSortChange = { sortName = it.name },
                viewMode = viewMode,
                onViewModeChange = {
                    viewModeName = if (viewMode == VaultLibraryViewMode.GRID) {
                        VaultLibraryViewMode.LIST.name
                    } else {
                        VaultLibraryViewMode.GRID.name
                    }
                },
                gridColumns = gridColumns,
                onGridColumnsChange = { columns ->
                    gridColumns = columns.coerceIn(2, 6)
                    shelfPreferences.edit {
                        putInt(VAULT_LIBRARY_GRID_COLUMNS, gridColumns)
                    }
                },
                onOpenFiles = onOpenFiles,
            )
        }

        when {
            books.isEmpty() -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.vault_library_empty),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.vault_library_empty_detail),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenFiles) {
                            Text(stringResource(R.string.vault_library_add_from_files))
                        }
                    }
                }
            }

            visibleBooks.isEmpty() -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.vaultshelf_library_search_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.vaultshelf_library_search_empty_detail),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { searchQuery = "" }) {
                            Text(stringResource(R.string.vaultshelf_library_search_clear))
                        }
                    }
                }
            }

            viewMode == VaultLibraryViewMode.GRID -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 8.dp,
                        bottom = 28.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    gridItems(visibleBooks, key = { it.id }) { entry ->
                        val selected = entry.id in selectedIds
                        VaultBookCard(
                            entry = entry,
                            selected = selected,
                            selectionMode = selectedIds.isNotEmpty(),
                            onOpen = {
                                if (selectedIds.isEmpty()) onOpen(entry)
                                else {
                                    selectedIds = selectedIds.toMutableSet().apply {
                                        if (!add(entry.id)) remove(entry.id)
                                    }
                                }
                            },
                            onLongPress = {
                                selectedIds = selectedIds.toMutableSet().apply {
                                    if (!add(entry.id)) remove(entry.id)
                                }
                            },
                            onRemove = {
                                selectedIds = setOf(entry.id)
                                confirmDelete = true
                            },
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 8.dp,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleBooks, key = { it.id }) { entry ->
                        val selected = entry.id in selectedIds
                        VaultBookListItem(
                            entry = entry,
                            selected = selected,
                            selectionMode = selectedIds.isNotEmpty(),
                            onOpen = {
                                if (selectedIds.isEmpty()) onOpen(entry)
                                else {
                                    selectedIds = selectedIds.toMutableSet().apply {
                                        if (!add(entry.id)) remove(entry.id)
                                    }
                                }
                            },
                            onLongPress = {
                                selectedIds = selectedIds.toMutableSet().apply {
                                    if (!add(entry.id)) remove(entry.id)
                                }
                            },
                            onRemove = {
                                selectedIds = setOf(entry.id)
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val selected = books.filter { it.id in selectedIds }
        val hasLinkedFiles = selected.any { it.sourcePath != null }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.vaultshelf_library_batch_delete_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.vaultshelf_library_batch_delete_message,
                        selected.size,
                        selected.size,
                    ),
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                selected.forEach { libraryStore.remove(it.id) }
                                withContext(Dispatchers.Main) {
                                    selectedIds = emptySet()
                                    confirmDelete = false
                                    refresh()
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.vault_transfer_delete_library_only))
                    }
                    if (hasLinkedFiles) {
                        TextButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    selected.forEach {
                                        libraryStore.remove(it.id, deleteLinkedSource = true)
                                    }
                                    withContext(Dispatchers.Main) {
                                        selectedIds = emptySet()
                                        confirmDelete = false
                                        refresh()
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.vault_transfer_delete_library_and_file))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    completedTransfer?.let { transfer ->
        val count = completedSourceIds.size
        AlertDialog(
            onDismissRequest = {
                completedTransfer = null
                completedSourceIds = emptyList()
            },
            title = { Text(stringResource(R.string.vault_transfer_done_title)) },
            text = {
                Text(
                    pluralStringResource(
                        when (transfer) {
                            VaultLibraryCompletedTransfer.EXTERNAL_FILES ->
                                R.plurals.vault_transfer_vault_library_to_external_files_done
                            VaultLibraryCompletedTransfer.EXTERNAL_LIBRARY ->
                                R.plurals.vault_transfer_vault_library_to_external_library_done
                        },
                        count,
                        count,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = completedSourceIds
                        completedTransfer = null
                        completedSourceIds = emptyList()
                        scope.launch(Dispatchers.IO) {
                            ids.forEach { libraryStore.remove(it) }
                            withContext(Dispatchers.Main) {
                                selectedIds = emptySet()
                                refresh()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.vault_transfer_delete_source))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        completedTransfer = null
                        completedSourceIds = emptyList()
                        selectedIds = emptySet()
                    },
                ) {
                    Text(stringResource(R.string.vault_transfer_keep_source))
                }
            },
        )
    }
}

private fun filterAndSortVaultBooks(
    books: List<VaultLibraryEntry>,
    query: String,
    sort: VaultLibrarySort,
): List<VaultLibraryEntry> {
    val normalizedQuery = query.trim()
    val filtered = if (normalizedQuery.isEmpty()) {
        books
    } else {
        books.filter { it.title.contains(normalizedQuery, ignoreCase = true) }
    }
    return when (sort) {
        VaultLibrarySort.LAST_ACTIVITY -> filtered.sortedWith(
            compareByDescending<VaultLibraryEntry> {
                if (it.lastOpenedAtEpochMillis > 0L) it.lastOpenedAtEpochMillis else it.addedAtEpochMillis
            }.thenBy { it.title.lowercase() },
        )
        VaultLibrarySort.TITLE -> filtered.sortedBy { it.title.lowercase() }
        VaultLibrarySort.ADDED -> filtered.sortedByDescending { it.addedAtEpochMillis }
    }
}

@Composable
private fun VaultLibraryHeader(
    itemCount: Int,
    visibleCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sort: VaultLibrarySort,
    onSortChange: (VaultLibrarySort) -> Unit,
    viewMode: VaultLibraryViewMode,
    onViewModeChange: () -> Unit,
    gridColumns: Int,
    onGridColumnsChange: (Int) -> Unit,
    onOpenFiles: () -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var gridExpanded by remember { mutableStateOf(false) }

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
                    text = stringResource(R.string.vault_library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (searchQuery.isBlank()) {
                        pluralStringResource(R.plurals.vault_library_count, itemCount, itemCount)
                    } else {
                        pluralStringResource(
                            R.plurals.vaultshelf_library_search_result_count,
                            itemCount,
                            visibleCount,
                            itemCount,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenFiles) {
                Text(stringResource(R.string.vault_library_add_from_files))
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
                TextButton(onClick = { sortExpanded = true }) {
                    Text(
                        stringResource(
                            R.string.vaultshelf_library_sort_button,
                            stringResource(sort.labelRes),
                        ),
                    )
                }
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                ) {
                    VaultLibrarySort.entries.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(stringResource(item.labelRes)) },
                            onClick = {
                                sortExpanded = false
                                onSortChange(item)
                            },
                        )
                    }
                }
            }
            TextButton(onClick = onViewModeChange) {
                Text(
                    stringResource(
                        if (viewMode == VaultLibraryViewMode.GRID) {
                            R.string.vaultshelf_library_view_list
                        } else {
                            R.string.vaultshelf_library_view_grid
                        },
                    ),
                )
            }
            if (viewMode == VaultLibraryViewMode.GRID) {
                Box {
                    TextButton(onClick = { gridExpanded = true }) {
                        Text(
                            pluralStringResource(
                                R.plurals.vaultshelf_library_grid_columns,
                                gridColumns,
                                gridColumns,
                            ),
                        )
                    }
                    DropdownMenu(
                        expanded = gridExpanded,
                        onDismissRequest = { gridExpanded = false },
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
                                    gridExpanded = false
                                    onGridColumnsChange(columns)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultLibrarySelectionHeader(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onExportExternalFiles: () -> Unit,
    onExportExternalLibrary: () -> Unit,
    onExportVaultFiles: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                            if (allSelected) {
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
            ) {
                VaultSelectionAction(
                    text = stringResource(R.string.vault_transfer_to_external_files),
                    onClick = onExportExternalFiles,
                    modifier = Modifier.weight(1f),
                )
                VaultSelectionAction(
                    text = stringResource(R.string.vault_transfer_to_external_library),
                    onClick = onExportExternalLibrary,
                    modifier = Modifier.weight(1f),
                )
                VaultSelectionAction(
                    text = stringResource(R.string.vault_transfer_vault_library_to_files),
                    onClick = onExportVaultFiles,
                    modifier = Modifier.weight(1f),
                )
                VaultSelectionAction(
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
private fun VaultSelectionAction(
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
            maxLines = 2,
            textAlign = TextAlign.Center,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun VaultBookCard(
    entry: VaultLibraryEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            VaultGeneratedBookCover(
                entry = entry,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .then(
                        if (selected) {
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(12.dp),
                            )
                        } else {
                            Modifier
                        },
                    )
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
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else if (!selectionMode) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.align(Alignment.TopEnd),
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "⋯",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            minLines = 2,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = entry.format.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VaultBookListItem(
    entry: VaultLibraryEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress,
            ),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.format.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
            } else if (!selectionMode) {
                TextButton(onClick = onRemove) {
                    Text("⋯", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun VaultGeneratedBookCover(
    entry: VaultLibraryEntry,
    modifier: Modifier = Modifier,
) {
    val fakeBook = remember(entry.id, entry.title, entry.format) {
        LibraryBook(
            id = entry.id,
            title = entry.title,
            storedFileName = entry.path,
            format = entry.format,
            sizeBytes = entry.sizeBytes,
            totalCharacters = 0,
            addedAtEpochMillis = entry.addedAtEpochMillis,
            lastOpenedAtEpochMillis = entry.lastOpenedAtEpochMillis,
            readingOffset = 0,
        )
    }
    val style = remember(fakeBook) { BookCoverStyle.from(fakeBook) }
    val coverColor = VAULT_COVER_COLORS[style.paletteIndex % VAULT_COVER_COLORS.size]
    val titleSize = when {
        style.title.length > 30 -> 12.sp
        style.title.length > 18 -> 14.sp
        else -> 16.sp
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(coverColor)
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
                text = stringResource(R.string.vault_mode_title),
                color = Color.White.copy(alpha = 0.62f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private suspend fun exportVaultLibraryBooksToTree(
    context: Context,
    fileRepository: VaultFileRepository,
    books: List<VaultLibraryEntry>,
    rootUri: Uri,
): List<String> {
    val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
    val exported = ArrayList<String>()
    books.forEach { entry ->
        val extension = com.arjun.gander.library.BookFormat.extension(entry.format)
        val baseName = VaultFileRepository.sanitizeFileName(entry.title).ifBlank { "book" }
        var fileName = "$baseName.$extension"
        var index = 2
        while (root.findFile(fileName) != null) {
            fileName = "$baseName ($index).$extension"
            index += 1
        }
        val target = root.createFile("application/octet-stream", fileName) ?: return@forEach
        val copied = runCatching {
            val output = context.contentResolver.openOutputStream(target.uri)
                ?: error("Unable to open export destination")
            fileRepository.volume.exportFile(entry.path, output)
        }.getOrDefault(false)
        if (copied) exported += entry.id else runCatching { target.delete() }
    }
    return exported
}

@Composable
private fun VaultSettingsScreen(
    volumeName: String,
    onOpenVaultSettings: () -> Unit,
    onOpenVaultBackup: () -> Unit,
    onLockVault: () -> Unit,
    onOpenVaultSwitcher: () -> Unit,
    onOpenExternalDestination: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.vault_settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = volumeName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenVaultSwitcher, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.vault_switch_volume))
        }
        Button(onClick = onOpenVaultSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.vault_settings_security))
        }
        Button(onClick = onOpenVaultBackup, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.vault_settings_backup))
        }
        TextButton(onClick = { onOpenExternalDestination("HOME") }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.vault_return_external_home))
        }
        TextButton(onClick = onLockVault, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.vault_settings_lock),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

}

private val VAULT_COVER_COLORS = listOf(
    Color(0xFF315A7D),
    Color(0xFF73536E),
    Color(0xFF49635A),
    Color(0xFF745640),
    Color(0xFF4E5878),
    Color(0xFF6A5E45),
)
