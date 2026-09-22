package com.arjun.gander.ui.shell

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

@Composable
internal fun <T> VaultShelfDestinationCrossfade(
    targetState: T,
    content: @Composable (T) -> Unit,
) {
    Crossfade(
        targetState = targetState,
        animationSpec = tween(durationMillis = 180),
    ) { destination ->
        content(destination)
    }
}

internal fun Activity.applyVaultShelfPeerTransition() {
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
}
