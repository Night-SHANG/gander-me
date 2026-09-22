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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.arjun.gander.R
import com.arjun.gander.ui.theme.VaultShelfTheme
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import java.util.Locale

class ReaderAppearanceSettingsActivity : AppCompatActivity(), ColorPickerDialogListener {

    private var primary by mutableStateOf(ReaderChromePreferences.DEFAULT_PRIMARY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        primary = ReaderChromePreferences.load(this)
        setContentView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    VaultShelfTheme {
                        ReaderAppearanceSettingsScreen(
                            primary = primary,
                            onPickColor = ::showColorPicker,
                            onSave = {
                                ReaderChromePreferences.save(this@ReaderAppearanceSettingsActivity, primary)
                                primary = ReaderChromePreferences.load(this@ReaderAppearanceSettingsActivity)
                            },
                            onReset = {
                                ReaderChromePreferences.reset(this@ReaderAppearanceSettingsActivity)
                                primary = ReaderChromePreferences.DEFAULT_PRIMARY
                            },
                            onBack = { finish() },
                        )
                    }
                }
            },
        )
    }

    private fun showColorPicker() {
        val initial = ReaderChromePreferences.normalizeHex(primary)
            ?.let { runCatching { it.toColorInt() }.getOrNull() }
            ?: ReaderChromePreferences.DEFAULT_PRIMARY.toColorInt()
        ColorPickerDialog.newBuilder()
            .setColor(initial)
            .setShowAlphaSlider(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setDialogId(READER_INTERFACE_COLOR)
            .show(this)
    }

    override fun onColorSelected(dialogId: Int, color: Int) {
        if (dialogId != READER_INTERFACE_COLOR) return
        primary = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
    }

    override fun onDialogDismissed(dialogId: Int) = Unit

    private companion object {
        const val READER_INTERFACE_COLOR = 0x5653
    }
}

@Composable
private fun ReaderAppearanceSettingsScreen(
    primary: String,
    onPickColor: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var saved by remember(primary) { mutableStateOf(false) }
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
            ReaderColorPickerField(
                value = primary,
                onPickColor = onPickColor,
            )
            Button(
                onClick = {
                    onSave()
                    saved = true
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.reader_appearance_save))
            }
            TextButton(
                onClick = {
                    onReset()
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
private fun ReaderColorPickerField(
    value: String,
    onPickColor: () -> Unit,
) {
    val preview = ReaderChromePreferences.normalizeHex(value)?.let {
        runCatching { Color(it.toColorInt()) }.getOrNull()
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
        OutlinedButton(
            onClick = onPickColor,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.reader_appearance_pick_color))
        }
    }
}
