package io.github.hyperisland.compose.page.settings.extensions

internal const val KEY_RESUME_NOTIFICATION = "pref_resume_notification"
internal const val KEY_SETTINGS_HOME_ENTRY = "pref_settings_home_entry"
internal const val KEY_SETTINGS_HOME_ENTRY_POSITION = "pref_settings_home_entry_position"
internal const val KEY_SETTINGS_HOME_ENTRY_ICON_STYLE = "pref_settings_home_entry_icon_style"
internal const val KEY_BLUETOOTH_ISLAND = "pref_bluetooth_island"
internal const val KEY_BLUETOOTH_SHOW_DEVICE_NAME = "pref_bluetooth_island_show_device_name"
internal const val KEY_BLUETOOTH_DURATION = "pref_bluetooth_island_display_duration_seconds"
internal const val KEY_BLUETOOTH_OUTER_GLOW = "pref_bluetooth_island_outer_glow"
internal const val KEY_BLUETOOTH_OUTER_GLOW_COLOR = "pref_bluetooth_island_outer_glow_color"
internal const val KEY_BLUETOOTH_WHITELIST_ENABLED = "pref_bluetooth_island_whitelist_enabled"
internal const val KEY_BLUETOOTH_WHITELIST_ADDRESSES = "pref_bluetooth_island_whitelist_addresses"
internal const val KEY_SMOOTH_ISLAND = "pref_smooth_island"
internal const val KEY_SMOOTHING = "pref_smooth_island_smoothing"
internal const val KEY_SMALL_ICON_ADJUSTMENT = "pref_small_island_icon_adjustment"
internal const val KEY_SMALL_ICON_OPACITY = "pref_small_island_icon_opacity"
internal const val KEY_UNLOCK_ALL_FOCUS = "pref_unlock_all_focus"
internal const val KEY_UNLOCK_FOCUS_AUTH = "pref_unlock_focus_auth"
internal const val KEY_CHARGE_ISLAND = "pref_charge_island"
internal const val KEY_CHARGE_LEFT_MODE = "pref_charge_island_left_mode"
internal const val KEY_CHARGE_RIGHT_MODE = "pref_charge_island_right_mode"
internal const val KEY_CHARGE_DURATION_MODE = "pref_charge_island_duration_mode"
internal const val KEY_CHARGE_DURATION_SECONDS = "pref_charge_island_duration_seconds"
internal const val KEY_CHARGE_OUTER_GLOW = "pref_charge_island_outer_glow"
internal const val KEY_FACE_UNLOCK_ISLAND = "pref_face_unlock_island"
internal const val KEY_FACE_UNLOCK_FIRST_FLOAT = "pref_face_unlock_island_first_float"
internal const val KEY_FACE_UNLOCK_ANIMATION = "pref_face_unlock_island_animation_style"
internal const val KEY_FACE_UNLOCK_KEEP = "pref_face_unlock_island_keep_until_keyguard_hidden"
internal const val KEY_HIDE_FACE_UNLOCK_ICON = "pref_hide_lockscreen_face_unlock_icon"

internal const val MODE_DEFAULT = "default"
internal const val MODE_OUTLINE = "outline"
internal const val SETTINGS_POSITION_TOP = "top"
internal const val SETTINGS_POSITION_MIDDLE = "middle"
internal const val SETTINGS_POSITION_BOTTOM = "bottom"
internal const val MODE_POWER = "power"
internal const val MODE_VOLTAGE = "voltage"
internal const val MODE_CURRENT = "current"
internal const val MODE_LEVEL = "level"
internal const val MODE_TEMPERATURE = "temperature"
internal const val DURATION_CUSTOM = "custom"
internal const val DURATION_PERSISTENT = "persistent"
internal const val ANIMATION_LOCK = "lock"

internal const val DEFAULT_SMOOTHING = 0.8
internal const val DEFAULT_ICON_OPACITY = 0.5
internal const val DEFAULT_BLUETOOTH_DURATION = 2L
internal const val DEFAULT_CHARGE_DURATION = 10L

internal enum class HookExtensionDetail { Bluetooth, Charge, FaceUnlock }
