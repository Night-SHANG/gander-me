package com.arjun.gander.vault

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.arjun.gander.BookReadingPositions
import com.arjun.gander.R
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.ui.theme.VaultShelfTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vaultshelf.droidfs.VaultShelfFileRouter
import com.vaultshelf.droidfs.VaultShelfProgressStore
import com.vaultshelf.legado.LegadoReaderBridge
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.SettingsActivity as DroidFsSettingsActivity
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.util.finishOnClose

class VaultModeActivity : AppCompatActivity() {

    private var libraryRevision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val volumeId = intent.getIntExtra(EXTRA_VOLUME_ID, -1)
        val volumeName = intent.getStringExtra(EXTRA_VOLUME_NAME).orEmpty()
        val volumeManager = (application as VolumeManagerApp).volumeManager
        val volume = volumeManager.getVolume(volumeId)
        if (volumeId < 0 || volume == null) {
            finish()
            return
        }
        finishOnClose(volume)

        val fileRepository = VaultFileRepository(applicationContext, volumeId)
        val libraryStore = VaultLibraryStore(applicationContext, fileRepository)
        val importIds = if (savedInstanceState == null) {
            intent.getStringArrayListExtra(EXTRA_IMPORT_BOOK_IDS).orEmpty()
        } else {
            emptyList()
        }

        val root = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VaultShelfTheme {
                    VaultModeShell(
                        volumeName = volumeName,
                        fileRepository = fileRepository,
                        libraryStore = libraryStore,
                        externalRevision = libraryRevision,
                        initialLibrary = importIds.isNotEmpty(),
                        onOpenFile = { item ->
                            if (!VaultShelfFileRouter.openAny(
                                    this@VaultModeActivity,
                                    item.path,
                                    item.sizeBytes,
                                    volumeId,
                                )
                            ) {
                                Toast.makeText(
                                    this@VaultModeActivity,
                                    R.string.vault_open_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onOpenFiles = {
                            startActivity(
                                Intent(this@VaultModeActivity, ExplorerActivity::class.java)
                                    .putExtra("volumeId", volumeId)
                                    .putExtra("volumeName", volumeName),
                            )
                        },
                        onOpenVaultSettings = {
                            startActivity(
                                Intent(this@VaultModeActivity, DroidFsSettingsActivity::class.java),
                            )
                        },
                        onOpenVaultBackup = {
                            startActivity(
                                Intent(this@VaultModeActivity, VaultBackupActivity::class.java),
                            )
                        },
                        onLockVault = {
                            volumeManager.closeVolume(volumeId)
                        },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
        setContentView(root)

        if (importIds.isNotEmpty()) {
            importExternalLibraryBooks(importIds, fileRepository, libraryStore)
        }
    }

    override fun onResume() {
        super.onResume()
        libraryRevision += 1
    }

    private fun importExternalLibraryBooks(
        ids: List<String>,
        fileRepository: VaultFileRepository,
        libraryStore: VaultLibraryStore,
    ) {
        val localRepository = LocalLibraryRepository(applicationContext)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val imported = ArrayList<String>()
                var failed = 0
                ids.forEach { id ->
                    val book = localRepository.getBook(id)
                    if (book == null) {
                        failed += 1
                        return@forEach
                    }
                    val source = runCatching { localRepository.bookFile(id) }.getOrNull()
                    if (source == null) {
                        failed += 1
                        return@forEach
                    }
                    runCatching {
                        val vaultBook = libraryStore.importExternalLibraryBook(book, source)
                        book.legadoBookUrl?.let { bookUrl ->
                            LegadoReaderBridge.snapshot(applicationContext, bookUrl)?.let { snapshot ->
                                BookReadingPositions.save(
                                    applicationContext,
                                    VaultShelfProgressStore.fileKey(
                                        fileRepository.volumeUuid,
                                        vaultBook.path,
                                    ),
                                    snapshot.chapterIndex,
                                    snapshot.chapterPosition,
                                )
                            }
                        }
                        vaultBook
                    }.onSuccess {
                        imported += id
                    }.onFailure {
                        failed += 1
                    }
                }
                imported to failed
            }

            libraryRevision += 1
            val importedIds = result.first
            val failed = result.second
            if (importedIds.isEmpty()) {
                Toast.makeText(
                    this@VaultModeActivity,
                    R.string.vault_external_import_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }

            if (failed > 0) {
                Toast.makeText(
                    this@VaultModeActivity,
                    getString(R.string.vault_external_import_partial, failed),
                    Toast.LENGTH_LONG,
                ).show()
            }

            MaterialAlertDialogBuilder(this@VaultModeActivity)
                .setTitle(R.string.vault_external_import_done_title)
                .setMessage(
                    getString(
                        R.string.vault_external_import_done_message,
                        importedIds.size,
                    ),
                )
                .setPositiveButton(R.string.vault_external_import_delete_source) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        importedIds.forEach { id ->
                            runCatching { localRepository.deleteBook(id) }
                        }
                    }
                }
                .setNegativeButton(R.string.vault_external_import_keep_source, null)
                .show()
        }
    }

    companion object {
        const val EXTRA_VOLUME_ID = "vaultshelf.mode.volume_id"
        const val EXTRA_VOLUME_NAME = "vaultshelf.mode.volume_name"
        const val EXTRA_IMPORT_BOOK_IDS = "vaultshelf.mode.import_book_ids"
    }
}
