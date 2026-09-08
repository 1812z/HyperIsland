package io.github.hyperisland.compose.service

import android.content.Context

/** Local-only consent state. It is intentionally excluded from module config and backups. */
internal object PrivacyConsentStore {
    private const val PREFS_NAME = "HyperIslandPrivacy"
    private const val KEY_PRIVACY_POLICY_ACCEPTED = "privacy_policy_accepted"

    fun isAccepted(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_PRIVACY_POLICY_ACCEPTED, false)

    fun accept(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_PRIVACY_POLICY_ACCEPTED, true)
        .commit()
}
