package com.arjun.gander

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import com.arjun.gander.files.ExternalExplorerActivity
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.ui.shell.VaultShelfShell
import com.arjun.gander.transfer.TransferBehaviorSettingsActivity
import com.arjun.gander.ui.theme.VaultShelfTheme
import com.arjun.gander.vault.VaultBackupActivity
import com.arjun.gander.vault.VaultImportTargetActivity
import com.arjun.gander.vault.VaultVolumeActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vaultshelf.droidfs.SafVolume
import java.io.File
import java.util.ArrayList
import java.util.UUID
import sushi.hardcore.droidfs.SettingsActivity as DroidFsSettingsActivity
import sushi.hardcore.droidfs.VolumeData
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.filesystems.EncryptedVolume

/**
 * VaultShelf's product shell.
 *
 * Home, library, the external Files root and settings live in this one shell. Entering an
 * authorized directory hands the actual file tree to DroidFS Explorer, which keeps this shell
 * below it so switching tabs can return to the exact directory rather than rebuilding state.
 */
class VaultShelfActivity : AppCompatActivity() {

    private var libraryRevision by mutableIntStateOf(0)
    private var requestedDestinationName by mutableStateOf("HOME")
    private var returnToFiles by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyNavigationIntent(intent)
        val libraryRepository = LocalLibraryRepository(applicationContext)

        val root = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VaultShelfTheme {
                    VaultShelfShell(
                        libraryRepository = libraryRepository,
                        externalRevision = libraryRevision,
                        onOpenExternalFolder = ::openExternalFolder,
                        onOpenVault = ::openVault,
                        onOpenVaultSettings = {
                            startActivity(
                                Intent(this@VaultShelfActivity, DroidFsSettingsActivity::class.java),
                            )
                        },
                        onOpenVaultBackup = {
                            startActivity(
                                Intent(this@VaultShelfActivity, VaultBackupActivity::class.java),
                            )
                        },
                        onOpenTransferSettings = {
                            startActivity(
                                Intent(
                                    this@VaultShelfActivity,
                                    TransferBehaviorSettingsActivity::class.java,
                                ),
                            )
                        },
                        onImportBooksToVaultFiles = { books ->
                            startActivity(
                                Intent(this@VaultShelfActivity, VaultVolumeActivity::class.java)
                                    .setAction(VaultImportTargetActivity.ACTION_IMPORT_TO_VAULT)
                                    .putStringArrayListExtra(
                                        VaultImportTargetActivity.EXTRA_SOURCE_LIBRARY_IDS,
                                        ArrayList(books.map { it.id }),
                                    )
                                    .putExtra(
                                        VaultImportTargetActivity.EXTRA_TARGET_LIBRARY,
                                        false,
                                    ),
                            )
                        },
                        onImportBooksToVaultLibrary = { books ->
                            startActivity(
                                Intent(this@VaultShelfActivity, VaultVolumeActivity::class.java)
                                    .setAction(
                                        VaultImportTargetActivity.ACTION_IMPORT_TO_VAULT_LIBRARY,
                                    )
                                    .putStringArrayListExtra(
                                        VaultImportTargetActivity.EXTRA_SOURCE_LIBRARY_IDS,
                                        ArrayList(books.map { it.id }),
                                    )
                                    .putExtra(
                                        VaultImportTargetActivity.EXTRA_TARGET_LIBRARY,
                                        true,
                                    ),
                            )
                        },
                        onOpenAbout = ::showAbout,
                        initialDestinationName = requestedDestinationName,
                        returnToFiles = returnToFiles,
                        onReturnToFiles = { finish() },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
        setContentView(root)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyNavigationIntent(intent)
    }

    private fun applyNavigationIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_PRESERVE_DESTINATION, false)) return
        requestedDestinationName =
            intent.getStringExtra(EXTRA_INITIAL_DESTINATION).orEmpty().ifBlank { "HOME" }
        returnToFiles = intent.getBooleanExtra(EXTRA_RETURN_TO_FILES, false)
    }

    override fun onResume() {
        super.onResume()
        libraryRevision += 1
    }

    private fun openVault() {
        startActivity(
            Intent(this, VaultVolumeActivity::class.java)
                .putExtra(EXTRA_VAULT_SHELL_ENTRY, true),
        )
    }

    private fun openExternalFolder(treeUri: Uri, label: String) {
        val app = application as VolumeManagerApp
        val volume = runCatching { SafVolume(applicationContext, treeUri) }.getOrElse {
            Toast.makeText(this, R.string.vault_open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val volumeId = app.volumeManager.insert(
            volume,
            VolumeData(
                uuid = UUID.randomUUID().toString(),
                name = "saf:$treeUri",
                isHidden = false,
                type = EncryptedVolume.GOCRYPTFS_VOLUME_TYPE,
            ),
        )
        val opened = runCatching {
            startActivity(
                Intent(this, ExternalExplorerActivity::class.java)
                    .putExtra("volumeId", volumeId)
                    .putExtra("volumeName", label)
                    .putExtra(EXTRA_PLAIN_VOLUME, true),
            )
            true
        }.getOrDefault(false)
        if (!opened) {
            app.volumeManager.closeVolume(volumeId)
            Toast.makeText(this, R.string.vault_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAbout() {
        val view = layoutInflater.inflate(R.layout.dialog_about, null)

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }
            .getOrNull().orEmpty()
        view.findViewById<TextView>(R.id.aboutVersion).text =
            getString(R.string.about_version, version)

        val permissions = requestedPermissions()
        val field = view.findViewById<TextView>(R.id.aboutPermissions)
        when {
            permissions == null ->
                view.findViewById<View>(R.id.aboutPermissionsCard).visibility = View.GONE
            permissions.isEmpty() -> field.setText(R.string.about_permissions_none)
            else -> field.text = permissions.joinToString("\n")
        }

        view.findViewById<View>(R.id.aboutAuthor)
            .setOnClickListener { openUrl(getString(R.string.url_author)) }
        view.findViewById<View>(R.id.aboutSource)
            .setOnClickListener { openUrl(getString(R.string.url_source)) }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_gander)
            .setView(view)
            .setPositiveButton(R.string.about_close, null)
            .show()

        view.findViewById<View>(R.id.aboutLicences).setOnClickListener {
            dialog.dismiss()
            openLicences()
        }
    }

    private fun requestedPermissions(): List<String>? = runCatching {
        packageManager
            .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .filterNot { it.startsWith("$packageName.") }
    }.getOrNull()

    private fun openLicences() {
        val file = File(cacheDir, getString(R.string.licences_file_name))
        val opened = runCatching {
            assets.open(LICENCES_ASSET).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            startActivity(
                Intent(this, ViewerActivity::class.java)
                    .putExtra(ViewerActivity.EXTRA_PATH, file.absolutePath)
            )
        }.isSuccess
        if (!opened) Toast.makeText(this, R.string.licences_failed, Toast.LENGTH_SHORT).show()
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val EXTRA_INITIAL_DESTINATION = "vaultshelf.initial_destination"
        const val EXTRA_RETURN_TO_FILES = "vaultshelf.return_to_files"
        const val EXTRA_PLAIN_VOLUME = "vaultshelf.plain_volume"
        const val EXTRA_VAULT_SHELL_ENTRY = "vaultshelf.shell_entry"
        const val EXTRA_PRESERVE_DESTINATION = "vaultshelf.preserve_destination"
        private const val LICENCES_ASSET = "licences.md"
    }
}
