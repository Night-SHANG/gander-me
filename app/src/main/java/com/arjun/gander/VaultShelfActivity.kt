package com.arjun.gander

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.arjun.gander.library.LocalLibraryRepository
import com.arjun.gander.ui.shell.VaultShelfShell
import com.arjun.gander.ui.theme.VaultShelfTheme

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
                        // Android 15+ is edge-to-edge by default. Keep our custom bottom
                        // navigation above gesture/3-button navigation instead of relying
                        // on a fixed dp offset that varies by phone.
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
            }
        }
        setContentView(root)
    }
}
