package io.github.hyperisland.compose.page.settings.extensions

internal const val KEY_RESUME_NOTIFICATION = "pref_resume_notification"
internal const val KEY_DOWNLOAD_SHOW_TASK_ICON = "pref_download_show_task_icon"
internal const val KEY_CLIPBOARD_TOAST_CONVERSION = "pref_clipboard_toast_conversion"
internal const val KEY_CLIPBOARD_OPTIMIZE_ISLAND_STYLE = "pref_clipboard_optimize_island_style"
internal const val KEY_SCREEN_RECORDER_ISLAND = "pref_screen_recorder_island"
internal const val KEY_SCREEN_RECORDER_IMMEDIATE_START = "pref_screen_recorder_immediate_start"
internal const val KEY_SCREEN_RECORDER_ICON_STYLE = "pref_screen_recorder_icon_style"
internal const val KEY_SETTINGS_HOME_ENTRY = "pref_settings_home_entry"
internal const val KEY_SETTINGS_HOME_ENTRY_POSITION = "pref_settings_home_entry_position"
internal const val KEY_SETTINGS_HOME_ENTRY_SAME_GROUP = "pref_settings_home_entry_same_group"
internal const val KEY_SETTINGS_HOME_ENTRY_ICON_STYLE = "pref_settings_home_entry_icon_style"
internal const val KEY_BLUETOOTH_ISLAND = "pref_bluetooth_island"
internal const val KEY_BLUETOOTH_SHOW_DEVICE_NAME = "pref_bluetooth_island_show_device_name"
internal const val KEY_BLUETOOTH_DURATION = "pref_bluetooth_island_display_duration_seconds"
internal const val KEY_BLUETOOTH_OUTER_GLOW = "pref_bluetooth_island_outer_glow"
internal const val KEY_BLUETOOTH_OUTER_GLOW_COLOR = "pref_bluetooth_island_outer_glow_color"
internal const val KEY_BLUETOOTH_WHITELIST_ENABLED = "pref_bluetooth_island_whitelist_enabled"
internal const val KEY_BLUETOOTH_WHITELIST_ADDRESSES = "pref_bluetooth_island_whitelist_addresses"
internal const val KEY_HEART_RATE_ISLAND = "pref_heart_rate_island"
internal const val KEY_HEART_RATE_READ_MODE = "pref_heart_rate_island_read_mode"
internal const val KEY_HEART_RATE_DEVICE_ADDRESS = "pref_heart_rate_island_device_address"
internal const val KEY_HEART_RATE_DEVICE_NAME = "pref_heart_rate_island_device_name"
internal const val KEY_HEART_RATE_SHOW_UNIT = "pref_heart_rate_island_show_unit"
internal const val KEY_SMOOTH_ISLAND = "pref_smooth_island"
internal const val KEY_SMOOTHING = "pref_smooth_island_smoothing"
internal const val KEY_SMALL_ICON_ADJUSTMENT = "pref_small_island_icon_adjustment"
internal const val KEY_SMALL_ICON_OPACITY = "pref_small_island_icon_opacity"
internal const val KEY_UNLOCK_ALL_FOCUS = "pref_unlock_all_focus"
internal const val KEY_UNLOCK_FOCUS_AUTH = "pref_unlock_focus_auth"
internal const val KEY_CHARGE_ISLAND = "pref_charge_island"
internal const val KEY_CHARGE_BLOCKED = "pref_charge_island_blocked"
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
internal const val KEY_LOCKSCREEN_DEVICE_CENTER = "pref_lockscreen_device_center"
internal const val KEY_WIFI_TILE_DISCONNECT = "pref_wifi_tile_disconnect_only"

internal const val MODE_DEFAULT = "default"
internal const val MODE_OUTLINE = "outline"
internal const val SCREEN_RECORDER_ICON_VOICE_RECORDER = "voice_recorder"
internal const val SCREEN_RECORDER_ICON_SCREEN_RECORDER = "screen_recorder"
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
internal const val HEART_RATE_READ_MODE_BROADCAST = "heart_rate_broadcast"

internal const val DEFAULT_SMOOTHING = 0.8
internal const val DEFAULT_ICON_OPACITY = 0.5
internal const val DEFAULT_BLUETOOTH_DURATION = 2L
internal const val DEFAULT_CHARGE_DURATION = 10L

internal const val PKG_SYSTEM_UI = "com.android.systemui"
internal const val PKG_MILINK = "com.milink.service"
internal const val PKG_SETTINGS = "com.android.settings"
internal const val PKG_SECURITY_CENTER = "com.miui.securitycenter"
internal const val PKG_XMSF = "com.xiaomi.xmsf"
internal const val PKG_SCREEN_RECORDER = "com.miui.screenrecorder"
internal const val PKG_DOWNLOAD_MANAGER = "com.android.providers.downloads"

internal val RESTART_SCOPE_SYSTEM_UI = setOf(PKG_SYSTEM_UI, PKG_MILINK)
internal val RESTART_SCOPE_ISLAND = setOf(PKG_SYSTEM_UI)
internal val RESTART_SCOPE_SETTINGS = setOf(PKG_SETTINGS)
internal val RESTART_SCOPE_SECURITY_CENTER = setOf(PKG_SECURITY_CENTER)
internal val RESTART_SCOPE_XMSF = setOf(PKG_XMSF)
internal val RESTART_SCOPE_SCREEN_RECORDER = setOf(PKG_SCREEN_RECORDER)
internal val RESTART_SCOPE_DOWNLOAD_MANAGER = setOf(PKG_DOWNLOAD_MANAGER)

internal enum class HookExtensionDetail {
    SystemUi,
    SystemSettings,
    SecurityCenter,
    Xmsf,
    ScreenRecorder,
    DownloadManager,
    Bluetooth,
    HeartRate,
    Charge,
    FaceUnlock,
}
