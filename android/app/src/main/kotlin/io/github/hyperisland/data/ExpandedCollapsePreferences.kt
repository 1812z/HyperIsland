package io.github.hyperisland.data

/** Percentages: transparency 0 = opaque; blur 100 = the system's full content self-blur. */
object ExpandedCollapsePreferences {
    const val ENABLED = "pref_expand_collapse_animation_enabled"
    const val TRANSPARENCY_START = "pref_expand_collapse_transparency_start"
    const val TRANSPARENCY_END = "pref_expand_collapse_transparency_end"
    const val BLUR_START = "pref_expand_collapse_blur_start"
    const val BLUR_END = "pref_expand_collapse_blur_end"
    const val DEFAULT_START_PERCENT = 0L
    const val DEFAULT_END_PERCENT = 80L
    fun defaultPercent(key: String): Long = when (key) {
        TRANSPARENCY_END, BLUR_END -> DEFAULT_END_PERCENT
        else -> DEFAULT_START_PERCENT
    }
    val percentageKeys = setOf(TRANSPARENCY_START, TRANSPARENCY_END, BLUR_START, BLUR_END)
}
