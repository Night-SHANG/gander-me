package com.arjun.gander.vault

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import com.arjun.gander.BookReadingPositions
import com.arjun.gander.R
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LocalLibraryRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.vaultshelf.droidfs.VaultShelfProgressStore
import com.vaultshelf.legado.LegadoReaderBridge
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
 * Destination browser for "Files -> Import to vault".
 *
 * Source browsing stays inside the same DroidFS Explorer engine through SafVolume; this
 * screen only chooses the encrypted destination and asks FileOperationService to perform
 * the cross-volume copy. No second file-copy implementation is maintained in VaultShelf.
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
        if (
            sourceVolumeId < 0 ||
            sourcePaths.isEmpty() ||
            sourcePaths.size != sourceTypes.size ||
            app.volumeManager.getVolume(sourceVolumeId) == null
        ) {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(DroidFsR.menu.explorer_drop, menu)
        val result = super.onCreateOptionsMenu(menu)
        menu.findItem(DroidFsR.id.validate).isVisible = explorerAdapter.selectedItems.isEmpty()
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == DroidFsR.id.validate) {
            importIntoCurrentDirectory()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun importIntoCurrentDirectory() {
        val sourceVolume = app.volumeManager.getVolume(sourceVolumeId) ?: return
        val topLevel = sourcePaths.indices.map { index ->
            OperationFile(sourcePaths[index], sourceTypes[index])
        }

        object : LoadingTask<List<OperationFile>>(this, DroidFsR.string.discovering_files) {
            override suspend fun doTask(): List<OperationFile> {
                val mapped = topLevel.toMutableList()
                topLevel.filter { it.isDirectory }.forEach { directory ->
                    sourceVolume.recursiveMapFiles(directory.srcPath)
                        ?.forEach { mapped += OperationFile.fromExplorerElement(it) }
                }
                return mapped
            }
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
                            migrateLibraryIdentityAndPromptDelete(
                                sourceVolume,
                                checked,
                                topLevel,
                            )
                        },
                    )
                }
            }
        }
    }

    private fun migrateLibraryIdentityAndPromptDelete(
        sourceVolume: EncryptedVolume,
        copied: List<OperationFile>,
        topLevel: List<OperationFile>,
    ) {
        lifecycleScope.launch {
            val matchedBooks = withContext(Dispatchers.IO) {
                migrateLibraryIdentity(sourceVolume, copied)
            }
            val message = if (matchedBooks.isEmpty()) {
                getString(R.string.vault_file_import_done_message)
            } else {
                resources.getQuantityString(
                    R.plurals.vault_file_import_done_with_library,
                    matchedBooks.size,
                    matchedBooks.size,
                )
            }

            MaterialAlertDialogBuilder(this@VaultImportTargetActivity)
                .setTitle(R.string.vault_file_import_done_title)
                .setMessage(message)
                .setPositiveButton(R.string.vault_external_import_delete_source) { _, _ ->
                    deleteSourcesAndExternalLibrary(topLevel, matchedBooks)
                }
                .setNegativeButton(R.string.vault_external_import_keep_source) { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }
    }

    private suspend fun migrateLibraryIdentity(
        sourceVolume: EncryptedVolume,
        copied: List<OperationFile>,
    ): List<LibraryBook> {
        val localRepository = LocalLibraryRepository(applicationContext)
        val books = localRepository.listBooks()
            .filter { !it.contentSha256.isNullOrBlank() }
        if (books.isEmpty()) return emptyList()

        val matched = LinkedHashMap<String, LibraryBook>()
        copied.asSequence()
            .filterNot { it.isDirectory }
            .forEach { operation ->
                val destination = operation.dstPath ?: return@forEach
                val format = VaultLibraryStore.formatForPath(destination) ?: return@forEach
                val stat = sourceVolume.getAttr(operation.srcPath) ?: return@forEach
                val candidates = books.filter {
                    it.format == format && it.sizeBytes == stat.size
                }
                if (candidates.isEmpty()) return@forEach

                val digest = sha256(sourceVolume, operation.srcPath) ?: return@forEach
                val book = candidates.firstOrNull { it.contentSha256 == digest }
                    ?: return@forEach

                val vaultBook = runCatching {
                    vaultLibrary.addPath(destination, book.title)
                }.getOrNull() ?: return@forEach

                book.legadoBookUrl?.let { bookUrl ->
                    LegadoReaderBridge.snapshot(applicationContext, bookUrl)?.let { snapshot ->
                        BookReadingPositions.save(
                            applicationContext,
                            VaultShelfProgressStore.fileKey(
                                vaultFiles.volumeUuid,
                                vaultBook.path,
                            ),
                            snapshot.chapterIndex,
                            snapshot.chapterPosition,
                        )
                    }
                }
                matched[book.id] = book
            }
        return matched.values.toList()
    }

    private fun deleteSourcesAndExternalLibrary(
        topLevel: List<OperationFile>,
        matchedBooks: List<LibraryBook>,
    ) {
        val sourceVolume = app.volumeManager.getVolume(sourceVolumeId) ?: run {
            finish()
            return
        }
        val sourceElements = topLevel.mapNotNull { operation ->
            val stat = sourceVolume.getAttr(operation.srcPath) ?: return@mapNotNull null
            ExplorerElement(
                operation.srcPath.substringAfterLast('/'),
                Stat(stat.type, stat.size, stat.mTime),
                PathUtils.getParentPath(operation.srcPath),
            )
        }

        lifecycleScope.launch {
            val failedItem = fileOperationService.removeElements(
                sourceVolumeId,
                sourceElements,
            )
            if (failedItem == null) {
                withContext(Dispatchers.IO) {
                    val repository = LocalLibraryRepository(applicationContext)
                    matchedBooks.forEach { book ->
                        runCatching { repository.deleteBook(book.id) }
                    }
                }
            }
            finish()
        }
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
        const val EXTRA_SOURCE_VOLUME_ID = "vaultshelf.source_volume_id"
        const val EXTRA_SOURCE_PATHS = "vaultshelf.source_paths"
        const val EXTRA_SOURCE_TYPES = "vaultshelf.source_types"
    }
}
