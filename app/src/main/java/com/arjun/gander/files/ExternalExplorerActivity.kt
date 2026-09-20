package com.arjun.gander.files

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.arjun.gander.R
import com.arjun.gander.VaultShelfActivity
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LibraryBook
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.vault.VaultImportTargetActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vaultshelf.droidfs.SafVolume
import java.security.MessageDigest
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.MainActivity as DroidFsMainActivity
import sushi.hardcore.droidfs.R as DroidFsR
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.explorers.ExplorerElement

/**
 * Plain SAF variant of the mature DroidFS Explorer.
 *
 * The visible file tree remains DroidFS. VaultShelf adds cross-zone transfer actions and
 * its persistent product navigation without replacing copy/move/sort/thumbnail behavior.
 */
class ExternalExplorerActivity : ExplorerActivity() {

    private var bottomNavigation: BottomNavigationView? = null

    private var volumeClosed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra("volumeName").orEmpty()
        configureBottomNavigation()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)

        menu.findItem(DroidFsR.id.lock)?.isVisible = false
        menu.findItem(DroidFsR.id.close)?.isVisible = false
        menu.findItem(DroidFsR.id.decrypt)?.isVisible = false
        menu.findItem(DroidFsR.id.unsafe_features)?.isVisible = false

        val selected = selectedElements()
        val anySelected = selected.isNotEmpty()
        val allBooks = anySelected && selected.all {
            !it.isDirectory && BookFormat.fromFileName(it.name) != null
        }

        menuAction(
            menu,
            R.id.action_add_to_library,
            R.string.vault_transfer_to_external_library,
            allBooks,
        )
        menuAction(
            menu,
            R.id.action_import_to_vault,
            R.string.vault_transfer_to_vault_files,
            anySelected,
        )
        menuAction(
            menu,
            R.id.action_import_to_vault_library,
            R.string.vault_transfer_to_vault_library,
            allBooks,
        )
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_to_library -> {
                importSelectedIntoExternalLibrary()
                true
            }
            R.id.action_import_to_vault -> {
                startVaultTransfer(targetLibrary = false)
                true
            }
            R.id.action_import_to_vault_library -> {
                startVaultTransfer(targetLibrary = true)
                true
            }
            DroidFsR.id.delete -> {
                confirmDeleteSelected()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun menuAction(
        menu: Menu,
        id: Int,
        titleRes: Int,
        visible: Boolean,
    ): MenuItem {
        val item = menu.findItem(id) ?: menu.add(Menu.NONE, id, Menu.NONE, titleRes).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        item.isVisible = visible
        return item
    }

    private fun selectedElements(): List<ExplorerElement> =
        explorerAdapter.selectedItems
            .sorted()
            .mapNotNull { index -> explorerElements.getOrNull(index) }
            .filterNot { it.isParentFolder }

    private fun importSelectedIntoExternalLibrary() {
        val selected = selectedElements().filter {
            !it.isDirectory && BookFormat.fromFileName(it.name) != null
        }
        val saf = encryptedVolume as? SafVolume ?: return
        if (selected.isEmpty()) return

        lifecycleScope.launch {
            val repository = LocalLibraryRepository(applicationContext)
            val imported = withContext(Dispatchers.IO) {
                selected.mapNotNull { element ->
                    val uri = saf.uriForPath(element.fullPath) ?: return@mapNotNull null
                    runCatching { repository.importBook(uri) }.getOrNull()
                }
            }
            if (imported.isEmpty()) {
                showTransferFailed()
                return@launch
            }
            MaterialAlertDialogBuilder(this@ExternalExplorerActivity)
                .setTitle(R.string.vault_transfer_done_title)
                .setMessage(
                    resources.getQuantityString(
                        R.plurals.vault_transfer_external_files_to_library_done,
                        imported.size,
                        imported.size,
                    ),
                )
                .setPositiveButton(R.string.vault_transfer_delete_source) { _, _ ->
                    lifecycleScope.launch {
                        if (deleteElements(selected)) {
                            withContext(Dispatchers.IO) {
                                imported.forEach { book ->
                                    repository.detachOriginalSource(book.id)
                                }
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.vault_transfer_keep_source) { _, _ ->
                    unselectAll()
                    invalidateOptionsMenu()
                }
                .show()
        }
    }

    private fun startVaultTransfer(targetLibrary: Boolean) {
        val selected = selectedElements()
        if (selected.isEmpty()) return

        startActivity(
            Intent(this, DroidFsMainActivity::class.java)
                .setAction(
                    if (targetLibrary) {
                        VaultImportTargetActivity.ACTION_IMPORT_TO_VAULT_LIBRARY
                    } else {
                        VaultImportTargetActivity.ACTION_IMPORT_TO_VAULT
                    },
                )
                .putExtra(
                    VaultImportTargetActivity.EXTRA_SOURCE_VOLUME_ID,
                    volumeId,
                )
                .putStringArrayListExtra(
                    VaultImportTargetActivity.EXTRA_SOURCE_PATHS,
                    ArrayList(selected.map { it.fullPath }),
                )
                .putIntegerArrayListExtra(
                    VaultImportTargetActivity.EXTRA_SOURCE_TYPES,
                    ArrayList(selected.map { it.stat.type }),
                )
                .putExtra(
                    VaultImportTargetActivity.EXTRA_TARGET_LIBRARY,
                    targetLibrary,
                ),
        )
        unselectAll()
    }

    private fun confirmDeleteSelected() {
        val selected = selectedElements()
        if (selected.isEmpty()) return
        lifecycleScope.launch {
            val linked = withContext(Dispatchers.IO) { linkedLibraryBooks(selected) }
            val builder = MaterialAlertDialogBuilder(this@ExternalExplorerActivity)
                .setTitle(R.string.vault_transfer_delete_files_title)
                .setMessage(
                    if (linked.isEmpty()) {
                        resources.getQuantityString(
                            R.plurals.vault_transfer_delete_files_message,
                            selected.size,
                            selected.size,
                        )
                    } else {
                        resources.getQuantityString(
                            R.plurals.vault_transfer_delete_files_linked_library_message,
                            linked.size,
                            linked.size,
                        )
                    },
                )
                .setNegativeButton(android.R.string.cancel, null)

            if (linked.isNotEmpty()) {
                builder
                    .setPositiveButton(R.string.vault_transfer_delete_file_and_library) { _, _ ->
                        lifecycleScope.launch {
                            if (deleteElements(selected)) {
                                withContext(Dispatchers.IO) {
                                    val repository = LocalLibraryRepository(applicationContext)
                                    linked.forEach { book ->
                                        runCatching { repository.deleteBook(book.id) }
                                    }
                                }
                            }
                        }
                    }
                    .setNeutralButton(R.string.vault_transfer_delete_file_only) { _, _ ->
                        lifecycleScope.launch {
                            if (deleteElements(selected)) {
                                withContext(Dispatchers.IO) {
                                    val repository = LocalLibraryRepository(applicationContext)
                                    linked.forEach { book ->
                                        repository.detachOriginalSource(book.id)
                                    }
                                }
                            }
                        }
                    }
            } else {
                builder.setPositiveButton(R.string.vault_transfer_delete_source) { _, _ ->
                    lifecycleScope.launch { deleteElements(selected) }
                }
            }
            builder.show()
        }
    }

    private suspend fun linkedLibraryBooks(
        selected: List<ExplorerElement>,
    ): List<LibraryBook> {
        val repository = LocalLibraryRepository(applicationContext)
        val books = repository.listBooks()
        val saf = encryptedVolume as? SafVolume
        val matched = LinkedHashMap<String, LibraryBook>()
        val files = buildList {
            selected.forEach { element ->
                if (element.isDirectory) {
                    encryptedVolume.recursiveMapFiles(element.fullPath)
                        ?.filterNot { it.isDirectory }
                        ?.let(::addAll)
                } else {
                    add(element)
                }
            }
        }
        files.forEach { element ->
            val sourceUri = saf?.uriForPath(element.fullPath)?.toString()
            if (sourceUri != null) {
                books.firstOrNull { it.sourceUri == sourceUri }?.let { exact ->
                    matched[exact.id] = exact
                    return@forEach
                }
            }

            val format = BookFormat.fromFileName(element.name) ?: return@forEach
            val digest = sha256(element.fullPath) ?: return@forEach
            repository.findByContentFingerprint(format, element.stat.size, digest)
                ?.takeIf { it.sourceUri == null }
                ?.let { legacy ->
                    matched[legacy.id] = legacy
                }
        }
        return matched.values.toList()
    }

    private fun sha256(path: String): String? {
        val handle = encryptedVolume.openFileReadMode(path)
        if (handle == -1L) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var offset = 0L
            while (true) {
                val read = encryptedVolume.read(
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
            encryptedVolume.closeFile(handle)
        }
    }

    private suspend fun deleteElements(elements: List<ExplorerElement>): Boolean {
        val failed = fileOperationService.removeElements(volumeId, elements)
        refreshCurrentDirectory()
        unselectAll()
        return failed == null
    }

    private fun showTransferFailed() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error)
            .setMessage(R.string.vault_transfer_failed)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun configureBottomNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.vaultshelf_explorer_bottom_nav)
        bottomNavigation = nav
        nav.isVisible = true
        nav.selectedItemId = R.id.vaultshelf_nav_files_item
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.vaultshelf_nav_files_item -> true
                R.id.vaultshelf_nav_home_item -> {
                    openShell("HOME")
                    false
                }
                R.id.vaultshelf_nav_library_item -> {
                    openShell("LIBRARY")
                    false
                }
                R.id.vaultshelf_nav_vault_item -> {
                    startActivity(
                        Intent(this, DroidFsMainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
                    )
                    overridePendingTransition(0, 0)
                    false
                }
                R.id.vaultshelf_nav_settings_item -> {
                    openShell("SETTINGS")
                    false
                }
                else -> false
            }
        }
    }

    private fun openShell(destination: String) {
        startActivity(
            Intent(this, VaultShelfActivity::class.java)
                .putExtra(VaultShelfActivity.EXTRA_INITIAL_DESTINATION, destination)
                .putExtra(VaultShelfActivity.EXTRA_RETURN_TO_FILES, true)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        bottomNavigation?.menu
            ?.findItem(R.id.vaultshelf_nav_files_item)
            ?.isChecked = true
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) closeTransientVolume()
        super.onDestroy()
    }

    private fun closeTransientVolume() {
        if (volumeClosed || volumeId < 0) return
        volumeClosed = true
        if (app.volumeManager.getVolume(volumeId) != null) {
            app.volumeManager.closeVolume(volumeId)
        }
    }
}
