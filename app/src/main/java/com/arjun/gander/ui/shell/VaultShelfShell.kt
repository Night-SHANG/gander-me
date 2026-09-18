package com.arjun.gander.ui.shell

import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arjun.gander.EpubReaderActivity
import com.arjun.gander.MobiReaderActivity
import com.arjun.gander.R
import com.arjun.gander.TxtReaderActivity
import com.arjun.gander.UmdReaderActivity
import com.arjun.gander.ViewerActivity
import com.arjun.gander.library.BookCoverStyle
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LibraryRepository
import com.arjun.gander.ui.library.LibraryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VaultShelfDestination(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    HOME(R.string.vaultshelf_nav_home, R.drawable.ic_vaultshelf_home),
    LIBRARY(R.string.vaultshelf_nav_library, R.drawable.ic_vaultshelf_library),
    FILES(R.string.vaultshelf_nav_files, R.drawable.ic_vaultshelf_files),
    VAULT(R.string.vaultshelf_nav_vault, R.drawable.ic_vaultshelf_vault),
    SETTINGS(R.string.vaultshelf_nav_settings, R.drawable.ic_vaultshelf_settings),
}

@Composable
fun VaultShelfShell(
    libraryRepository: LibraryRepository,
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedName by rememberSaveable { mutableStateOf(VaultShelfDestination.HOME.name) }
    val selected = VaultShelfDestination.entries
        .firstOrNull { it.name == selectedName }
        ?.takeUnless { it == VaultShelfDestination.FILES }
        ?: VaultShelfDestination.HOME

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FluentBottomBar(
                selected = selected,
                onSelected = { destination ->
                    if (destination == VaultShelfDestination.FILES) {
                        onOpenFiles()
                    } else {
                        selectedName = destination.name
                    }
                },
            )
        },
    ) { innerPadding ->
        when (selected) {
            VaultShelfDestination.HOME -> HomeScreen(
                libraryRepository = libraryRepository,
                modifier = Modifier.padding(innerPadding),
                onOpenFiles = onOpenFiles,
                onOpenLibrary = { selectedName = VaultShelfDestination.LIBRARY.name },
                onOpenVault = { selectedName = VaultShelfDestination.VAULT.name },
            )

            VaultShelfDestination.LIBRARY -> LibraryScreen(
                repository = libraryRepository,
                modifier = Modifier.padding(innerPadding),
            )

            VaultShelfDestination.FILES -> FilesScreen(
                onOpenFiles = onOpenFiles,
                modifier = Modifier.padding(innerPadding),
            )

            VaultShelfDestination.VAULT -> FoundationScreen(
                titleRes = R.string.vaultshelf_vault_title,
                detailRes = R.string.vaultshelf_vault_placeholder,
                modifier = Modifier.padding(innerPadding),
            )

            VaultShelfDestination.SETTINGS -> FoundationScreen(
                titleRes = R.string.vaultshelf_settings_title,
                detailRes = R.string.vaultshelf_settings_placeholder,
                modifier = Modifier.padding(innerPadding),
            )
        }
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
            val intent = when (book.format) {
                BookFormat.TXT -> Intent(context, TxtReaderActivity::class.java)
                    .putExtra(TxtReaderActivity.EXTRA_BOOK_ID, book.id)

                BookFormat.EPUB -> Intent(context, EpubReaderActivity::class.java)
                    .putExtra(EpubReaderActivity.EXTRA_BOOK_ID, book.id)

                BookFormat.MARKDOWN, BookFormat.PDF -> {
                    libraryRepository.updateProgress(book.id, book.readingOffset)
                    Intent(context, ViewerActivity::class.java)
                        .putExtra(
                            ViewerActivity.EXTRA_PATH,
                            libraryRepository.bookFile(book.id).absolutePath,
                        )
                        .putExtra(ViewerActivity.EXTRA_LIBRARY_BOOK_ID, book.id)
                }

                BookFormat.UMD -> Intent(context, UmdReaderActivity::class.java)
                    .putExtra(UmdReaderActivity.EXTRA_BOOK_ID, book.id)

                BookFormat.MOBI, BookFormat.AZW3, BookFormat.AZW ->
                    Intent(context, MobiReaderActivity::class.java)
                        .putExtra(MobiReaderActivity.EXTRA_BOOK_ID, book.id)
            }
            readerLauncher.launch(intent)
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
private fun FilesScreen(
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.vaultshelf_files_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.vaultshelf_files_detail),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onOpenFiles,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(stringResource(R.string.vaultshelf_open_file_browser))
        }
    }
}

@Composable
private fun FoundationScreen(
    @StringRes titleRes: Int,
    @StringRes detailRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.vaultshelf_foundation_status),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(detailRes),
                    style = MaterialTheme.typography.bodyLarge,
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

@Composable
private fun FluentBottomBar(
    selected: VaultShelfDestination,
    onSelected: (VaultShelfDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            VaultShelfDestination.entries.forEach { destination ->
                val isSelected = selected == destination
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else Color.Transparent,
                        )
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelected(destination) },
                            role = Role.Tab,
                        )
                        .padding(horizontal = 3.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        maxLines = 1,
                    )
                }
            }
        }
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
