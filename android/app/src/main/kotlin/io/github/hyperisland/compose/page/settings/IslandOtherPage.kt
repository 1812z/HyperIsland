package io.github.hyperisland.compose.page.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card

@Composable
internal fun IslandOtherPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val dnd = rememberStringPreference(prefs, KEY_DND, DEFAULT_DND)
    val fullscreen = rememberStringPreference(prefs, KEY_FULLSCREEN, DEFAULT_SCENE)
    val landscape = rememberStringPreference(prefs, KEY_LANDSCAPE, DEFAULT_SCENE)
    val expandedAction = rememberStringPreference(prefs, KEY_EXPANDED_ACTION, ACTION_NONE)
    val bigAction = rememberStringPreference(prefs, KEY_BIG_ACTION, ACTION_NONE)
    val ignoreOngoing = rememberBooleanPreference(prefs, KEY_IGNORE_ONGOING, true)
    val speed = rememberLongPreference(prefs, KEY_MARQUEE_SPEED, DEFAULT_MARQUEE_SPEED)
    var speedDraft by remember { mutableFloatStateOf(speed.value.toFloat()) }

    LaunchedEffect(Unit) {
        if (!prefs.getBoolean(KEY_MARQUEE_FEATURE, false)) {
            prefs.putBoolean(KEY_MARQUEE_FEATURE, true)
        }
    }
    LaunchedEffect(speed.value) { speedDraft = speed.value.toFloat() }

    val dndValues = listOf(DEFAULT_DND, "suppress", "small_only")
    val sceneValues = listOf(DEFAULT_SCENE, "fallback", "expand")
    val expandedValues = listOf(ACTION_NONE, ACTION_CANCEL, ACTION_HIDE)
    val bigValues = listOf(ACTION_NONE, ACTION_CANCEL)

    DetailPage(title = stringResource(R.string.other), onBack = onBack) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.filter_rules_order),
                    summary = stringResource(R.string.filter_rules_order_summary),
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.filter_rules_title))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceDropdown(
                    stringResource(R.string.dnd_behavior),
                    dndSummary(dnd.value),
                    null,
                    listOf(
                        stringResource(R.string.default_option),
                        stringResource(R.string.fallback_notification),
                        stringResource(R.string.small_only),
                    ),
                    dndValues.indexOf(dnd.value).coerceAtLeast(0),
                ) {
                    dnd.value = dndValues[it]
                    if (dndValues[it] == DEFAULT_DND) prefs.remove(KEY_DND)
                    else prefs.putString(KEY_DND, dndValues[it])
                }
                PreferenceDropdown(
                    stringResource(R.string.fullscreen_rule),
                    sceneSummary(fullscreen.value),
                    null,
                    sceneLabels(),
                    sceneValues.indexOf(fullscreen.value).coerceAtLeast(0),
                ) { fullscreen.value = sceneValues[it]; prefs.putString(KEY_FULLSCREEN, sceneValues[it]) }
                PreferenceDropdown(
                    stringResource(R.string.landscape_rule),
                    sceneSummary(landscape.value),
                    null,
                    sceneLabels(),
                    sceneValues.indexOf(landscape.value).coerceAtLeast(0),
                ) { landscape.value = sceneValues[it]; prefs.putString(KEY_LANDSCAPE, sceneValues[it]) }
            }
        }
        item {
            SectionTitle(stringResource(R.string.swipe_actions))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceDropdown(
                    stringResource(R.string.expanded_collapse_action),
                    null,
                    null,
                    expandedActionLabels(),
                    expandedValues.indexOf(expandedAction.value).coerceAtLeast(0),
                ) { expandedAction.value = expandedValues[it]; prefs.putString(KEY_EXPANDED_ACTION, expandedValues[it]) }
                PreferenceDropdown(
                    stringResource(R.string.big_collapse_action),
                    null,
                    null,
                    bigActionLabels(),
                    bigValues.indexOf(bigAction.value).coerceAtLeast(0),
                ) { bigAction.value = bigValues[it]; prefs.putString(KEY_BIG_ACTION, bigValues[it]) }
                PreferenceSwitch(
                    stringResource(R.string.ignore_ongoing),
                    null,
                    null,
                    ignoreOngoing.value,
                ) { ignoreOngoing.value = it; prefs.putBoolean(KEY_IGNORE_ONGOING, it) }
            }
        }
        item {
            SectionTitle(stringResource(R.string.marquee))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSlider(
                    title = stringResource(R.string.marquee_speed),
                    icon = null,
                    value = speedDraft,
                    valueText = stringResource(R.string.marquee_speed_value, speedDraft.toInt()),
                    valueRange = 20f..500f,
                    steps = 47,
                    resetVisible = speedDraft.toLong() != DEFAULT_MARQUEE_SPEED,
                    onReset = {
                        speedDraft = DEFAULT_MARQUEE_SPEED.toFloat()
                        speed.value = DEFAULT_MARQUEE_SPEED
                        prefs.remove(KEY_MARQUEE_SPEED)
                    },
                    onValueChange = { speedDraft = it },
                    onValueChangeFinished = {
                        val next = speedDraft.toLong()
                        speed.value = next
                        prefs.putLong(KEY_MARQUEE_SPEED, next)
                    },
                )
            }
        }
    }
}

@Composable
private fun dndSummary(value: String): String = when (value) {
    "suppress" -> stringResource(R.string.behavior_suppress)
    "small_only" -> stringResource(R.string.behavior_small_only)
    else -> stringResource(R.string.behavior_default)
}

@Composable
private fun sceneSummary(value: String): String = when (value) {
    "fallback" -> stringResource(R.string.behavior_suppress)
    "expand" -> stringResource(R.string.behavior_expand)
    else -> stringResource(R.string.behavior_default)
}

@Composable
private fun sceneLabels() = listOf(
    stringResource(R.string.default_option),
    stringResource(R.string.fallback_notification),
    stringResource(R.string.expand_notification),
)

@Composable
private fun expandedActionLabels() = listOf(
    stringResource(R.string.action_none),
    stringResource(R.string.action_clear_notification),
    stringResource(R.string.action_hide_island),
)

@Composable
private fun bigActionLabels() = listOf(
    stringResource(R.string.action_none),
    stringResource(R.string.action_clear_notification),
)

private const val KEY_DND = "pref_scene_dnd"
private const val KEY_FULLSCREEN = "pref_fullscreen_behavior"
private const val KEY_LANDSCAPE = "pref_landscape_behavior"
private const val KEY_EXPANDED_ACTION = "pref_expanded_collapse_action"
private const val KEY_BIG_ACTION = "pref_big_island_collapse_action"
private const val KEY_IGNORE_ONGOING = "pref_island_swipe_ignore_ongoing"
private const val KEY_MARQUEE_FEATURE = "pref_marquee_feature"
private const val KEY_MARQUEE_SPEED = "pref_marquee_speed"
private const val DEFAULT_MARQUEE_SPEED = 100L
private const val DEFAULT_DND = "default"
private const val DEFAULT_SCENE = "off"
private const val ACTION_NONE = "none"
private const val ACTION_CANCEL = "cancel_notification"
private const val ACTION_HIDE = "hide_island"
