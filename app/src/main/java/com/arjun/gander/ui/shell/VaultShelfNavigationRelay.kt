package com.arjun.gander.ui.shell

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Coordinates top-level navigation while a translucent nested surface (Explorer/chooser)
 * is still on screen. The existing shell can move to the target behind the overlay, then the
 * overlay slides away without moving the bottom bar.
 */
internal object VaultShelfNavigationRelay {
    private val externalRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val vaultRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val external = externalRequests.asSharedFlow()
    val vault = vaultRequests.asSharedFlow()

    fun navigateExternal(destinationName: String) {
        externalRequests.tryEmit(destinationName)
    }

    fun navigateVault(destinationName: String) {
        vaultRequests.tryEmit(destinationName)
    }
}
