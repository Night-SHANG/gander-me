package com.arjun.gander.vault

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.arjun.gander.VaultShelfActivity
import com.arjun.gander.ui.shell.VaultShelfBottomBar
import com.arjun.gander.ui.shell.VaultShelfDestination
import com.arjun.gander.ui.shell.VaultShelfExternalDestinations
import com.arjun.gander.ui.theme.VaultShelfTheme
import com.arjun.gander.ui.shell.applyVaultShelfPeerTransition
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import sushi.hardcore.droidfs.MainActivity
import sushi.hardcore.droidfs.R as DroidFsR

/**
 * VaultShelf wrapper around DroidFS' mature volume chooser.
 *
 * DroidFS still owns volume creation, biometric/password unlock and volume management.
 * VaultShelf only supplies the same global bottom navigation used everywhere else.
 */
class VaultVolumeActivity : MainActivity() {

    private var selectedVolumeCount = 0

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

        onBackPressedDispatcher.addCallback(this) {
            if (selectedVolumeCount > 0) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            } else {
                showExitConfirmation()
            }
        }

        val navigation = ComposeView(this).apply {
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
        }
        ViewCompat.setOnApplyWindowInsetsListener(navigation) { view, insets ->
            view.isVisible = !insets.isVisible(WindowInsetsCompat.Type.ime())
            insets
        }
        ViewCompat.requestApplyInsets(navigation)
        root.addView(navigation)
    }

    override fun onSelectionChanged(size: Int) {
        selectedVolumeCount = size
        super.onSelectionChanged(size)
    }

    private fun showExitConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(com.arjun.gander.R.string.vault_exit_title)
            .setMessage(com.arjun.gander.R.string.vault_exit_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(com.arjun.gander.R.string.vault_exit_confirm) { _, _ ->
                finish()
                applyVaultShelfPeerTransition()
            }
            .show()
    }

    private fun openExternalDestination(destination: String) {
        startActivity(
            Intent(this, VaultShelfActivity::class.java)
                .putExtra(VaultShelfActivity.EXTRA_INITIAL_DESTINATION, destination)
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        applyVaultShelfPeerTransition()
        finish()
    }
}
