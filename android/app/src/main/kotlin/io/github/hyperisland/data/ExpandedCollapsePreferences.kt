package io.github.hyperisland.data

/** Percentages: transparency 0 = opaque; blur 100 = the system's full content self-blur. */
object ExpandedCollapsePreferences {
    const val TYPE = "pref_expand_animation_type"
    const val REBOUND = "pref_expand_animation_rebound"
    const val RETURN_OVERSHOOT = "pref_expand_animation_return_overshoot"
    const val DEFAULT_RETURN_OVERSHOOT = true
    const val OVERSHOOT_DAMPING = "pref_expand_animation_overshoot_damping"
    const val OVERSHOOT_DURATION = "pref_expand_animation_overshoot_duration"
    const val DEFAULT_OVERSHOOT_DAMPING = 95L
    const val DEFAULT_OVERSHOOT_DURATION = 320L
    const val GESTURE_FOLLOW = "pref_expand_animation_gesture_follow"
    const val PARABOLIC = "pref_expand_animation_parabolic"
    const val THROW_STRENGTH = "pref_expand_animation_throw_strength"
    const val CURVE = "pref_expand_animation_curve"
    const val KEEP_CONTENT_SIZE = "pref_expand_animation_keep_content_size"
    const val TOP_GAP = "pref_expand_top_gap"
    const val CONTENT_TOP_GAP = "pref_expand_content_top_gap"
    const val DEFAULT_TOP_GAP = -1L
    const val DEFAULT_CONTENT_TOP_GAP = 0L
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
}
