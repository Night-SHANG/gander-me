package com.arjun.gander.files

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.arjun.gander.R
import com.arjun.gander.vault.VaultImportTargetActivity
import java.util.ArrayList
import sushi.hardcore.droidfs.MainActivity as DroidFsMainActivity
import sushi.hardcore.droidfs.R as DroidFsR
import sushi.hardcore.droidfs.explorers.ExplorerActivity

/**
 * Plain-file variant of DroidFS Explorer.
 *
 * All browsing, thumbnails, list/grid modes, sorting, selection, copy/move, rename,
 * deletion, importing, sharing and conflict handling remain DroidFS code. This class only
 * removes vault-only actions and adds the cross-volume "Import to vault" command.
 */
class ExternalExplorerActivity : ExplorerActivity() {

    private var volumeClosed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.getStringExtra("volumeName").orEmpty()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)

        menu.findItem(DroidFsR.id.lock)?.isVisible = false
        menu.findItem(DroidFsR.id.decrypt)?.isVisible = false
        menu.findItem(DroidFsR.id.unsafe_features)?.isVisible = false

        val anySelected = explorerAdapter.selectedItems.isNotEmpty()
        val importItem = menu.findItem(R.id.action_import_to_vault)
            ?: menu.add(
                Menu.NONE,
                R.id.action_import_to_vault,
                Menu.NONE,
                R.string.vaultshelf_files_import_to_vault,
            ).apply {
                setIcon(DroidFsR.drawable.icon_transfer)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
        importItem.isVisible = anySelected
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_import_to_vault) {
            val selected = explorerAdapter.selectedItems
                .sorted()
                .map { explorerElements[it] }
            if (selected.isEmpty()) return true

            startActivity(
                Intent(this, DroidFsMainActivity::class.java)
                    .setAction(VaultImportTargetActivity.ACTION_IMPORT_TO_VAULT)
                    .putExtra(
                        VaultImportTargetActivity.EXTRA_SOURCE_VOLUME_ID,
                        volumeId,
                    )
                    .putStringArrayListExtra(
                        VaultImportTargetActivity.EXTRA_SOURCE_PATHS,
                        ArrayList(selected.map { it.fullPath }),
                    )
                    .putIntegerArrayListExtra(
                        VaultImportTargetActivity.EXTRA_SOURCE_TYPES,
                        ArrayList(selected.map { it.stat.type }),
                    ),
            )
            unselectAll()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) closeTransientVolume()
        super.onDestroy()
    }

    private fun closeTransientVolume() {
        if (volumeClosed || volumeId < 0) return
        volumeClosed = true
        if (app.volumeManager.getVolume(volumeId) != null) {
            app.volumeManager.closeVolume(volumeId)
        }
    }
}
