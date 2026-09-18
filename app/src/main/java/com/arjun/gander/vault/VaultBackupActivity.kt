package com.arjun.gander.vault

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.arjun.gander.R
import com.arjun.gander.ui.theme.VaultShelfTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.VolumeData

class VaultBackupActivity : AppCompatActivity() {

    private var pendingBackupUuid: String? = null
    private var refreshToken by mutableIntStateOf(0)

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val uuid = pendingBackupUuid
        pendingBackupUuid = null
        val volume = uuid?.let { wanted ->
            VaultBackupManager.hiddenVolumes(applicationContext)
                .firstOrNull { it.uuid == wanted }
        }
        if (uri != null && volume != null) {
            runBackup(volume, uri)
        }
    }

    private val restoreBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) runRestore(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingBackupUuid = savedInstanceState?.getString(STATE_PENDING_BACKUP_UUID)

        val root = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VaultShelfTheme {
                    val token = refreshToken
                    val volumes by produceState<List<VolumeData>>(
                        initialValue = emptyList(),
                        token,
                    ) {
                        value = withContext(Dispatchers.IO) {
                            VaultBackupManager.hiddenVolumes(applicationContext)
                        }
                    }

                    VaultBackupScreen(
                        volumes = volumes,
                        isOpen = { VaultBackupManager.isOpen(applicationContext, it) },
                        onBack = ::finish,
                        onBackup = ::requestBackup,
                        onRestore = {
                            restoreBackup.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                ),
                            )
                        },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingBackupUuid?.let { outState.putString(STATE_PENDING_BACKUP_UUID, it) }
        super.onSaveInstanceState(outState)
    }

    private fun requestBackup(volume: VolumeData) {
        if (VaultBackupManager.isOpen(applicationContext, volume)) {
            toast(R.string.vaultshelf_backup_lock_first)
            return
        }
        pendingBackupUuid = volume.uuid
        val fileName = volume.shortName
            .replace(Regex("""[^A-Za-z0-9._\-\u4e00-\u9fff]+"""), "_")
            .trim('_')
            .ifBlank { "vault" }
        createBackup.launch("$fileName.vsbackup")
    }

    private fun runBackup(volume: VolumeData, uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    VaultBackupManager.export(applicationContext, volume, uri)
                }
            }
            if (result.isSuccess) {
                toast(R.string.vaultshelf_backup_done)
            } else {
                toast(failureMessage(result.exceptionOrNull()))
            }
        }
    }

    private fun runRestore(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    VaultBackupManager.restore(applicationContext, uri)
                }
            }
            if (result.isSuccess) {
                refreshToken++
                toast(R.string.vaultshelf_restore_done)
            } else {
                toast(failureMessage(result.exceptionOrNull()))
            }
        }
    }

    private fun failureMessage(error: Throwable?): Int {
        val failure = (error as? VaultBackupManager.BackupException)?.failure
        return when (failure) {
            VaultBackupManager.Failure.VOLUME_OPEN -> R.string.vaultshelf_backup_lock_first
            VaultBackupManager.Failure.ALREADY_EXISTS -> R.string.vaultshelf_restore_exists
            VaultBackupManager.Failure.INVALID_BACKUP -> R.string.vaultshelf_restore_invalid
            VaultBackupManager.Failure.UNSUPPORTED_VOLUME -> R.string.vaultshelf_restore_unsupported
            VaultBackupManager.Failure.SOURCE_MISSING -> R.string.vaultshelf_backup_source_missing
            else -> R.string.vaultshelf_backup_failed
        }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val STATE_PENDING_BACKUP_UUID = "pending_backup_uuid"
    }
}

@Composable
private fun VaultBackupScreen(
    volumes: List<VolumeData>,
    isOpen: (VolumeData) -> Boolean,
    onBack: () -> Unit,
    onBackup: (VolumeData) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.vaultshelf_backup_back),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onBack),
        )

        Text(
            text = stringResource(R.string.vaultshelf_backup_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.vaultshelf_backup_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.vaultshelf_restore_action))
        }

        Text(
            text = stringResource(R.string.vaultshelf_backup_hidden_volumes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        if (volumes.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Text(
                    text = stringResource(R.string.vaultshelf_backup_empty),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            volumes.forEach { volume ->
                val open = isOpen(volume)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = volume.shortName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(
                                    if (open) {
                                        R.string.vaultshelf_backup_status_unlocked
                                    } else {
                                        R.string.vaultshelf_backup_status_locked
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (open) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = { onBackup(volume) },
                            enabled = !open,
                        ) {
                            Text(stringResource(R.string.vaultshelf_backup_action))
                        }
                    }
                }
            }
        }
    }
}
