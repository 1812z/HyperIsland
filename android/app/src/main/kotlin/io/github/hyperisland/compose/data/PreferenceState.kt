package io.github.hyperisland.compose.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
internal fun rememberBooleanPreference(
    prefs: FlutterPrefsRepository,
    key: String,
    default: Boolean,
): MutableState<Boolean> {
    val state = remember(prefs, key) { mutableStateOf(prefs.getBoolean(key, default)) }
    DisposableEffect(prefs, key) {
        val unregister = prefs.addChangeListener { changedKey ->
            if (changedKey == key) state.value = prefs.getBoolean(key, default)
        }
        onDispose(unregister)
    }
    return state
}

@Composable
internal fun rememberStringPreference(
    prefs: FlutterPrefsRepository,
    key: String,
    default: String,
): MutableState<String> {
    val state = remember(prefs, key) { mutableStateOf(prefs.getString(key, default)) }
    DisposableEffect(prefs, key) {
        val unregister = prefs.addChangeListener { changedKey ->
            if (changedKey == key) state.value = prefs.getString(key, default)
        }
        onDispose(unregister)
    }
    return state
}

@Composable
internal fun rememberLongPreference(
    prefs: FlutterPrefsRepository,
    key: String,
    default: Long,
): MutableState<Long> {
    val state = remember(prefs, key) { mutableLongStateOf(prefs.getLong(key, default)) }
    DisposableEffect(prefs, key) {
        val unregister = prefs.addChangeListener { changedKey ->
            if (changedKey == key) state.value = prefs.getLong(key, default)
        }
        onDispose(unregister)
    }
    return state
}
