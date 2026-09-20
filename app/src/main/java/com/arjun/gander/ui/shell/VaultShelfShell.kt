package com.arjun.gander.ui.shell

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.gander.R
import com.arjun.gander.ViewerActivity
import com.arjun.gander.library.BookCoverStyle
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LibraryRepository
import com.arjun.gander.library.createReaderLaunchPlan
import com.arjun.gander.library.syncLegadoReaderProgress
import com.arjun.gander.ui.library.LibraryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VaultShelfShell(
    libraryRepository: LibraryRepository,
    externalRevision: Int,
    onOpenExternalFolder: (Uri, String) -> Unit,
    onOpenVault: () -> Unit,
    onOpenVaultSettings: () -> Unit,
    onOpenVaultBackup: () -> Unit,
    onImportBooksToVaultFiles: (List<LibraryBook>) -> Unit,
    onImportBooksToVaultLibrary: (List<LibraryBook>) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    initialDestinationName: String = VaultShelfDestination.HOME.name,
    returnToFiles: Boolean = false,
    onReturnToFiles: () -> Unit = {},
) {
    var selectedName by rememberSaveable {
        mutableStateOf(
            VaultShelfDestination.entries
                .firstOrNull { it.name == initialDestinationName && it != VaultShelfDestination.VAULT }
                ?.name
                ?: VaultShelfDestination.HOME.name,
        )
    }
    val selected = VaultShelfDestination.entries
        .firstOrNull { it.name == selectedName }
        ?: VaultShelfDestination.HOME

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            VaultShelfBottomBar(
                destinations = VaultShelfExternalDestinations,
                selected = selected,
                onSelected = { destination ->
                    when (destination) {
                        VaultShelfDestination.FILES -> {
                            if (returnToFiles) onReturnToFiles()
                            else selectedName = destination.name
                        }
                        VaultShelfDestination.VAULT -> onOpenVault()
                        else -> selectedName = destination.name
                    }
                },
            )
        },
    ) { innerPadding ->
        when (selected) {
            VaultShelfDestination.HOME -> HomeScreen(
                libraryRepository = libraryRepository,
                modifier = Modifier.padding(innerPadding),
                onOpenFiles = {
                    if (returnToFiles) onReturnToFiles()
                    else selectedName = VaultShelfDestination.FILES.name
                },
                onOpenLibrary = { selectedName = VaultShelfDestination.LIBRARY.name },
                onOpenVault = onOpenVault,
            )

            VaultShelfDestination.LIBRARY -> LibraryScreen(
                repository = libraryRepository,
                externalRevision = externalRevision,
                onImportToVaultFiles = onImportBooksToVaultFiles,
                onImportToVaultLibrary = onImportBooksToVaultLibrary,
                modifier = Modifier.padding(innerPadding),
            )

            VaultShelfDestination.FILES -> ExternalFilesScreen(
                onOpenFolder = onOpenExternalFolder,
                modifier = Modifier.padding(innerPadding),
            )

            VaultShelfDestination.VAULT -> Unit

            VaultShelfDestination.SETTINGS -> SettingsScreen(
                onOpenVaultSettings = onOpenVaultSettings,
                onOpenVaultBackup = onOpenVaultBackup,
                onOpenAbout = onOpenAbout,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun ExternalFilesScreen(
    onOpenFolder: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var revision by rememberSaveable { mutableIntStateOf(0) }
    val roots by produceState<List<Pair<Uri, String>>>(
        initialValue = emptyList(),
        revision,
    ) {
        value = withContext(Dispatchers.IO) {
            context.contentResolver.persistedUriPermissions
                .asSequence()
                .filter { it.isReadPermission && isTreeUri(it.uri) }
                .map { permission ->
                    permission.uri to readTreeLabel(context, permission.uri)
                }
                .sortedBy { (_, label) -> label.lowercase() }
                .toList()
        }
    }
    val openTree = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            revision += 1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.vaultshelf_files_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.vaultshelf_files_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (roots.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.vaultshelf_files_empty),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.vaultshelf_files_empty_detail),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            roots.forEach { (uri, label) ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFolder(uri, label) },
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_vaultshelf_files),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.vaultshelf_files_authorized),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.contentResolver.releasePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                    )
                                }.recoverCatching {
                                    context.contentResolver.releasePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                    )
                                }
                                revision += 1
                            },
                        ) {
                            Text(stringResource(R.string.vaultshelf_files_remove_access))
                        }
                    }
                }
            }
        }

        Button(
            onClick = { openTree.launch(null) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.vaultshelf_files_add_folder))
        }
    }
}

private fun isTreeUri(uri: Uri): Boolean =
    runCatching { DocumentsContract.getTreeDocumentId(uri) }.isSuccess &&
        uri.pathSegments.firstOrNull() == "tree"

private fun readTreeLabel(context: Context, uri: Uri): String {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: return context.getString(R.string.vaultshelf_files_folder_fallback)
    return runCatching {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(uri, documentId),
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: documentId.substringAfterLast(':').ifBlank {
            context.getString(R.string.vaultshelf_files_folder_fallback)
        }
}

@Composable
private fun HomeScreen(
    libraryRepository: LibraryRepository,
    onOpenFiles: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recentBooks by remember { mutableStateOf<List<LibraryBook>>(emptyList()) }
    var pendingLegadoBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLegadoBookUrl by rememberSaveable { mutableStateOf<String?>(null) }

    fun refreshRecent() {
        scope.launch {
            recentBooks = libraryRepository.listBooks()
                .asSequence()
                .filter { it.lastOpenedAtEpochMillis > 0L }
                .take(6)
                .toList()
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
                syncLegadoReaderProgress(context, libraryRepository, legadoId, legadoUrl)
                refreshRecent()
            }
            return@rememberLauncherForActivityResult
        }

        val data = result.data
        val bookId = data?.getStringExtra(ViewerActivity.EXTRA_LIBRARY_BOOK_ID)
        val hasProgress = data?.hasExtra(ViewerActivity.EXTRA_LIBRARY_PROGRESS) == true
        if (result.resultCode == android.app.Activity.RESULT_OK && bookId != null && hasProgress) {
            val progress = data?.getFloatExtra(ViewerActivity.EXTRA_LIBRARY_PROGRESS, 0f) ?: 0f
            scope.launch {
                libraryRepository.updateViewerProgress(bookId, progress)
                refreshRecent()
            }
        } else {
            refreshRecent()
        }
    }

    fun openBook(book: LibraryBook) {
        scope.launch {
            runCatching {
                createReaderLaunchPlan(context, libraryRepository, book)
            }.onSuccess { plan ->
                if (plan.legadoBookUrl != null) {
                    pendingLegadoBookId = book.id
                    pendingLegadoBookUrl = plan.legadoBookUrl
                }
                readerLauncher.launch(plan.intent)
            }
        }
    }

    LaunchedEffect(libraryRepository) {
        refreshRecent()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.vaultshelf_app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.vaultshelf_home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            StatusPill()
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.vaultshelf_home_recent),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.vaultshelf_home_open_library),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenLibrary),
                )
            }

            if (recentBooks.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Text(
                        text = stringResource(R.string.vaultshelf_home_recent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(recentBooks, key = { it.id }) { book ->
                        RecentBookCard(
                            book = book,
                            repository = libraryRepository,
                            onOpen = { openBook(book) },
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.vaultshelf_quick_access),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickActionTile(
                    titleRes = R.string.vaultshelf_open_documents,
                    iconRes = R.drawable.ic_vaultshelf_files,
                    onClick = onOpenFiles,
                    modifier = Modifier.weight(1f),
                )
                QuickActionTile(
                    titleRes = R.string.vaultshelf_library_card,
                    iconRes = R.drawable.ic_vaultshelf_library,
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                )
                QuickActionTile(
                    titleRes = R.string.vaultshelf_vault_card,
                    iconRes = R.drawable.ic_vaultshelf_vault,
                    onClick = onOpenVault,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RecentBookCard(
    book: LibraryBook,
    repository: LibraryRepository,
    onOpen: () -> Unit,
) {
    val cover by produceState<ImageBitmap?>(initialValue = null, book.id, book.coverFileName) {
        value = withContext(Dispatchers.IO) {
            repository.coverFile(book.id)
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                ?.asImageBitmap()
        }
    }
    val coverStyle = remember(book.id, book.title, book.format) { BookCoverStyle.from(book) }
    val coverColor = RECENT_COVER_COLORS[coverStyle.paletteIndex % RECENT_COVER_COLORS.size]

    Column(
        modifier = Modifier
            .width(108.dp)
            .clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(154.dp),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 2.dp,
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover!!,
                    contentDescription = stringResource(R.string.vaultshelf_library_cover_description, book.title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(coverColor)
                        .padding(9.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = coverStyle.formatLabel,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = coverStyle.title,
                        color = Color.White,
                        fontSize = if (coverStyle.title.length > 24) 13.sp else 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.vaultshelf_app_name),
                        color = Color.White.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.vaultshelf_reader_progress, book.progressPercent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickActionTile(
    @StringRes titleRes: Int,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    onOpenVaultSettings: () -> Unit,
    onOpenVaultBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.vaultshelf_settings_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
        )

        SettingsEntry(
            titleRes = R.string.vaultshelf_settings_vault,
            detailRes = R.string.vaultshelf_settings_vault_detail,
            iconRes = R.drawable.ic_vaultshelf_vault,
            onClick = onOpenVaultSettings,
        )

        SettingsEntry(
            titleRes = R.string.vaultshelf_settings_backup,
            detailRes = R.string.vaultshelf_settings_backup_detail,
            iconRes = R.drawable.ic_vaultshelf_vault,
            onClick = onOpenVaultBackup,
        )

        SettingsEntry(
            titleRes = R.string.vaultshelf_settings_about,
            detailRes = R.string.vaultshelf_settings_about_detail,
            iconRes = R.drawable.ic_vaultshelf_settings,
            onClick = onOpenAbout,
        )
    }
}

@Composable
private fun SettingsEntry(
    @StringRes titleRes: Int,
    @StringRes detailRes: Int,
    @DrawableRes iconRes: Int,
    onClick: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(detailRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = stringResource(R.string.vaultshelf_offline_badge),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private val RECENT_COVER_COLORS = listOf(
    Color(0xFF315A7D),
    Color(0xFF73536E),
    Color(0xFF49635A),
    Color(0xFF745640),
    Color(0xFF4E5878),
    Color(0xFF6A5E45),
)
