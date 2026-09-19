package com.arjun.gander

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.vault.VaultBackupActivity
import com.arjun.gander.ui.shell.VaultShelfShell
import com.arjun.gander.ui.theme.VaultShelfTheme
import com.vaultshelf.legado.LegadoReaderBridge
import kotlinx.coroutines.launch
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
                        onOpenReaderSettings = {
                            lifecycleScope.launch {
                                val bookUrl = libraryRepository.listBooks()
                                    .firstOrNull { !it.legadoBookUrl.isNullOrBlank() }
                                    ?.legadoBookUrl
                                if (bookUrl == null) {
                                    Toast.makeText(
                                        this@VaultShelfActivity,
                                        R.string.vaultshelf_settings_reader_requires_book,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    startActivity(
                                        LegadoReaderBridge.readerSettingsIntent(
                                            this@VaultShelfActivity,
                                            bookUrl,
                                        ),
                                    )
                                }
                            }
                        },
                        onOpenAbout = {
                            startActivity(
                                Intent(this@VaultShelfActivity, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_SHOW_ABOUT, true),
                            )
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
}
