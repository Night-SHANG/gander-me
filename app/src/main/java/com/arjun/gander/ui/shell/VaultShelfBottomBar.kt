package com.arjun.gander.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arjun.gander.R

internal enum class VaultShelfDestination(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    HOME(R.string.vaultshelf_nav_home, R.drawable.ic_vaultshelf_home),
    LIBRARY(R.string.vaultshelf_nav_library, R.drawable.ic_vaultshelf_library),
    FILES(R.string.vaultshelf_nav_files, R.drawable.ic_vaultshelf_files),
    VAULT(R.string.vaultshelf_nav_vault, R.drawable.ic_vaultshelf_vault),
    SETTINGS(R.string.vaultshelf_nav_settings, R.drawable.ic_vaultshelf_settings),
}

internal val VaultShelfExternalDestinations: List<VaultShelfDestination> =
    VaultShelfDestination.entries

@Composable
internal fun VaultShelfBottomBar(
    destinations: List<VaultShelfDestination>,
    selected: VaultShelfDestination,
    onSelected: (VaultShelfDestination) -> Unit,
    modifier: Modifier = Modifier,
    labelOverrides: Map<VaultShelfDestination, Int> = emptyMap(),
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = selected == destination,
                onClick = { onSelected(destination) },
                icon = {
                    Icon(
                        painter = painterResource(destination.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = {
                    Text(
                        text = stringResource(
                            labelOverrides[destination] ?: destination.labelRes,
                        ),
                        maxLines = 1,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
