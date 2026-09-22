package com.arjun.gander.vault

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
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
 * DroidFS owns volume creation, biometric/password unlock and volume management.
 * VaultShelf only adds the shared bottom navigation; the Activity otherwise uses
 * ordinary opaque Android window/navigation behaviour.
 */
class VaultVolumeActivity : MainActivity() {

    init {
        applyCustomTheme = false
    }

    private var bottomNavigation: ComposeView? = null
    private var imeVisible = false
    private var windowFocused = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing || !intent.getBooleanExtra(VaultShelfActivity.EXTRA_VAULT_SHELL_ENTRY, false)) {
            return
        }

        val content = findViewById<View>(DroidFsR.id.content_area)
        val root = content.parent as? LinearLayout ?: return
        content.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )

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
        bottomNavigation = navigation
        ViewCompat.setOnApplyWindowInsetsListener(navigation) { _, insets ->
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            updateBottomNavigationVisibility()
            insets
        }
        ViewCompat.requestApplyInsets(navigation)
        root.addView(navigation)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        val windowContent = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(windowContent) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(windowContent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocused = hasFocus
        updateBottomNavigationVisibility()
    }

    private fun updateBottomNavigationVisibility() {
        bottomNavigation?.isVisible = windowFocused && !imeVisible
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
        finish()
    }
}
