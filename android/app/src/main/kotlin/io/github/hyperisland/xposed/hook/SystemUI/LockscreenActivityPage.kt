package io.github.hyperisland.xposed.hook.SystemUI

import android.content.ComponentName
import android.content.Intent
import android.net.Uri

/** Immutable Activity launch contract. Overlay services intentionally do not implement this. */
internal data class LockscreenActivityPage(
    val packageName: String,
    val activityName: String,
    val action: String,
    val uri: String,
    val launchExtra: String,
) {
    fun createIntent(): Intent = Intent(action).apply {
        component = ComponentName(packageName, activityName)
        data = Uri.parse(uri)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra("from", "keyguard")
        putExtra("entry_source", "swipe")
        putExtra(launchExtra, true)
    }
}
