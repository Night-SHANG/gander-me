package com.arjun.gander.vault

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.arjun.gander.VaultShelfActivity
import com.arjun.gander.ui.shell.VaultShelfBottomBar
import com.arjun.gander.ui.shell.VaultShelfDestination
import com.arjun.gander.ui.shell.VaultShelfExternalDestinations
import com.arjun.gander.ui.theme.VaultShelfTheme
import sushi.hardcore.droidfs.MainActivity
import sushi.hardcore.droidfs.R as DroidFsR

/**
 * VaultShelf wrapper around DroidFS' mature volume chooser.
 *
 * DroidFS still owns volume creation, biometric/password unlock and volume management.
 * VaultShelf only supplies the same global bottom navigation used everywhere else.
 */
class VaultVolumeActivity : MainActivity() {

    companion object {
        const val EXTRA_SWITCHING_VAULT = "vaultshelf.switching_vault"
        const val EXTRA_CURRENT_VOLUME_UUID = "vaultshelf.current_volume_uuid"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing || !intent.getBooleanExtra(VaultShelfActivity.EXTRA_VAULT_SHELL_ENTRY, false)) {
            return
        }

        val content = findViewById<android.view.View>(DroidFsR.id.content_area)
        val root = content.parent as? LinearLayout ?: return
        content.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )

        root.addView(
            ComposeView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
                )
                setContent {
                    VaultShelfTheme {
                        VaultShelfBottomBar(
                            destinations = VaultShelfExternalDestinations,
                            selected = VaultShelfDestination.VAULT,
                            onSelected = { destination ->
                                if (destination != VaultShelfDestination.VAULT) {
                                    openExternalDestination(destination.name)
                                }
                            },
                        )
                    }
                }
            },
        )
    }

    private fun openExternalDestination(destination: String) {
        startActivity(
            Intent(this, VaultShelfActivity::class.java)
                .putExtra(VaultShelfActivity.EXTRA_INITIAL_DESTINATION, destination)
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
        )
        overridePendingTransition(0, 0)
        finish()
    }
}
