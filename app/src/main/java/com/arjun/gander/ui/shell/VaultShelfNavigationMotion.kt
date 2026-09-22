package com.arjun.gander.ui.shell

import android.app.Activity

/**
 * Peer bottom-navigation destinations must not animate the whole Activity window.
 * Top-level content transitions are handled inside the shell pager instead.
 */
internal fun Activity.applyVaultShelfPeerTransition() {
    overridePendingTransition(0, 0)
}
