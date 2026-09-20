package com.arjun.gander.vault

import android.content.Context

object VaultSecurityPolicy {
    fun allowPlaintextExport(context: Context): Boolean =
        context.getSharedPreferences(
            context.packageName + "_preferences",
            Context.MODE_PRIVATE,
        ).getBoolean("usf_decrypt", false)

    fun allowSystemShare(context: Context): Boolean =
        context.getSharedPreferences(
            context.packageName + "_preferences",
            Context.MODE_PRIVATE,
        ).getBoolean("usf_share", false)
}
