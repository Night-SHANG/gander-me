package com.arjun.gander.transfer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arjun.gander.R
import com.arjun.gander.ui.theme.VaultShelfTheme

class TransferBehaviorSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    VaultShelfTheme {
                        TransferBehaviorSettingsScreen(onBack = { finish() })
                    }
                }
            },
        )
    }
}

private data class RouteUi(
    val route: TransferRoute,
    val sourceLabel: Int,
    val targetLabel: Int,
)

private val ROUTES = listOf(
    RouteUi(TransferRoute.EXTERNAL_FILES_TO_EXTERNAL_LIBRARY, R.string.transfer_zone_external_files, R.string.transfer_zone_external_library),
    RouteUi(TransferRoute.EXTERNAL_FILES_TO_VAULT_FILES, R.string.transfer_zone_external_files, R.string.transfer_zone_vault_files),
    RouteUi(TransferRoute.EXTERNAL_FILES_TO_VAULT_LIBRARY, R.string.transfer_zone_external_files, R.string.transfer_zone_vault_library),
    RouteUi(TransferRoute.EXTERNAL_LIBRARY_TO_EXTERNAL_FILES, R.string.transfer_zone_external_library, R.string.transfer_zone_external_files),
    RouteUi(TransferRoute.EXTERNAL_LIBRARY_TO_VAULT_FILES, R.string.transfer_zone_external_library, R.string.transfer_zone_vault_files),
    RouteUi(TransferRoute.EXTERNAL_LIBRARY_TO_VAULT_LIBRARY, R.string.transfer_zone_external_library, R.string.transfer_zone_vault_library),
    RouteUi(TransferRoute.VAULT_FILES_TO_EXTERNAL_FILES, R.string.transfer_zone_vault_files, R.string.transfer_zone_external_files),
    RouteUi(TransferRoute.VAULT_FILES_TO_EXTERNAL_LIBRARY, R.string.transfer_zone_vault_files, R.string.transfer_zone_external_library),
    RouteUi(TransferRoute.VAULT_FILES_TO_VAULT_LIBRARY, R.string.transfer_zone_vault_files, R.string.transfer_zone_vault_library),
    RouteUi(TransferRoute.VAULT_LIBRARY_TO_EXTERNAL_FILES, R.string.transfer_zone_vault_library, R.string.transfer_zone_external_files),
    RouteUi(TransferRoute.VAULT_LIBRARY_TO_EXTERNAL_LIBRARY, R.string.transfer_zone_vault_library, R.string.transfer_zone_external_library),
    RouteUi(TransferRoute.VAULT_LIBRARY_TO_VAULT_FILES, R.string.transfer_zone_vault_library, R.string.transfer_zone_vault_files),
)

@Composable
private fun TransferBehaviorSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var automatic by remember {
        mutableStateOf(TransferBehaviorPreferences.isAutomatic(context))
    }
    var revision by remember { mutableStateOf(0) }

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
                text = stringResource(R.string.transfer_settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            SettingSwitchRow(
                title = stringResource(R.string.transfer_settings_auto_title),
                summary = stringResource(R.string.transfer_settings_auto_summary),
                checked = automatic,
                enabled = true,
                onCheckedChange = {
                    automatic = it
                    TransferBehaviorPreferences.setAutomatic(context, it)
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = stringResource(R.string.transfer_settings_routes_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ROUTES.forEach { item ->
                val decision = remember(item.route, revision) {
                    TransferBehaviorPreferences.decision(context, item.route)
                }
                val delete = decision == TransferSourceDecision.DELETE
                val routeTitle = stringResource(
                    R.string.transfer_route_label,
                    stringResource(item.sourceLabel),
                    stringResource(item.targetLabel),
                )
                SettingSwitchRow(
                    title = routeTitle,
                    summary = stringResource(
                        if (delete) R.string.transfer_source_delete else R.string.transfer_source_keep,
                    ),
                    checked = delete,
                    enabled = automatic,
                    onCheckedChange = { checked ->
                        TransferBehaviorPreferences.setDecision(
                            context,
                            item.route,
                            if (checked) TransferSourceDecision.DELETE else TransferSourceDecision.KEEP,
                        )
                        revision += 1
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
