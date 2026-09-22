package com.arjun.gander.ui.shell

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

@Composable
internal fun <T> VaultShelfDestinationTransition(
    targetState: T,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            (
                fadeIn(
                    animationSpec = tween(
                        durationMillis = 180,
                        delayMillis = 45,
                    ),
                ) +
                    scaleIn(
                        initialScale = 0.985f,
                        animationSpec = tween(
                            durationMillis = 180,
                            delayMillis = 45,
                        ),
                    )
                ).togetherWith(
                    fadeOut(
                        animationSpec = tween(durationMillis = 90),
                    ),
                )
        },
        label = "VaultShelfDestinationTransition",
    ) { destination ->
        content(destination)
    }
}

/**
 * Peer destinations that live in separate Activities must not fade the whole window.
 * The previous full-window fade exposed the window background and looked like a dark flash.
 */
internal fun Activity.applyVaultShelfPeerTransition() {
    overridePendingTransition(0, 0)
}
