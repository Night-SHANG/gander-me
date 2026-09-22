package com.arjun.gander.vault

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.arjun.gander.R
import com.arjun.gander.VaultShelfActivity
import com.arjun.gander.ui.shell.VAULTSHELF_PAGE_TRANSITION_MS
import com.arjun.gander.ui.shell.VaultShelfBottomBar
import com.arjun.gander.ui.shell.VaultShelfDestination
import com.arjun.gander.ui.shell.VaultShelfExternalDestinations
import com.arjun.gander.ui.shell.VaultShelfNavigationRelay
import com.arjun.gander.ui.shell.suppressVaultShelfWindowTransition
import com.arjun.gander.ui.theme.VaultShelfTheme
import sushi.hardcore.droidfs.MainActivity
import sushi.hardcore.droidfs.R as DroidFsR

/**
 * VaultShelf wrapper around DroidFS' mature volume chooser.
 *
 * DroidFS still owns volume creation, biometric/password unlock and volume management.
 * VaultShelf only supplies the shared top-level navigation surface.
 */
class VaultVolumeActivity : MainActivity() {

    init {
        applyCustomTheme = false
    }

    private var bottomNavigation: ComposeView? = null
    private var imeVisible = false
    private var windowFocused = true
    private var finishingNavigation = false
    private var pageViews: List<View> = emptyList()
    private val pageEaseOut = PathInterpolator(0f, 0f, 0.58f, 1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        val shellEntry = intent.getBooleanExtra(VaultShelfActivity.EXTRA_VAULT_SHELL_ENTRY, false)
        if (shellEntry) {
            setTheme(R.style.Theme_Gander_PeerOverlay)
        }
        super.onCreate(savedInstanceState)
        if (isFinishing || !shellEntry) return

        val content = findViewById<View>(DroidFsR.id.content_area)
        val root = content.parent as? LinearLayout ?: return
        val appBar = findViewById<View>(DroidFsR.id.toolbar).parent as View
        pageViews = listOf(appBar, content)
        root.setBackgroundColor(Color.TRANSPARENT)

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
                                openExternalDestination(destination)
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

        if (savedInstanceState == null) {
            animatePageIn()
        }
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

    private fun animatePageIn() {
        val source = runCatching {
            VaultShelfDestination.valueOf(
                intent.getStringExtra(EXTRA_SOURCE_DESTINATION)
                    ?: VaultShelfDestination.HOME.name,
            )
        }.getOrDefault(VaultShelfDestination.HOME)
        val anchor = pageViews.lastOrNull() ?: return
        anchor.doOnPreDraw {
            val width = anchor.width.toFloat()
            if (width <= 0f || isFinishing) return@doOnPreDraw
            val start = if (VaultShelfDestination.VAULT.ordinal > source.ordinal) {
                width
            } else {
                -width
            }
            pageViews.forEach { view ->
                view.animate().cancel()
                view.translationX = start
                view.animate()
                    .translationX(0f)
                    .setDuration(VAULTSHELF_PAGE_TRANSITION_MS.toLong())
                    .setInterpolator(pageEaseOut)
                    .start()
            }
        }
    }

    private fun openExternalDestination(destination: VaultShelfDestination) {
        if (finishingNavigation) return
        finishingNavigation = true
        VaultShelfNavigationRelay.navigateExternal(destination.name)

        val anchor = pageViews.lastOrNull()
        val width = anchor?.width?.toFloat() ?: 0f
        if (width <= 0f) {
            finishOverlay()
            return
        }
        val target = if (destination.ordinal > VaultShelfDestination.VAULT.ordinal) {
            -width
        } else {
            width
        }
        pageViews.forEachIndexed { index, view ->
            view.animate().cancel()
            val animator = view.animate()
                .translationX(target)
                .setDuration(VAULTSHELF_PAGE_TRANSITION_MS.toLong())
                .setInterpolator(pageEaseOut)
            if (index == pageViews.lastIndex) {
                animator.withEndAction(::finishOverlay)
            }
            animator.start()
        }
    }

    private fun finishOverlay() {
        finish()
        suppressVaultShelfWindowTransition()
    }

    companion object {
        const val EXTRA_SOURCE_DESTINATION = "vaultshelf.vault.source_destination"
    }
}
