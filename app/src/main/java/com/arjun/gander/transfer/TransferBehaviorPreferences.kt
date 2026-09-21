package com.arjun.gander.transfer

import android.content.Context
import androidx.core.content.edit

enum class TransferRoute(val key: String) {
    EXTERNAL_FILES_TO_EXTERNAL_LIBRARY("external_files_to_external_library"),
    EXTERNAL_FILES_TO_VAULT_FILES("external_files_to_vault_files"),
    EXTERNAL_FILES_TO_VAULT_LIBRARY("external_files_to_vault_library"),
    EXTERNAL_LIBRARY_TO_EXTERNAL_FILES("external_library_to_external_files"),
    EXTERNAL_LIBRARY_TO_VAULT_FILES("external_library_to_vault_files"),
    EXTERNAL_LIBRARY_TO_VAULT_LIBRARY("external_library_to_vault_library"),
    VAULT_FILES_TO_EXTERNAL_FILES("vault_files_to_external_files"),
    VAULT_FILES_TO_EXTERNAL_LIBRARY("vault_files_to_external_library"),
    VAULT_FILES_TO_VAULT_LIBRARY("vault_files_to_vault_library"),
    VAULT_LIBRARY_TO_EXTERNAL_FILES("vault_library_to_external_files"),
    VAULT_LIBRARY_TO_EXTERNAL_LIBRARY("vault_library_to_external_library"),
    VAULT_LIBRARY_TO_VAULT_FILES("vault_library_to_vault_files"),
}

enum class TransferSourceDecision {
    KEEP,
    DELETE,
}

object TransferBehaviorPreferences {
    private const val PREFS = "vaultshelf_transfer_behavior"
    private const val KEY_AUTOMATIC = "automatic_source_handling"
    private const val ROUTE_PREFIX = "route_"

    fun isAutomatic(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOMATIC, false)

    fun setAutomatic(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_AUTOMATIC, enabled)
        }
    }

    fun decision(context: Context, route: TransferRoute): TransferSourceDecision {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ROUTE_PREFIX + route.key, TransferSourceDecision.KEEP.name)
        return runCatching { TransferSourceDecision.valueOf(raw.orEmpty()) }
            .getOrDefault(TransferSourceDecision.KEEP)
    }

    fun setDecision(
        context: Context,
        route: TransferRoute,
        decision: TransferSourceDecision,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(ROUTE_PREFIX + route.key, decision.name)
        }
    }

    /**
     * null means keep the existing per-transfer confirmation dialog.
     * Non-null means the user explicitly enabled preset handling.
     */
    fun automaticDecision(
        context: Context,
        route: TransferRoute,
    ): TransferSourceDecision? =
        if (isAutomatic(context)) decision(context, route) else null
}
