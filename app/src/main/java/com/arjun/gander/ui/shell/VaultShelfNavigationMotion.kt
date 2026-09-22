package com.arjun.gander.ui.shell

import android.app.Activity
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState

internal const val VAULTSHELF_PAGE_TRANSITION_MS = 300
private val TabEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

@OptIn(ExperimentalFoundationApi::class)
internal suspend fun PagerState.animateVaultShelfPageTo(page: Int) {
    if (currentPage == page) return
    animateScrollToPage(
        page = page,
        animationSpec = tween(
            durationMillis = VAULTSHELF_PAGE_TRANSITION_MS,
            easing = TabEaseOut,
        ),
    )
}

/**
 * Peer destinations that live in separate Activities must not animate the whole window.
 * Top-level bottom-navigation motion is handled by the shell pager.
 */
internal fun Activity.applyVaultShelfPeerTransition() {
    overridePendingTransition(0, 0)
}

/**
 * Nested content owns its own motion. Suppress the Activity window animation so shared shell
 * chrome (especially the bottom bar) stays visually fixed while only the content moves.
 */
internal fun Activity.suppressVaultShelfWindowTransition() {
    overridePendingTransition(0, 0)
}
