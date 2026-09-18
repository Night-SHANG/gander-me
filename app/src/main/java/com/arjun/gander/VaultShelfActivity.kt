package com.arjun.gander

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.ui.shell.VaultShelfShell
import com.arjun.gander.ui.theme.VaultShelfTheme
import sushi.hardcore.droidfs.MainActivity as DroidFsMainActivity

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
