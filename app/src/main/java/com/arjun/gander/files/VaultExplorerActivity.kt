package com.arjun.gander.files

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.arjun.gander.BookReadingPositions
import com.arjun.gander.Positions
import com.arjun.gander.R
import com.arjun.gander.library.BookFormat
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.vault.EncryptedVolumeInputStream
import com.arjun.gander.vault.VaultImportTargetActivity
import com.arjun.gander.vault.VaultLibraryEntry
import com.arjun.gander.vault.VaultLibraryStore
import com.arjun.gander.vault.VaultModeActivity
import com.arjun.gander.vault.VaultFileRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vaultshelf.droidfs.VaultShelfProgressStore
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.R as DroidFsR
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.file_operations.TaskResult

/**
 * Vault-file variant of DroidFS Explorer with VaultShelf navigation and four-zone transfers.
 */
class VaultExplorerActivity : ExplorerActivity() {

    private lateinit var vaultFiles: VaultFileRepository
    private lateinit var vaultLibrary: VaultLibraryStore
    private var pendingExport: List<ExplorerElement> = emptyList()

    private val exportDirectory =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val selected = pendingExport
            pendingExport = emptyList()
            if (uri != null && selected.isNotEmpty()) {
                exportSelectedToExternal(uri, selected)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vaultFiles = VaultFileRepository(applicationContext, volumeId)
        vaultLibrary = VaultLibraryStore(applicationContext, vaultFiles)
        configureBottomNavigation()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        menu.findItem(DroidFsR.id.decrypt)?.isVisible = false
        menu.findItem(DroidFsR.id.unsafe_features)?.isVisible = false

        val selected = selectedElements()
        val anySelected = selected.isNotEmpty()
        val allBooks = anySelected && selected.all {
            !it.isDirectory && BookFormat.fromFileName(it.name) != null
        }

        menuAction(
            menu,
            R.id.action_export_external,
            R.string.vault_transfer_to_external_files,
            anySelected,
        )
        menuAction(
            menu,
            R.id.action_add_to_external_library,
            R.string.vault_transfer_to_external_library,
            allBooks,
        )
        menuAction(
            menu,
            R.id.action_add_to_vault_library,
            R.string.vault_transfer_to_vault_library,
            allBooks,
        )
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_external -> {
                pendingExport = selectedElements()
                if (pendingExport.isNotEmpty()) exportDirectory.launch(null)
                true
            }
            R.id.action_add_to_external_library -> {
                importSelectedIntoExternalLibrary()
                true
            }
            R.id.action_add_to_vault_library -> {
                importSelectedIntoVaultLibrary()
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

    private fun exportSelectedToExternal(uri: Uri, selected: List<ExplorerElement>) {
        lifecycleScope.launch {
            val result = fileOperationService.exportFiles(volumeId, selected, uri)
            if (result.state != TaskResult.State.SUCCESS) {
                result.showErrorAlertDialog(this@VaultExplorerActivity)
                return@launch
            }
            MaterialAlertDialogBuilder(this@VaultExplorerActivity)
                .setTitle(R.string.vault_transfer_done_title)
                .setMessage(R.string.vault_transfer_delete_file_source_question)
                .setPositiveButton(R.string.vault_transfer_delete_source) { _, _ ->
                    promptDeleteVaultFileSources(
                        selected,
                        allowLinkedLibraryCleanup = true,
                    )
                }
                .setNegativeButton(R.string.vault_transfer_keep_source) { _, _ ->
                    unselectAll()
                }
                .show()
        }
    }

    private fun importSelectedIntoExternalLibrary() {
        val selected = selectedElements().filter {
            !it.isDirectory && BookFormat.fromFileName(it.name) != null
        }
        if (selected.isEmpty()) return

        lifecycleScope.launch {
            val repository = LocalLibraryRepository(applicationContext)
            val imported = withContext(Dispatchers.IO) {
                selected.mapNotNull { element ->
                    val format = BookFormat.fromFileName(element.name) ?: return@mapNotNull null
                    val book = runCatching {
                        repository.importStream(
                            displayName = element.name,
                            format = format,
                            inputStreamProvider = {
                                EncryptedVolumeInputStream(encryptedVolume, element.fullPath)
                            },
                        )
                    }.getOrNull() ?: return@mapNotNull null
                    val targetFile = runCatching { repository.bookFile(book.id) }.getOrNull()
                    if (targetFile != null) {
                        Positions.keyFor(targetFile)?.let { targetKey ->
                            copyReadingPosition(
                                VaultShelfProgressStore.fileKey(
                                    vaultFiles.volumeUuid,
                                    element.fullPath,
                                ),
                                targetKey,
                            )
                        }
                    }
                    element
                }
            }
            if (imported.isEmpty()) {
                showTransferFailed()
            } else {
                MaterialAlertDialogBuilder(this@VaultExplorerActivity)
                    .setTitle(R.string.vault_transfer_done_title)
                    .setMessage(
                        resources.getQuantityString(
                            R.plurals.vault_transfer_vault_files_to_external_library_done,
                            imported.size,
                            imported.size,
                        ),
                    )
                    .setPositiveButton(R.string.vault_transfer_delete_source) { _, _ ->
                        promptDeleteVaultFileSources(
                            imported,
                            allowLinkedLibraryCleanup = true,
                        )
                    }
                    .setNegativeButton(R.string.vault_transfer_keep_source) { _, _ ->
                        unselectAll()
                    }
                    .show()
            }
        }
    }

    private fun importSelectedIntoVaultLibrary() {
        val selected = selectedElements().filter {
            !it.isDirectory && BookFormat.fromFileName(it.name) != null
        }
        if (selected.isEmpty()) return

        lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                selected.mapNotNull { element ->
                    runCatching {
                        vaultLibrary.addPath(element.fullPath)
                        element
                    }.getOrNull()
                }
            }
            if (imported.isEmpty()) {
                showTransferFailed()
            } else {
                MaterialAlertDialogBuilder(this@VaultExplorerActivity)
                    .setTitle(R.string.vault_transfer_done_title)
                    .setMessage(
                        resources.getQuantityString(
                            R.plurals.vault_transfer_vault_files_to_vault_library_done,
                            imported.size,
                            imported.size,
                        ),
                    )
                    .setPositiveButton(R.string.vault_transfer_delete_source) { _, _ ->
                        lifecycleScope.launch { deleteElements(imported) }
                    }
                    .setNegativeButton(R.string.vault_transfer_keep_source) { _, _ ->
                        unselectAll()
                    }
                    .show()
            }
        }
    }

    private fun confirmDeleteSelected() {
        val selected = selectedElements()
        if (selected.isEmpty()) return
        lifecycleScope.launch {
            val linked = withContext(Dispatchers.IO) { linkedVaultLibrary(selected) }
            val builder = MaterialAlertDialogBuilder(this@VaultExplorerActivity)
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
                            R.plurals.vault_transfer_delete_files_linked_vault_library_message,
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
                                    linked.forEach { entry -> vaultLibrary.remove(entry.id) }
                                }
                            }
                        }
                    }
                    .setNeutralButton(R.string.vault_transfer_delete_file_only) { _, _ ->
                        lifecycleScope.launch { deleteElements(selected) }
                    }
            } else {
                builder.setPositiveButton(R.string.vault_transfer_delete_source) { _, _ ->
                    lifecycleScope.launch { deleteElements(selected) }
                }
            }
            builder.show()
        }
    }

    private fun promptDeleteVaultFileSources(
        selected: List<ExplorerElement>,
        allowLinkedLibraryCleanup: Boolean,
    ) {
        lifecycleScope.launch {
            val linked = if (allowLinkedLibraryCleanup) {
                withContext(Dispatchers.IO) { linkedVaultLibrary(selected) }
            } else {
                emptyList()
            }
            if (linked.isEmpty()) {
                deleteElements(selected)
                return@launch
            }
            MaterialAlertDialogBuilder(this@VaultExplorerActivity)
                .setTitle(R.string.vault_transfer_linked_library_title)
                .setMessage(
                    resources.getQuantityString(
                        R.plurals.vault_transfer_linked_vault_library_message,
                        linked.size,
                        linked.size,
                    ),
                )
                .setPositiveButton(R.string.vault_transfer_delete_file_and_library) { _, _ ->
                    lifecycleScope.launch {
                        if (deleteElements(selected)) {
                            withContext(Dispatchers.IO) {
                                linked.forEach { entry -> vaultLibrary.remove(entry.id) }
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.vault_transfer_delete_file_only) { _, _ ->
                    lifecycleScope.launch { deleteElements(selected) }
                }
                .show()
        }
    }

    private fun linkedVaultLibrary(
        selected: List<ExplorerElement>,
    ): List<VaultLibraryEntry> {
        val linked = LinkedHashMap<String, VaultLibraryEntry>()
        selected.forEach { element ->
            vaultLibrary.linkedToSourcePath(element.fullPath).forEach { entry ->
                linked[entry.id] = entry
            }
        }
        return linked.values.toList()
    }

    private fun copyReadingPosition(sourceKey: String, destinationKey: String) {
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
        nav.isVisible = true
        nav.menu.findItem(R.id.vaultshelf_nav_vault_item)?.isVisible = false
        nav.selectedItemId = R.id.vaultshelf_nav_files_item
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.vaultshelf_nav_home_item -> openVaultShell("HOME")
                R.id.vaultshelf_nav_library_item -> openVaultShell("LIBRARY")
                R.id.vaultshelf_nav_files_item -> Unit
                R.id.vaultshelf_nav_settings_item -> openVaultShell("SETTINGS")
                else -> return@setOnItemSelectedListener false
            }
            true
        }
    }

    private fun openVaultShell(destination: String) {
        startActivity(
            Intent(this, VaultModeActivity::class.java)
                .putExtra(VaultModeActivity.EXTRA_VOLUME_ID, volumeId)
                .putExtra(
                    VaultModeActivity.EXTRA_VOLUME_NAME,
                    intent.getStringExtra("volumeName").orEmpty(),
                )
                .putExtra(VaultModeActivity.EXTRA_INITIAL_DESTINATION, destination)
                .putExtra(VaultModeActivity.EXTRA_RETURN_TO_FILES, true)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        overridePendingTransition(0, 0)
    }
}
