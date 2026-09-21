package com.arjun.gander.reader

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arjun.gander.R
import com.arjun.gander.ui.theme.VaultShelfTheme

class ReaderAppearanceSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    VaultShelfTheme {
                        ReaderAppearanceSettingsScreen(onBack = { finish() })
                    }
                }
            },
        )
    }
}

@Composable
private fun ReaderAppearanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var primary by remember { mutableStateOf(ReaderChromePreferences.load(context)) }
    var saved by remember { mutableStateOf(false) }
    val valid = ReaderChromePreferences.isValid(primary)

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
                text = stringResource(R.string.reader_appearance_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.reader_appearance_scope),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReaderColorField(
                value = primary,
                onValueChange = {
                    primary = it
                    saved = false
                },
            )
            Button(
                onClick = {
                    ReaderChromePreferences.save(context, primary)
                    primary = ReaderChromePreferences.load(context)
                    saved = true
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reader_appearance_save))
            }
            TextButton(
                onClick = {
                    ReaderChromePreferences.reset(context)
                    primary = ReaderChromePreferences.DEFAULT_PRIMARY
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reader_appearance_reset))
            }
            if (saved) {
                Text(
                    stringResource(R.string.reader_appearance_saved),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ReaderColorField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val normalized = ReaderChromePreferences.normalizeHex(value)
    val preview = normalized?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: Color.Transparent

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(preview, MaterialTheme.shapes.small),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.reader_appearance_primary)) },
            supportingText = {
                Text(
                    stringResource(
                        if (normalized != null) {
                            R.string.reader_appearance_hex_hint
                        } else {
                            R.string.reader_appearance_invalid
                        },
                    ),
                )
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}
