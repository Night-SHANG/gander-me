package com.arjun.gander.vault

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import com.arjun.gander.BookReadingPositions
import com.arjun.gander.Positions
import com.arjun.gander.R
import com.arjun.gander.VaultShelfActivity
import com.arjun.gander.files.ExternalExplorerActivity
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.transfer.TransferBehaviorPreferences
import com.arjun.gander.transfer.TransferRoute
import com.arjun.gander.transfer.TransferSourceDecision
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.vaultshelf.droidfs.SafVolume
import com.vaultshelf.droidfs.VaultShelfProgressStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.LoadingTask
import sushi.hardcore.droidfs.R as DroidFsR
import sushi.hardcore.droidfs.explorers.BaseExplorerActivity
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.file_operations.OperationFile
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.PathUtils

/**
 * Unified destination browser for transfers into an unlocked vault.
 *
 * Sources may be ordinary SAF files, the external VaultShelf library, or the vault library
 * itself. The target is either the visible vault file tree or the vault's private library.
 * Source deletion is always offered only after the target copy has completed successfully.
 */
class VaultImportTargetActivity : BaseExplorerActivity() {

    private val sourceVolumeId: Int by lazy {
        intent.getIntExtra(EXTRA_SOURCE_VOLUME_ID, -1)
    }
    private val sourcePaths: List<String> by lazy {
        intent.getStringArrayListExtra(EXTRA_SOURCE_PATHS).orEmpty()
    }
    private val sourceTypes: List<Int> by lazy {
        intent.getIntegerArrayListExtra(EXTRA_SOURCE_TYPES).orEmpty()
    }
    private val sourceLibraryIds: List<String> by lazy {
        intent.getStringArrayListExtra(EXTRA_SOURCE_LIBRARY_IDS).orEmpty()
    }
    private val sourceVaultLibraryIds: List<String> by lazy {
        intent.getStringArrayListExtra(EXTRA_SOURCE_VAULT_LIBRARY_IDS).orEmpty()
    }
    private val targetLibrary: Boolean by lazy {
        intent.getBooleanExtra(EXTRA_TARGET_LIBRARY, false)
    }
    private val sourceVolumeName: String by lazy {
        intent.getStringExtra(EXTRA_SOURCE_VOLUME_NAME).orEmpty()
    }

    private lateinit var vaultFiles: VaultFileRepository
    private lateinit var vaultLibrary: VaultLibraryStore

    override fun init() {
        super.init()
        findViewById<FloatingActionButton>(DroidFsR.id.fab).setOnClickListener {
            openDialogCreateFolder()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vaultFiles = VaultFileRepository(applicationContext, volumeId)
        vaultLibrary = VaultLibraryStore(applicationContext, vaultFiles)

        val hasVolumeSource =
            sourceVolumeId >= 0 &&
                sourcePaths.isNotEmpty() &&
                sourcePaths.size == sourceTypes.size &&
                app.volumeManager.getVolume(sourceVolumeId) != null
        val hasLibrarySource = sourceLibraryIds.isNotEmpty()
        val hasVaultLibrarySource = sourceVaultLibraryIds.isNotEmpty()

        if (!hasVolumeSource && !hasLibrarySource && !hasVaultLibrarySource) {
            finish()
            return
        }

        if (targetLibrary && savedInstanceState == null) {
            lifecycleScope.launch {
                importDirectlyIntoVaultLibrary()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(DroidFsR.menu.explorer_drop, menu)
        val result = super.onCreateOptionsMenu(menu)
        menu.findItem(DroidFsR.id.validate).isVisible =
            !targetLibrary && explorerAdapter.selectedItems.isEmpty()
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == DroidFsR.id.validate && !targetLibrary) {
            importIntoCurrentDirectory()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private suspend fun importDirectlyIntoVaultLibrary() {
        when {
            sourceLibraryIds.isNotEmpty() -> {
                val repository = LocalLibraryRepository(applicationContext)
                val imported = withContext(Dispatchers.IO) {
                    sourceLibraryIds.mapNotNull { id ->
                        val book = repository.getBook(id) ?: return@mapNotNull null
                        val source = runCatching { repository.bookFile(id) }.getOrNull()
                            ?: return@mapNotNull null
                        runCatching {
                            vaultLibrary.importExternalLibraryBook(book, source)
                            id
                        }.getOrNull()
                    }
                }
                if (imported.isEmpty()) {
                    showTransferFailed()
                } else {
                    promptExternalLibrarySourceChoice(imported)
                }
            }

            sourceVolumeId >= 0 -> {
                val sourceVolume = app.volumeManager.getVolume(sourceVolumeId)
                    ?: return finish()
                val topLevel = sourceOperations()
                val mapped = withContext(Dispatchers.IO) {
                    mapSourceFiles(sourceVolume, topLevel)
                }
                val supported = mapped.filter {
                    !it.isDirectory && VaultLibraryStore.formatForPath(it.srcPath) != null
                }
                val matchedBooks = withContext(Dispatchers.IO) {
                    linkedExternalBooks(sourceVolume, supported)
                }
                val importedCount = withContext(Dispatchers.IO) {
                    supported.count { operation ->
                        val stat = sourceVolume.getAttr(operation.srcPath) ?: return@count false
                        val matched = matchedBooks.firstOrNull { book ->
                            book.format == VaultLibraryStore.formatForPath(operation.srcPath) &&
                                book.sizeBytes == stat.size &&
                                book.contentSha256 == sha256(sourceVolume, operation.srcPath)
                        }
                        val sourceKey = sourceReadingKey(sourceVolume, operation.srcPath, stat.size)
                        runCatching {
                            vaultLibrary.importFromVolume(
                                sourceVolume = sourceVolume,
                                sourcePath = operation.srcPath,
                                titleOverride = matched?.title,
                                sourceReadingKey = sourceKey,
                            )
                        }.isSuccess
                    }
                }
                if (importedCount == 0) {
                    showTransferFailed()
                } else {
                    promptVolumeSourceChoice(topLevel, matchedBooks)
                }
            }

            else -> showTransferFailed()
        }
    }

    private fun importIntoCurrentDirectory() {
        when {
            sourceLibraryIds.isNotEmpty() -> importExternalLibraryIntoCurrentDirectory()
            sourceVaultLibraryIds.isNotEmpty() -> exportVaultLibraryIntoCurrentDirectory()
            else -> importVolumeIntoCurrentDirectory()
        }
    }

    private fun importExternalLibraryIntoCurrentDirectory() {
        lifecycleScope.launch {
            val repository = LocalLibraryRepository(applicationContext)
            val imported = withContext(Dispatchers.IO) {
                sourceLibraryIds.mapNotNull { id ->
                    val book = repository.getBook(id) ?: return@mapNotNull null
                    val source = runCatching { repository.bookFile(id) }.getOrNull()
                        ?: return@mapNotNull null
                    val requestedName =
                        "${VaultFileRepository.sanitizeFileName(book.title).ifBlank { "book" }}." +
                            BookFormat.extension(book.format)
                    runCatching {
                        val destination = vaultFiles.importFile(
                            source,
                            requestedName,
                            currentDirectoryPath,
                        )
                        migrateContentProgressToVault(source, destination)
                        id
                    }.getOrNull()
                }
            }
            refreshCurrentDirectory()
            if (imported.isEmpty()) {
                showTransferFailed()
            } else {
                promptExternalLibrarySourceChoice(imported)
            }
        }
    }

    private fun exportVaultLibraryIntoCurrentDirectory() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                vaultLibrary.listBooks().filter { it.id in sourceVaultLibraryIds }
            }
            val exported = withContext(Dispatchers.IO) {
                entries.mapNotNull { entry ->
                    runCatching {
                        vaultLibrary.exportToVisibleFile(entry, currentDirectoryPath)
                        entry.id
                    }.getOrNull()
                }
            }
            refreshCurrentDirectory()
            if (exported.isEmpty()) {
                showTransferFailed()
            } else {
                when (
                    TransferBehaviorPreferences.automaticDecision(
                        this@VaultImportTargetActivity,
                        TransferRoute.VAULT_LIBRARY_TO_VAULT_FILES,
                    )
                ) {
                    TransferSourceDecision.KEEP -> finishToSource()
                    TransferSourceDecision.DELETE -> lifecycleScope.launch(Dispatchers.IO) {
                        exported.forEach { id -> vaultLibrary.remove(id) }
                        withContext(Dispatchers.Main) { finishToSource() }
                    }
                    null -> MaterialAlertDialogBuilder(this@VaultImportTargetActivity)
                        .setTitle(R.string.vault_transfer_done_title)
                        .setMessage(
                            resources.getQuantityString(
                                R.plurals.vault_transfer_vault_library_to_files_done,
                                exported.size,
                                exported.size,
                            ),
                        )
                        .setPositiveButton(R.string.vault_transfer_delete_source) { _, _ ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                exported.forEach { id -> vaultLibrary.remove(id) }
                                withContext(Dispatchers.Main) { finishToSource() }
                            }
                        }
                        .setNegativeButton(R.string.vault_transfer_keep_source) { _, _ ->
                            finishToSource()
                        }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    private fun importVolumeIntoCurrentDirectory() {
        val sourceVolume = app.volumeManager.getVolume(sourceVolumeId) ?: return
        val topLevel = sourceOperations()

        object : LoadingTask<List<OperationFile>>(this, DroidFsR.string.discovering_files) {
            override suspend fun doTask(): List<OperationFile> =
                mapSourceFiles(sourceVolume, topLevel)
        }.startTask(lifecycleScope) { mapped ->
            checkPathOverwrite(mapped, currentDirectoryPath) { checked ->
                checked ?: return@checkPathOverwrite
                activityScope.launch {
                    val result = fileOperationService.copyElements(
                        volumeId,
                        checked,
                        sourceVolumeId,
                    )
                    onTaskResult(
                        result,
                        DroidFsR.string.copy_failed,
                        onSuccess = {
                            refreshCurrentDirectory()
                            lifecycleScope.launch {
                                val matchedBooks = withContext(Dispatchers.IO) {
                                    linkedExternalBooks(sourceVolume, checked)
                                }
                                withContext(Dispatchers.IO) {
                                    checked
                                        .filterNot { it.isDirectory }
                                        .forEach { operation ->
                                            val destination = operation.dstPath ?: return@forEach
                                            val stat = sourceVolume.getAttr(operation.srcPath)
                                                ?: return@forEach
                                            sourceReadingKey(
                                                sourceVolume,
                                                operation.srcPath,
                                                stat.size,
                                            )?.let { sourceKey ->
                                                migrateReadingPosition(
                                                    sourceKey,
                                                    VaultShelfProgressStore.fileKey(
                                                        vaultFiles.volumeUuid,
                                                        destination,
                                                    ),
                                                )
                                            }
                                        }
                                }
                                promptVolumeSourceChoice(topLevel, matchedBooks)
                            }
                        },
                    )
                }
            }
        }
    }

    private fun sourceOperations(): List<OperationFile> =
        sourcePaths.indices.map { index ->
            OperationFile(sourcePaths[index], sourceTypes[index])
        }

    private fun mapSourceFiles(
        sourceVolume: EncryptedVolume,
        topLevel: List<OperationFile>,
    ): List<OperationFile> {
        val mapped = topLevel.toMutableList()
        topLevel.filter { it.isDirectory }.forEach { directory ->
            sourceVolume.recursiveMapFiles(directory.srcPath)
                ?.forEach { mapped += OperationFile.fromExplorerElement(it) }
        }
        return mapped
    }

    private suspend fun linkedExternalBooks(
        sourceVolume: EncryptedVolume,
        copied: List<OperationFile>,
    ): List<LibraryBook> {
        val localRepository = LocalLibraryRepository(applicationContext)
        val books = localRepository.listBooks()
        if (books.isEmpty()) return emptyList()

        val safVolume = sourceVolume as? SafVolume
        val matched = LinkedHashMap<String, LibraryBook>()
        copied.asSequence()
            .filterNot { it.isDirectory }
            .forEach { operation ->
                val sourceUri = safVolume?.uriForPath(operation.srcPath)?.toString()
                if (sourceUri != null) {
                    books.firstOrNull { it.sourceUri == sourceUri }?.let { exact ->
                        matched[exact.id] = exact
                        return@forEach
                    }
                }

                val format = VaultLibraryStore.formatForPath(operation.srcPath)
                    ?: return@forEach
                val stat = sourceVolume.getAttr(operation.srcPath) ?: return@forEach
                val candidates = books.filter {
                    it.sourceUri == null &&
                        !it.contentSha256.isNullOrBlank() &&
                        it.format == format &&
                        it.sizeBytes == stat.size
                }
                if (candidates.isEmpty()) return@forEach
                val digest = sha256(sourceVolume, operation.srcPath) ?: return@forEach
                candidates.firstOrNull { it.contentSha256 == digest }?.let { legacy ->
                    matched[legacy.id] = legacy
                }
            }
        return matched.values.toList()
    }

    private fun promptVolumeSourceChoice(
        topLevel: List<OperationFile>,
        matchedBooks: List<LibraryBook>,
    ) {
        val route = if (targetLibrary) {
            TransferRoute.EXTERNAL_FILES_TO_VAULT_LIBRARY
        } else {
            TransferRoute.EXTERNAL_FILES_TO_VAULT_FILES
        }
        when (TransferBehaviorPreferences.automaticDecision(this, route)) {
            TransferSourceDecision.KEEP -> finishToSource()
            TransferSourceDecision.DELETE -> deleteVolumeSources(
                topLevel,
                matchedBooks,
                allowLinkedLibraryPrompt = false,
            )
            null -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.vault_transfer_done_title)
                .setMessage(R.string.vault_transfer_delete_file_source_question)
                .setPositiveButton(R.string.vault_transfer_delete_source) { _, _ ->
                    deleteVolumeSources(
                        topLevel,
                        matchedBooks,
                        allowLinkedLibraryPrompt = true,
                    )
                }
                .setNegativeButton(R.string.vault_transfer_keep_source) { _, _ ->
                    finishToSource()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun deleteVolumeSources(
        topLevel: List<OperationFile>,
        matchedBooks: List<LibraryBook>,
        allowLinkedLibraryPrompt: Boolean,
    ) {
        val sourceVolume = app.volumeManager.getVolume(sourceVolumeId) ?: return finishToSource()
        val sourceElements = topLevel.mapNotNull { operation ->
            val stat = sourceVolume.getAttr(operation.srcPath) ?: return@mapNotNull null
            ExplorerElement(
                operation.srcPath.substringAfterLast('/'),
                Stat(stat.type, stat.size, stat.mTime),
                PathUtils.getParentPath(operation.srcPath),
            )
        }

        lifecycleScope.launch {
            val failedItem = fileOperationService.removeElements(sourceVolumeId, sourceElements)
            if (failedItem != null) {
                finishToSource()
                return@launch
            }
            if (matchedBooks.isEmpty()) {
                finishToSource()
            } else if (!allowLinkedLibraryPrompt) {
                withContext(Dispatchers.IO) {
                    val repository = LocalLibraryRepository(applicationContext)
                    matchedBooks.forEach { book ->
                        repository.detachOriginalSource(book.id)
                    }
                }
                finishToSource()
            } else {
                MaterialAlertDialogBuilder(this@VaultImportTargetActivity)
                    .setTitle(R.string.vault_transfer_linked_library_title)
                    .setMessage(
                        resources.getQuantityString(
                            R.plurals.vault_transfer_linked_external_library_message,
                            matchedBooks.size,
                            matchedBooks.size,
                        ),
                    )
                    .setPositiveButton(R.string.vault_transfer_delete_linked_library) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val repository = LocalLibraryRepository(applicationContext)
                            matchedBooks.forEach { book ->
                                runCatching { repository.deleteBook(book.id) }
                            }
                            withContext(Dispatchers.Main) { finishToSource() }
                        }
                    }
                    .setNegativeButton(R.string.vault_transfer_keep_linked_library) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val repository = LocalLibraryRepository(applicationContext)
                            matchedBooks.forEach { book ->
                                repository.detachOriginalSource(book.id)
                            }
                            withContext(Dispatchers.Main) { finishToSource() }
                        }
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun promptExternalLibrarySourceChoice(importedIds: List<String>) {
        val route = if (targetLibrary) {
            TransferRoute.EXTERNAL_LIBRARY_TO_VAULT_LIBRARY
        } else {
            TransferRoute.EXTERNAL_LIBRARY_TO_VAULT_FILES
        }
        when (TransferBehaviorPreferences.automaticDecision(this, route)) {
            TransferSourceDecision.KEEP -> finishToSource()
            TransferSourceDecision.DELETE -> lifecycleScope.launch(Dispatchers.IO) {
                val repository = LocalLibraryRepository(applicationContext)
                importedIds.forEach { id -> runCatching { repository.deleteBook(id) } }
                withContext(Dispatchers.Main) { finishToSource() }
            }
            null -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.vault_transfer_done_title)
                .setMessage(
                    resources.getQuantityString(
                        R.plurals.vault_transfer_external_library_done,
                        importedIds.size,
                        importedIds.size,
                    ),
                )
                .setPositiveButton(R.string.vault_transfer_delete_source) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val repository = LocalLibraryRepository(applicationContext)
                        importedIds.forEach { id -> runCatching { repository.deleteBook(id) } }
                        withContext(Dispatchers.Main) { finishToSource() }
                    }
                }
                .setNegativeButton(R.string.vault_transfer_keep_source) { _, _ ->
                    finishToSource()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun migrateContentProgressToVault(sourceFile: File, destinationPath: String) {
        val sourceKey = Positions.keyFor(sourceFile) ?: return
        migrateReadingPosition(
            sourceKey,
            VaultShelfProgressStore.fileKey(vaultFiles.volumeUuid, destinationPath),
        )
    }

    private fun sourceReadingKey(
        sourceVolume: EncryptedVolume,
        path: String,
        size: Long,
    ): String? {
        val safVolume = sourceVolume as? SafVolume ?: return null
        val uri = safVolume.uriForPath(path) ?: return null
        return Positions.keyFor(contentResolver, uri, size)
    }

    private fun migrateReadingPosition(sourceKey: String, destinationKey: String) {
        BookReadingPositions.get(applicationContext, sourceKey)?.let { position ->
            BookReadingPositions.save(
                applicationContext,
                destinationKey,
                position.chapterIndex,
                position.chapterPosition,
                position.updatedAtEpochMillis,
            )
        }
    }

    private fun finishToSource() {
        when {
            sourceLibraryIds.isNotEmpty() -> {
                startActivity(
                    Intent(this, VaultShelfActivity::class.java)
                        .putExtra(VaultShelfActivity.EXTRA_INITIAL_DESTINATION, "LIBRARY")
                        .addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                )
                overridePendingTransition(0, 0)
            }
            sourceVolumeId >= 0 -> {
                startActivity(
                    Intent(this, ExternalExplorerActivity::class.java)
                        .putExtra("volumeId", sourceVolumeId)
                        .putExtra("volumeName", sourceVolumeName)
                        .putExtra(VaultShelfActivity.EXTRA_PLAIN_VOLUME, true)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                )
                overridePendingTransition(0, 0)
            }
        }
        finish()
    }

    private fun showTransferFailed() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error)
            .setMessage(R.string.vault_transfer_failed)
            .setPositiveButton(R.string.ok) { _, _ -> finishToSource() }
            .show()
    }

    private fun sha256(volume: EncryptedVolume, path: String): String? {
        val handle = volume.openFileReadMode(path)
        if (handle == -1L) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var offset = 0L
            while (true) {
                val read = volume.read(
                    handle,
                    offset,
                    buffer,
                    0,
                    buffer.size.toLong(),
                )
                if (read <= 0) break
                digest.update(buffer, 0, read)
                offset += read
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            volume.closeFile(handle)
        }
    }

    companion object {
        const val ACTION_IMPORT_TO_VAULT = "vaultshelf_import_to_vault"
        const val ACTION_IMPORT_TO_VAULT_LIBRARY = "vaultshelf_import_to_vault_library"
        const val EXTRA_TARGET_LIBRARY = "vaultshelf.target_library"
        const val EXTRA_SOURCE_VOLUME_ID = "vaultshelf.source_volume_id"
        const val EXTRA_SOURCE_PATHS = "vaultshelf.source_paths"
        const val EXTRA_SOURCE_TYPES = "vaultshelf.source_types"
        const val EXTRA_SOURCE_LIBRARY_IDS = "vaultshelf.source_library_ids"
        const val EXTRA_SOURCE_VAULT_LIBRARY_IDS = "vaultshelf.source_vault_library_ids"
        const val EXTRA_SOURCE_VOLUME_NAME = "vaultshelf.source_volume_name"
    }
}
