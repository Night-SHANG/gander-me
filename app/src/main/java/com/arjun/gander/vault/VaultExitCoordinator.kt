package com.arjun.gander.vault

import android.app.Activity
import android.content.Intent
import com.arjun.gander.R
import com.arjun.gander.VaultShelfActivity
import com.arjun.gander.navigation.suppressTopLevelTransition
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal object VaultExitCoordinator {

    fun confirmExit(activity: Activity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.vault_exit_title)
            .setMessage(R.string.vault_exit_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.vault_exit_confirm) { _, _ ->
                exitToExternal(activity)
            }
            .show()
    }

    private fun exitToExternal(activity: Activity) {
        activity.startActivity(
            Intent(activity, VaultShelfActivity::class.java)
                .putExtra(VaultShelfActivity.EXTRA_PRESERVE_DESTINATION, true)
                .addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        activity.suppressTopLevelTransition()
        activity.finish()
        activity.suppressTopLevelTransition()
    }
}
