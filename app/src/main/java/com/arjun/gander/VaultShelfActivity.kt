package com.arjun.gander

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
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

        val root = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VaultShelfTheme {
                    VaultShelfShell(
                        onOpenFiles = {
                            startActivity(Intent(this@VaultShelfActivity, MainActivity::class.java))
                        },
                    )
                }
            }
        }
        setContentView(root)
    }
}
