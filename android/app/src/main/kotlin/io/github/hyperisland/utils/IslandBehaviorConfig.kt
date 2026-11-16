package io.github.hyperisland.utils

/** Global values shared by settings, backups and the SystemUI hook. */
internal object IslandBehaviorConfig {
    const val STYLE_KEY = "pref_island_behavior_style"
    const val COLOR_KEY = "pref_island_indicator_color"
    /** 指示器与岛边缘的间隙，单位 dp。 */
    const val INDICATOR_GAP_KEY = "pref_island_indicator_gap"
    /** 指示器圆弧张角，单位度。 */
    const val INDICATOR_ANGLE_KEY = "pref_island_indicator_angle"
    /** 指示器描边宽度，单位 dp。 */
    const val INDICATOR_WIDTH_KEY = "pref_island_indicator_width"
    const val DEFAULT = "default"
    const val SINGLE = "single"
    const val FOLLOW_ISLAND = "follow_island"
    const val INVERT_ISLAND = "invert_island"
    const val BLACK = "black"
    const val WHITE = "white"
    val styles = listOf(DEFAULT, SINGLE)
    val colors = listOf(FOLLOW_ISLAND, INVERT_ISLAND, BLACK, WHITE)

    const val INDICATOR_GAP_MIN = 0L
    const val INDICATOR_GAP_MAX = 24L
    const val INDICATOR_GAP_DEFAULT = 4L
    const val INDICATOR_ANGLE_MIN = 10L
    const val INDICATOR_ANGLE_MAX = 180L
    const val INDICATOR_ANGLE_DEFAULT = 100L
    const val INDICATOR_WIDTH_MIN = 1L
    const val INDICATOR_WIDTH_MAX = 8L
    const val INDICATOR_WIDTH_DEFAULT = 2L

    fun style(value: String) = value.takeIf { it in styles } ?: DEFAULT
    fun color(value: String) = value.takeIf { it in colors } ?: FOLLOW_ISLAND
    fun indicatorGap(value: Long) = value.coerceIn(INDICATOR_GAP_MIN, INDICATOR_GAP_MAX)
    fun indicatorAngle(value: Long) = value.coerceIn(INDICATOR_ANGLE_MIN, INDICATOR_ANGLE_MAX)
    fun indicatorWidth(value: Long) = value.coerceIn(INDICATOR_WIDTH_MIN, INDICATOR_WIDTH_MAX)
}
