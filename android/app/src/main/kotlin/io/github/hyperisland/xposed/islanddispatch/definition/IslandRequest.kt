package io.github.hyperisland.xposed.islanddispatch.definition

import android.app.Notification
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle

data class IslandRequest(
    val title: String,
    val content: String,
    val icon: Icon? = null,
    val notifId: Int = IslandDispatchContract.NOTIF_ID,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
    val preserveStatusBarSmallIcon: Boolean = true,
    val highlightColor: String? = null,
    val showLeftHighlightColor: Boolean = false,
    val showRightHighlightColor: Boolean = false,
    val showLeftNarrowFont: Boolean = false,
    val showRightNarrowFont: Boolean = false,
    val outerGlow: Boolean = false,
    val islandOuterGlow: Boolean = false,
    val islandOuterGlowColor: String? = null,
    val outEffectColor: String? = null,
    val sourcePackage: String? = null,
    val sourceChannelId: String? = null,
    val dismissIsland: Boolean = false,
    val contentIntent: android.app.PendingIntent? = null,
    val isOngoing: Boolean = false,
    val actions: List<Notification.Action> = emptyList(),
    val showIslandIcon: Boolean = true,
    val aodText: String = "default",
    val aodTitle: String? = null,
    val aodCustomizationJson: String? = null,
    val clearBeforePost: Boolean = false,
    val islandOnly: Boolean = false,
    val islandEnabled: Boolean = true,
    val focusTitle: String? = null,
    val focusContent: String? = null,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_TITLE, title)
        putString(KEY_CONTENT, content)
        putParcelable(KEY_ICON, icon)
        putInt(KEY_NOTIF_ID, notifId)
        putInt(KEY_TIMEOUT, timeoutSecs)
        putBoolean(KEY_FIRST_FLOAT, firstFloat)
        putBoolean(KEY_ENABLE_FLOAT, enableFloat)
        putBoolean(KEY_SHOW_NOTIF, showNotification)
        putBoolean(KEY_PRESERVE_SMALL_ICON, preserveStatusBarSmallIcon)
        putString(KEY_HIGHLIGHT, highlightColor)
        putBoolean(KEY_LEFT_HIGHLIGHT, showLeftHighlightColor)
        putBoolean(KEY_RIGHT_HIGHLIGHT, showRightHighlightColor)
        putBoolean(KEY_LEFT_NARROW_FONT, showLeftNarrowFont)
        putBoolean(KEY_RIGHT_NARROW_FONT, showRightNarrowFont)
        putBoolean(KEY_OUTER_GLOW, outerGlow)
        putBoolean(KEY_ISLAND_OUTER_GLOW, islandOuterGlow)
        putString(KEY_ISLAND_OUTER_GLOW_COLOR, islandOuterGlowColor)
        putString(KEY_OUT_EFFECT_COLOR, outEffectColor)
        putString(KEY_SOURCE_PACKAGE, sourcePackage)
        putString(KEY_SOURCE_CHANNEL_ID, sourceChannelId)
        putBoolean(KEY_DISMISS, dismissIsland)
        putParcelable(KEY_CONTENT_INTENT, contentIntent)
        putBoolean(KEY_ONGOING, isOngoing)
        putBoolean(KEY_SHOW_ISLAND_ICON, showIslandIcon)
        putString(KEY_AOD_TEXT, aodText)
        putString(KEY_AOD_TITLE, aodTitle)
        putString(KEY_AOD_CUSTOM, aodCustomizationJson)
        if (actions.isNotEmpty()) putParcelableArray(KEY_ACTIONS, actions.toTypedArray())
        putBoolean(KEY_CLEAR_BEFORE_POST, clearBeforePost)
        putBoolean(KEY_ISLAND_ONLY, islandOnly)
        putBoolean(KEY_ISLAND_ENABLED, islandEnabled)
        putString(KEY_FOCUS_TITLE, focusTitle)
        putString(KEY_FOCUS_CONTENT, focusContent)
    }

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_CONTENT = "content"
        private const val KEY_ICON = "icon"
        private const val KEY_NOTIF_ID = "notifId"
        private const val KEY_TIMEOUT = "timeoutSecs"
        private const val KEY_FIRST_FLOAT = "firstFloat"
        private const val KEY_ENABLE_FLOAT = "enableFloat"
        private const val KEY_SHOW_NOTIF = "showNotification"
        private const val KEY_PRESERVE_SMALL_ICON = "preserveStatusBarSmallIcon"
        private const val KEY_HIGHLIGHT = "highlightColor"
        private const val KEY_LEFT_HIGHLIGHT = "showLeftHighlightColor"
        private const val KEY_RIGHT_HIGHLIGHT = "showRightHighlightColor"
        private const val KEY_LEFT_NARROW_FONT = "showLeftNarrowFont"
        private const val KEY_RIGHT_NARROW_FONT = "showRightNarrowFont"
        private const val KEY_OUTER_GLOW = "outerGlow"
        private const val KEY_ISLAND_OUTER_GLOW = "islandOuterGlow"
        private const val KEY_ISLAND_OUTER_GLOW_COLOR = "islandOuterGlowColor"
        private const val KEY_OUT_EFFECT_COLOR = "outEffectColor"
        private const val KEY_SOURCE_PACKAGE = "sourcePackage"
        private const val KEY_SOURCE_CHANNEL_ID = "sourceChannelId"
        private const val KEY_DISMISS = "dismissIsland"
        private const val KEY_CONTENT_INTENT = "contentIntent"
        private const val KEY_ONGOING = "isOngoing"
        private const val KEY_ACTIONS = "actions"
        private const val KEY_SHOW_ISLAND_ICON = "showIslandIcon"
        private const val KEY_AOD_TEXT = "aodText"
        private const val KEY_AOD_TITLE = "aodTitle"
        private const val KEY_AOD_CUSTOM = "aodCustomizationJson"
        private const val KEY_CLEAR_BEFORE_POST = "clearBeforePost"
        private const val KEY_ISLAND_ONLY = "islandOnly"
        private const val KEY_ISLAND_ENABLED = "islandEnabled"
        private const val KEY_FOCUS_TITLE = "focusTitle"
        private const val KEY_FOCUS_CONTENT = "focusContent"

        fun fromBundle(b: Bundle) = IslandRequest(
            title = b.getString(KEY_TITLE, ""),
            content = b.getString(KEY_CONTENT, ""),
            icon = iconFromBundle(b),
            notifId = b.getInt(KEY_NOTIF_ID, IslandDispatchContract.NOTIF_ID),
            timeoutSecs = b.getInt(KEY_TIMEOUT, 5),
            firstFloat = b.getBoolean(KEY_FIRST_FLOAT, true),
            enableFloat = b.getBoolean(KEY_ENABLE_FLOAT, true),
            showNotification = b.getBoolean(KEY_SHOW_NOTIF, true),
            preserveStatusBarSmallIcon = b.getBoolean(KEY_PRESERVE_SMALL_ICON, true),
            highlightColor = b.getString(KEY_HIGHLIGHT),
            showLeftHighlightColor = b.getBoolean(KEY_LEFT_HIGHLIGHT, false),
            showRightHighlightColor = b.getBoolean(KEY_RIGHT_HIGHLIGHT, false),
            showLeftNarrowFont = b.getBoolean(KEY_LEFT_NARROW_FONT, false),
            showRightNarrowFont = b.getBoolean(KEY_RIGHT_NARROW_FONT, false),
            outerGlow = b.getBoolean(KEY_OUTER_GLOW, false),
            islandOuterGlow = b.getBoolean(KEY_ISLAND_OUTER_GLOW, false),
            islandOuterGlowColor = b.getString(KEY_ISLAND_OUTER_GLOW_COLOR),
            outEffectColor = b.getString(KEY_OUT_EFFECT_COLOR),
            sourcePackage = b.getString(KEY_SOURCE_PACKAGE),
            sourceChannelId = b.getString(KEY_SOURCE_CHANNEL_ID),
            dismissIsland = b.getBoolean(KEY_DISMISS, false),
            contentIntent = pendingIntentFromBundle(b),
            isOngoing = b.getBoolean(KEY_ONGOING, false),
            actions = actionsFromBundle(b),
            showIslandIcon = b.getBoolean(KEY_SHOW_ISLAND_ICON, true),
            aodText = b.getString(KEY_AOD_TEXT, "default"),
            aodTitle = b.getString(KEY_AOD_TITLE),
            aodCustomizationJson = b.getString(KEY_AOD_CUSTOM),
            clearBeforePost = b.getBoolean(KEY_CLEAR_BEFORE_POST, false),
            islandOnly = b.getBoolean(KEY_ISLAND_ONLY, false),
            islandEnabled = b.getBoolean(KEY_ISLAND_ENABLED, true),
            focusTitle = b.getString(KEY_FOCUS_TITLE),
            focusContent = b.getString(KEY_FOCUS_CONTENT),
        )

        private fun iconFromBundle(b: Bundle): Icon? =
            if (Build.VERSION.SDK_INT >= 33) b.getParcelable(KEY_ICON, Icon::class.java)
            else @Suppress("DEPRECATION") b.getParcelable(KEY_ICON)

        private fun actionsFromBundle(b: Bundle): List<Notification.Action> = try {
            if (Build.VERSION.SDK_INT >= 33) {
                b.getParcelableArray(KEY_ACTIONS, Notification.Action::class.java)?.toList() ?: emptyList()
            } else {
                @Suppress("DEPRECATION")
                (b.getParcelableArray(KEY_ACTIONS) as? Array<*>)
                    ?.filterIsInstance<Notification.Action>()
                    ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        private fun pendingIntentFromBundle(b: Bundle): android.app.PendingIntent? =
            if (Build.VERSION.SDK_INT >= 33)
                b.getParcelable(KEY_CONTENT_INTENT, android.app.PendingIntent::class.java)
            else
                @Suppress("DEPRECATION") b.getParcelable(KEY_CONTENT_INTENT)

        fun fromIntent(intent: Intent) = fromBundle(intent.extras ?: Bundle())
    }
}
