package com.arjun.gander.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.arjun.gander.R

private enum class VaultShelfDestination(@StringRes val labelRes: Int) {
    HOME(R.string.vaultshelf_nav_home),
    LIBRARY(R.string.vaultshelf_nav_library),
    FILES(R.string.vaultshelf_nav_files),
    VAULT(R.string.vaultshelf_nav_vault),
    SETTINGS(R.string.vaultshelf_nav_settings),
}

@Composable
fun VaultShelfShell(
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedName by rememberSaveable { mutableStateOf(VaultShelfDestination.HOME.name) }
    val selected = VaultShelfDestination.entries
        .firstOrNull { it.name == selectedName }
        ?: VaultShelfDestination.HOME

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FluentBottomBar(
                selected = selected,
                onSelected = { selectedName = it.name },
            )
        },
    ) { innerPadding ->
        when (selected) {
            VaultShelfDestination.HOME -> HomeScreen(
                modifier = Modifier.padding(innerPadding),
                onOpenFiles = onOpenFiles,
                onOpenLibrary = { selectedName = VaultShelfDestination.LIBRARY.name },
                onOpenVault = { selectedName = VaultShelfDestination.VAULT.name },
            )

            VaultShelfDestination.LIBRARY -> FoundationScreen(
                titleRes = R.string.vaultshelf_library_title,
                detailRes = R.string.vaultshelf_library_placeholder,
                modifier = Modifier.padding(innerPadding),
            )

            VaultShelfDestination.FILES -> FilesScreen(
                onOpenFiles = onOpenFiles,
                modifier = Modifier.padding(innerPadding),
            )

            VaultShelfDestination.VAULT -> FoundationScreen(
                titleRes = R.string.vaultshelf_vault_title,
                detailRes = R.string.vaultshelf_vault_placeholder,
                modifier = Modifier.padding(innerPadding),
            )

            VaultShelfDestination.SETTINGS -> FoundationScreen(
                titleRes = R.string.vaultshelf_settings_title,
                detailRes = R.string.vaultshelf_settings_placeholder,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onOpenFiles: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.vaultshelf_app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            StatusPill()
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.vaultshelf_home_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.vaultshelf_home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.vaultshelf_quick_access),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp),
        )

        FeaturePanel(
            titleRes = R.string.vaultshelf_open_documents,
            detailRes = R.string.vaultshelf_open_documents_detail,
            onClick = onOpenFiles,
        )
        FeaturePanel(
            titleRes = R.string.vaultshelf_library_card,
            detailRes = R.string.vaultshelf_library_card_detail,
            onClick = onOpenLibrary,
        )
        FeaturePanel(
            titleRes = R.string.vaultshelf_vault_card,
            detailRes = R.string.vaultshelf_vault_card_detail,
            onClick = onOpenVault,
        )

        OfflinePanel()
    }
}

@Composable
private fun FeaturePanel(
    @StringRes titleRes: Int,
    @StringRes detailRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(detailRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OfflinePanel(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.vaultshelf_offline_badge),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.vaultshelf_offline_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun FilesScreen(
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.vaultshelf_files_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.vaultshelf_files_detail),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onOpenFiles,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(stringResource(R.string.vaultshelf_open_file_browser))
        }
    }
}

@Composable
private fun FoundationScreen(
    @StringRes titleRes: Int,
    @StringRes detailRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.vaultshelf_foundation_status),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(detailRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = stringResource(R.string.vaultshelf_offline_badge),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FluentBottomBar(
    selected: VaultShelfDestination,
    onSelected: (VaultShelfDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            VaultShelfDestination.entries.forEach { destination ->
                val isSelected = selected == destination
                val background = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(background)
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelected(destination) },
                            role = Role.Tab,
                        )
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            ),
                    )
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
