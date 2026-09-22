package com.arjun.gander.ui.shell

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier

internal const val VAULTSHELF_PAGE_TRANSITION_MS = 300
internal val VaultShelfTabEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * Move directly between the current and requested bottom-navigation destinations.
 * Skipping tabs never exposes the destinations between them.
 */
@Composable
internal fun <T : Any> VaultShelfDirectionalContent(
    targetState: T,
    indexOf: (T) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            val forward = indexOf(targetState) > indexOf(initialState)
            slideInHorizontally(
                animationSpec = tween(
                    durationMillis = VAULTSHELF_PAGE_TRANSITION_MS,
                    easing = VaultShelfTabEaseOut,
                ),
                initialOffsetX = { width -> if (forward) width else -width },
            ) togetherWith slideOutHorizontally(
                animationSpec = tween(
                    durationMillis = VAULTSHELF_PAGE_TRANSITION_MS,
                    easing = VaultShelfTabEaseOut,
                ),
                targetOffsetX = { width -> if (forward) -width else width },
            )
        },
        modifier = modifier,
        label = "VaultShelfTopLevelNavigation",
    ) { destination ->
        stateHolder.SaveableStateProvider(destination.toString()) {
            content(destination)
        }
    }
}

/**
 * Separate Activities never animate their full window. Their content surface owns the motion so
 * the repeated bottom bar can remain visually stationary.
 */
internal fun Activity.applyVaultShelfPeerTransition() {
    overridePendingTransition(0, 0)
}

internal fun Activity.suppressVaultShelfWindowTransition() {
    overridePendingTransition(0, 0)
}
