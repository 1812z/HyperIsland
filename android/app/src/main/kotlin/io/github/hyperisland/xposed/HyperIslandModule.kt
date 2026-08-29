package io.github.hyperisland.xposed

import io.github.hyperisland.xposed.hook.SystemUI.BigIslandMinWidthHook
import io.github.hyperisland.xposed.hook.SystemUI.IslandIconHook
import io.github.hyperisland.xposed.hook.SystemUI.DynamicIslandVisibilityHook
import io.github.hyperisland.xposed.hook.SystemUI.IslandTopOffsetHook
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.IslandBlurHook
import io.github.hyperisland.xposed.hook.SystemUI.IslandOutlineHook
import io.github.hyperisland.xposed.hook.SystemUI.IslandTextSizeHook
import io.github.hyperisland.xposed.hook.SystemUI.IslandTransitionVisualHook
import io.github.hyperisland.xposed.hook.SystemUI.IslandSwipeActionHook
import io.github.hyperisland.xposed.hook.SystemUI.extensions.SmallIslandIconHook
import io.github.hyperisland.xposed.hook.SystemUI.extensions.SmoothIslandHook
import io.github.hyperisland.xposed.hook.ActiveIslandDismissHook
import io.github.hyperisland.xposed.hook.SystemUI.extensions.BluetoothIslandHook
import io.github.hyperisland.xposed.hook.SystemUI.extensions.ChargeIslandHook
import io.github.hyperisland.xposed.hook.DownloadHook
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.hook.FocusNotificationTextColorHook
import io.github.hyperisland.xposed.hook.SystemUI.extensions.FaceUnlockStateHook
import io.github.hyperisland.xposed.hook.SystemUI.extensions.LockscreenFaceUnlockUiHook
import io.github.hyperisland.xposed.hook.SystemUI.extensions.KeyguardUnlockStateHook
import io.github.hyperisland.xposed.hook.MediaNotificationTextColorHook
import io.github.hyperisland.xposed.hook.SystemUI.GenericProgressHook
import io.github.hyperisland.xposed.hook.IslandBackgroundHook
import io.github.hyperisland.xposed.hook.IslandDimenHook
import io.github.hyperisland.xposed.hook.IslandDispatcherHook
import io.github.hyperisland.xposed.hook.IslandOuterGlowHook
import io.github.hyperisland.xposed.hook.IslandTextColorHook
import io.github.hyperisland.xposed.hook.KeepIslandHook
import io.github.hyperisland.xposed.hook.MarqueeHook
import io.github.hyperisland.xposed.hook.SettingsHomeEntryHook
import io.github.hyperisland.xposed.hook.TextShadeHook
import io.github.hyperisland.xposed.hook.TempHiddenBehaviorHook
import io.github.hyperisland.xposed.hook.TimerTextColorHook
import io.github.hyperisland.xposed.hook.ToastUiInterceptHook
import io.github.hyperisland.xposed.hook.SystemUI.extensions.UnlockAllFocusHook
import io.github.hyperisland.xposed.hook.UnlockFocusAuthHook
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

/**
 * 模块主入口，继承 XposedModule。
 * 框架在各目标进程加载时回调 [onPackageLoaded]，由此分发到各子 Hook。
 */
class HyperIslandModule : XposedModule() {

    private var configManagerInitialized = false

    override fun onPackageLoaded(param: PackageLoadedParam) {
        initializeConfigManager()
        log("onPackageLoaded: pkg=${param.packageName}")
        
        when (param.packageName) {
            "com.android.systemui" -> {
                if (ConfigManager.getBoolean("pref_face_unlock_island", false)) {
                    FaceUnlockStateHook.init(this, param)
                    KeyguardUnlockStateHook.init(this, param)
                }
                if (ConfigManager.getBoolean("pref_hide_lockscreen_face_unlock_icon", false)) {
                    LockscreenFaceUnlockUiHook.init(this, param)
                }
                IslandDispatcherHook.init(this, param)
                GenericProgressHook.init(this, param)
                ActiveIslandDismissHook.init(this, param)
                MarqueeHook.init(this, param)
                IslandTextSizeHook.init(this, param)
                BigIslandMinWidthHook.init(this, param)
                IslandIconHook.init(this, param)
                UnlockAllFocusHook.init(this, param)
                FocusNotifStatusBarIconHook.init(this, param)
                IslandOuterGlowHook.init(this, param)
                IslandBackgroundHook.init(this, param)
                IslandBlurHook.init(this, param)
                IslandTransitionVisualHook.init(this, param)
                IslandTextColorHook.init(this, param)
                TimerTextColorHook.init(this, param)
                FocusNotificationTextColorHook.init(this, param)
                MediaNotificationTextColorHook.init(this, param)
                TextShadeHook.init(this, param)
                IslandDimenHook.init(this, param)
                IslandTopOffsetHook.init(this, param)
                IslandOutlineHook.init(this, param)
                if (ConfigManager.getBoolean("pref_temp_hide_behavior_enabled", false)) {
                    TempHiddenBehaviorHook.init(this, param)
                }
                if (ConfigManager.getBoolean("pref_smooth_island", false)) {
                    SmoothIslandHook.init(this, param)
                }
                if (ConfigManager.getBoolean("pref_small_island_icon_adjustment", false)) {
                    SmallIslandIconHook.init(this, param)
                }
                ToastUiInterceptHook.init(this, param)
                DynamicIslandVisibilityHook.init(this, param)
                IslandSwipeActionHook.init(this, param)
                KeepIslandHook.init(this, param)
                if (ConfigManager.getBoolean("pref_bluetooth_island", false)) {
                    BluetoothIslandHook.init(this, param)
                }
                if (ConfigManager.getBoolean("pref_charge_island", false)) {
                    ChargeIslandHook.init(this, param)
                }
            }

            "com.android.providers.downloads",
            "com.xiaomi.android.app.downloadmanager" ->
                DownloadHook.init(this, param)

            "com.xiaomi.xmsf" ->
                UnlockFocusAuthHook.init(this, param)

            "com.android.settings" ->
                if (ConfigManager.getBoolean("pref_settings_home_entry", true)) {
                    SettingsHomeEntryHook.init(this, param)
                }

        }
    }

    private fun initializeConfigManager() {
        if (!configManagerInitialized) {
            ConfigManager.init(this)
            configManagerInitialized = true
        }
    }
}
