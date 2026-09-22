package com.arjun.gander.vault

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.arjun.gander.R
import com.arjun.gander.VaultShelfActivity
import com.arjun.gander.files.VaultExplorerActivity
import com.arjun.gander.ui.theme.VaultShelfTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vaultshelf.droidfs.VaultShelfFileRouter
import java.util.ArrayList
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.SettingsActivity as DroidFsSettingsActivity
import sushi.hardcore.droidfs.VolumeDatabase
import sushi.hardcore.droidfs.VolumeOpener
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.util.finishOnClose

class VaultModeActivity : BaseActivity() {

    init {
        applyCustomTheme = false
    }

    private var libraryRevision by mutableIntStateOf(0)
    private var requestedDestinationName by mutableStateOf("HOME")
    private var returnToFiles by mutableStateOf(false)
    private lateinit var switchVolumeOpener: VolumeOpener
    private var bottomNavigationVisible by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyNavigationIntent(intent)
        VaultScreenshotPolicy.apply(this)

        val volumeId = intent.getIntExtra(EXTRA_VOLUME_ID, -1)
        val volumeName = intent.getStringExtra(EXTRA_VOLUME_NAME).orEmpty()
        val volumeManager = (application as VolumeManagerApp).volumeManager
        val volume = volumeManager.getVolume(volumeId)
        if (volumeId < 0 || volume == null) {
            finish()
            return
        }
        finishOnClose(volume)
        switchVolumeOpener = VolumeOpener(this)

        val fileRepository = VaultFileRepository(applicationContext, volumeId)
        val libraryStore = VaultLibraryStore(applicationContext, fileRepository)
        val root = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                VaultShelfTheme {
                    VaultModeShell(
                        volumeName = volumeName,
                        fileRepository = fileRepository,
                        libraryStore = libraryStore,
                        externalRevision = libraryRevision,
                        initialDestinationName = requestedDestinationName,
                        bottomBarVisible = bottomNavigationVisible,
                        onOpenFile = { item ->
                            if (!VaultShelfFileRouter.openAny(
                                    this@VaultModeActivity,
                                    item.path,
                                    item.sizeBytes,
                                    volumeId,
                                )
                            ) {
                                Toast.makeText(
                                    this@VaultModeActivity,
                                    R.string.vault_open_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onOpenFiles = {
                            if (returnToFiles) {
                                finish()
                            } else {
                                startActivity(
                                    Intent(
                                        this@VaultModeActivity,
                                        VaultExplorerActivity::class.java,
                                    )
                                        .putExtra("volumeId", volumeId)
                                        .putExtra("volumeName", volumeName),
                                )
                            }
                        },
                        onExportLibraryToVaultFiles = { entries ->
                            startActivity(
                                Intent(
                                    this@VaultModeActivity,
                                    VaultImportTargetActivity::class.java,
                                )
                                    .putExtra("volumeId", volumeId)
                                    .putExtra("volumeName", volumeName)
                                    .putStringArrayListExtra(
                                        VaultImportTargetActivity.EXTRA_SOURCE_VAULT_LIBRARY_IDS,
                                        ArrayList(entries.map { it.id }),
                                    )
                                    .putExtra(
                                        VaultImportTargetActivity.EXTRA_TARGET_LIBRARY,
                                        false,
                                    ),
                            )
                        },
                        onOpenVaultSettings = {
                            startActivity(
                                Intent(this@VaultModeActivity, DroidFsSettingsActivity::class.java),
                            )
                        },
                        onOpenVaultBackup = {
                            startActivity(
                                Intent(this@VaultModeActivity, VaultBackupActivity::class.java),
                            )
                        },
                        onLockVault = {
                            volumeManager.closeVolume(volumeId)
                        },
                        onSwitchVault = {
                            showVaultSwitchDialog(volumeId)
                        },
                        onOpenExternalDestination = { destination ->
                            startActivity(
                                Intent(this@VaultModeActivity, VaultShelfActivity::class.java)
                                    .putExtra(
                                        VaultShelfActivity.EXTRA_INITIAL_DESTINATION,
                                        destination,
                                    )
                                    .addFlags(
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                                    ),
                            )
                        },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
        setContentView(root)
        onBackPressedDispatcher.addCallback(this) {
            VaultExitCoordinator.confirmExit(this@VaultModeActivity)
        }
    }

    private fun showVaultSwitchDialog(currentVolumeId: Int) {
        val volumeManager = (application as VolumeManagerApp).volumeManager
        val volumes = VolumeDatabase(this).use { it.getVolumes() }
        if (volumes.isEmpty()) return

        val currentUuid = volumeManager.listVolumes()
            .firstOrNull { it.first == currentVolumeId }
            ?.second
            ?.uuid
        var selectedIndex = volumes.indexOfFirst { it.uuid == currentUuid }
            .takeIf { it >= 0 }
            ?: 0

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.vault_switch_volume)
            .setSingleChoiceItems(
                volumes.map { it.shortName }.toTypedArray(),
                selectedIndex,
            ) { _, which ->
                selectedIndex = which
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val target = volumes.getOrNull(selectedIndex) ?: return@setPositiveButton
                if (target.uuid == currentUuid) return@setPositiveButton
                switchVolumeOpener.openVolume(
                    target,
                    true,
                    object : VolumeOpener.VolumeOpenerCallbacks {
                        override fun onHashStorageReset() = Unit

                        override fun onVolumeOpened(id: Int) {
                            val opened = volumeManager.listVolumes()
                                .firstOrNull { it.first == id }
                                ?.second
                                ?: target
                            startActivity(
                                Intent(this@VaultModeActivity, VaultModeActivity::class.java)
                                    .putExtra(EXTRA_VOLUME_ID, id)
                                    .putExtra(EXTRA_VOLUME_NAME, opened.shortName)
                                    .putExtra(EXTRA_INITIAL_DESTINATION, "HOME"),
                            )
                            finish()
                        }
                    },
                )
            }
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyNavigationIntent(intent)
        VaultScreenshotPolicy.apply(this)
    }

    private fun applyNavigationIntent(intent: Intent) {
        requestedDestinationName =
            intent.getStringExtra(EXTRA_INITIAL_DESTINATION).orEmpty().ifBlank { "HOME" }
        returnToFiles = intent.getBooleanExtra(EXTRA_RETURN_TO_FILES, false)
    }

    override fun onResume() {
        super.onResume()
        VaultScreenshotPolicy.apply(this)
        libraryRevision += 1
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        bottomNavigationVisible = hasFocus
        if (hasFocus) VaultScreenshotPolicy.apply(this)
    }

    companion object {
        const val EXTRA_VOLUME_ID = "vaultshelf.mode.volume_id"
        const val EXTRA_VOLUME_NAME = "vaultshelf.mode.volume_name"
        const val EXTRA_INITIAL_DESTINATION = "vaultshelf.mode.initial_destination"
        const val EXTRA_RETURN_TO_FILES = "vaultshelf.mode.return_to_files"
    }
}
