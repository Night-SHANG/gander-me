package com.arjun.gander.vault

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arjun.gander.R
import com.arjun.gander.ui.theme.VaultShelfTheme
import sushi.hardcore.droidfs.SettingsActivity as DroidFsSettingsActivity
import sushi.hardcore.droidfs.VolumeData
import sushi.hardcore.droidfs.VolumeDatabase

class VaultSettingsActivity : AppCompatActivity() {
    private var revision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    VaultShelfTheme {
                        VaultSettingsScreen(
                            revision = revision,
                            onBack = { finish() },
                            onDefaultChanged = { revision += 1 },
                            onOpenSecurity = {
                                startActivity(
                                    Intent(
                                        this@VaultSettingsActivity,
                                        DroidFsSettingsActivity::class.java,
                                    ),
                                )
                            },
                        )
                    }
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        revision += 1
    }
}

@Composable
private fun VaultSettingsScreen(
    revision: Int,
    onBack: () -> Unit,
    onDefaultChanged: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val volumes = androidx.compose.runtime.remember(revision) {
        VolumeDatabase(context).use { it.getVolumes() }
    }
    val defaultVolume = androidx.compose.runtime.remember(volumes, revision) {
        VaultDefaultVolumePreference.resolve(context, volumes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.gander_ic_back),
                    contentDescription = stringResource(R.string.gander_back),
                )
            }
            Text(
                text = stringResource(R.string.vaultshelf_settings_vault),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.vault_default_volume_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            DefaultVolumeRow(
                volume = null,
                selected = defaultVolume == null,
                onClick = {
                    VaultDefaultVolumePreference.clear(context)
                    onDefaultChanged()
                },
            )
            volumes.forEach { volume ->
                DefaultVolumeRow(
                    volume = volume,
                    selected = defaultVolume?.uuid == volume.uuid,
                    onClick = {
                        VaultDefaultVolumePreference.set(context, volume)
                        onDefaultChanged()
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSecurity),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        stringResource(R.string.vault_settings_security),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.vault_settings_security_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultVolumeRow(
    volume: VolumeData?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = volume?.shortName ?: stringResource(R.string.vault_default_volume_none)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (selected) {
                    Text(
                        stringResource(R.string.vault_default_volume_selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
