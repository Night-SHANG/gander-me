package com.arjun.gander

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.vault.VaultBackupActivity
import com.arjun.gander.vault.VaultModeActivity
import com.arjun.gander.ui.shell.VaultShelfShell
import com.arjun.gander.ui.theme.VaultShelfTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.ArrayList
import sushi.hardcore.droidfs.MainActivity as DroidFsMainActivity
import sushi.hardcore.droidfs.SettingsActivity as DroidFsSettingsActivity

/**
 * VaultShelf's product shell.
 *
 * Gander's mature [MainActivity] and [ViewerActivity] deliberately remain separate:
 * this activity can evolve with Compose without coupling new library/vault features to
 * the document renderer that already works.
 */
class VaultShelfActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val libraryRepository = LocalLibraryRepository(applicationContext)

        val root = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VaultShelfTheme {
                    VaultShelfShell(
                        libraryRepository = libraryRepository,
                        onOpenFiles = {
                            startActivity(Intent(this@VaultShelfActivity, MainActivity::class.java))
                        },
                        onOpenVault = {
                            startActivity(
                                Intent(this@VaultShelfActivity, DroidFsMainActivity::class.java),
                            )
                        },
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
                        onImportBooksToVault = { books ->
                            startActivity(
                                Intent(this@VaultShelfActivity, DroidFsMainActivity::class.java)
                                    .putStringArrayListExtra(
                                        VaultModeActivity.EXTRA_IMPORT_BOOK_IDS,
                                        ArrayList(books.map { it.id }),
                                    ),
                            )
                        },
                        onOpenAbout = {
                            showAbout()
                        },
                        // Android 15+ is edge-to-edge by default. Use the platform-provided
                        // safe drawing area instead of fixed offsets so status bars, display
                        // cutouts and gesture/3-button navigation are handled per device.
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
        setContentView(root)
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
        private const val LICENCES_ASSET = "licences.md"
    }
}
