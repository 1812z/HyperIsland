package io.github.hyperisland.xposed.hook.SystemUI.animation

/** Controls suppression for the user-configured expanded top spacing. */
internal object ExpandedOcclusionPolicy {
    const val FADE_DURATION_MS = 120L

    fun shouldSuppress(hasCustomTopSpacing: Boolean, isExpanded: Boolean): Boolean =
        hasCustomTopSpacing && isExpanded
}
