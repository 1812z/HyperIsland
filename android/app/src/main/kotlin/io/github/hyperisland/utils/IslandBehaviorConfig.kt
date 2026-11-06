package io.github.hyperisland.utils

/** Global values shared by settings, backups and the SystemUI hook. */
internal object IslandBehaviorConfig {
    const val STYLE_KEY = "pref_island_behavior_style"
    const val COLOR_KEY = "pref_island_indicator_color"
    const val DEFAULT = "default"
    const val SINGLE = "single"
    const val FOLLOW_ISLAND = "follow_island"
    const val INVERT_ISLAND = "invert_island"
    const val BLACK = "black"
    const val WHITE = "white"
    val styles = listOf(DEFAULT, SINGLE)
    val colors = listOf(FOLLOW_ISLAND, INVERT_ISLAND, BLACK, WHITE)
    fun style(value: String) = value.takeIf { it in styles } ?: DEFAULT
    fun color(value: String) = value.takeIf { it in colors } ?: FOLLOW_ISLAND
}
