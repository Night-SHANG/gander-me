package com.arjun.gander.vault

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arjun.gander.R
import com.arjun.gander.library.BookCoverStyle
import com.arjun.gander.library.LibraryBook
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VaultModeDestination {
    HOME,
    LIBRARY,
    SETTINGS,
}

@Composable
fun VaultModeShell(
    volumeName: String,
    fileRepository: VaultFileRepository,
    libraryStore: VaultLibraryStore,
    externalRevision: Int,
    initialLibrary: Boolean,
    onOpenFile: (VaultFileItem) -> Unit,
    onOpenFiles: () -> Unit,
    onOpenVaultSettings: () -> Unit,
    onOpenVaultBackup: () -> Unit,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedName by rememberSaveable {
        mutableStateOf(
            if (initialLibrary) VaultModeDestination.LIBRARY.name else VaultModeDestination.HOME.name,
        )
    }
    var revision by remember { mutableIntStateOf(0) }
    val selected = VaultModeDestination.entries
        .firstOrNull { it.name == selectedName }
        ?: VaultModeDestination.HOME

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    VaultNavItem(
                        selected == VaultModeDestination.HOME,
                        R.drawable.ic_vaultshelf_home,
                        R.string.vaultshelf_nav_home,
                    ) { selectedName = VaultModeDestination.HOME.name }
                    VaultNavItem(
                        selected == VaultModeDestination.LIBRARY,
                        R.drawable.ic_vaultshelf_library,
                        R.string.vaultshelf_nav_library,
                    ) { selectedName = VaultModeDestination.LIBRARY.name }
                    VaultNavItem(
                        false,
                        R.drawable.ic_vaultshelf_files,
                        R.string.vaultshelf_nav_files,
                    ) { onOpenFiles() }
                    VaultNavItem(
                        selected == VaultModeDestination.SETTINGS,
                        R.drawable.ic_vaultshelf_settings,
                        R.string.vaultshelf_nav_settings,
                    ) { selectedName = VaultModeDestination.SETTINGS.name }
                }
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
                onOpenBook = { entry ->
                    libraryStore.markOpened(entry.id)
                    val stat = fileRepository.volume.getAttr(entry.path)
                    if (stat != null) {
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
                libraryStore = libraryStore,
                revision = revision + externalRevision,
                onOpen = { entry ->
                    libraryStore.markOpened(entry.id)
                    val stat = fileRepository.volume.getAttr(entry.path)
                    if (stat != null) {
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
                onRemove = { entry ->
                    libraryStore.remove(entry.id)
                    revision += 1
                },
                onOpenFiles = onOpenFiles,
                modifier = contentModifier,
            )

            VaultModeDestination.SETTINGS -> VaultSettingsScreen(
                volumeName = volumeName,
                onOpenVaultSettings = onOpenVaultSettings,
                onOpenVaultBackup = onOpenVaultBackup,
                onLockVault = onLockVault,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun RowScope.VaultNavItem(
    selected: Boolean,
    iconRes: Int,
    labelRes: Int,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else Color.Transparent,
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 3.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun VaultHomeScreen(
    volumeName: String,
    libraryStore: VaultLibraryStore,
    revision: Int,
    onOpenLibrary: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenBook: (VaultLibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var books by remember { mutableStateOf<List<VaultLibraryEntry>>(emptyList()) }
    LaunchedEffect(revision) {
        books = withContext(Dispatchers.IO) { libraryStore.listBooks() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = onOpenLibrary, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.vaultshelf_nav_library))
            }
            Button(onClick = onOpenFiles, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.vaultshelf_nav_files))
            }
        }
        Text(
            text = stringResource(R.string.vault_home_recent),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (books.none { it.lastOpenedAtEpochMillis > 0L }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    books.filter { it.lastOpenedAtEpochMillis > 0L }.take(6),
                    key = { it.id },
                ) { book ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenBook(book) },
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = book.format.name,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = book.title,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultLibraryScreen(
    libraryStore: VaultLibraryStore,
    revision: Int,
    onOpen: (VaultLibraryEntry) -> Unit,
    onRemove: (VaultLibraryEntry) -> Unit,
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var books by remember { mutableStateOf<List<VaultLibraryEntry>>(emptyList()) }
    LaunchedEffect(revision) {
        books = withContext(Dispatchers.IO) { libraryStore.listBooks() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.vault_library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = pluralStringResource(R.plurals.vault_library_count, books.size, books.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenFiles) {
                Text(stringResource(R.string.vault_library_add_from_files))
            }
        }

        if (books.isEmpty()) {
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
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            gridItems(books, key = { it.id }) { entry ->
                VaultBookCard(entry, onOpen, onRemove)
            }
        }
    }
}

@Composable
private fun VaultBookCard(
    entry: VaultLibraryEntry,
    onOpen: (VaultLibraryEntry) -> Unit,
    onRemove: (VaultLibraryEntry) -> Unit,
) {
    var menuExpanded by remember(entry.id) { mutableStateOf(false) }
    val fakeBook = remember(entry) {
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(entry) },
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier
                        .background(coverColor)
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = style.formatLabel,
                        color = Color.White.copy(alpha = 0.76f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = style.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 30.dp),
                    )
                    Text(
                        text = stringResource(R.string.vault_mode_title),
                        color = Color.White.copy(alpha = 0.56f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Text("⋯", color = Color.White)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.vault_library_remove)) },
                    onClick = {
                        menuExpanded = false
                        onRemove(entry)
                    },
                )
            }
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VaultSettingsScreen(
    volumeName: String,
    onOpenVaultSettings: () -> Unit,
    onOpenVaultBackup: () -> Unit,
    onLockVault: () -> Unit,
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
        Button(onClick = onOpenVaultSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.vault_settings_security))
        }
        Button(onClick = onOpenVaultBackup, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.vault_settings_backup))
        }
        TextButton(onClick = onLockVault, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.vault_settings_lock),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun parentVaultPath(path: String): String {
    val normalized = path.trimEnd('/')
    val parent = normalized.substringBeforeLast('/', missingDelimiterValue = "")
    return parent.takeUnless { it == "/" }.orEmpty()
}

private val VAULT_COVER_COLORS = listOf(
    Color(0xFF315A7D),
    Color(0xFF73536E),
    Color(0xFF49635A),
    Color(0xFF745640),
    Color(0xFF4E5878),
    Color(0xFF6A5E45),
)
