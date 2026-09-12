package io.github.hyperisland.xposed.hook.SystemUI

import android.content.Context
import android.net.wifi.WifiManager
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import android.os.Handler
import android.os.Looper
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections

/**
 * WiFi 磁贴“仅断开”增强。
 *
 * 原生 [MiuiWifiTile] 点击会调用 `networkController.setWifiEnabled(!value)` 直接开关 WiFi 射频。
 * 蓝牙磁贴通过 MIUI 的 bluetooth restrict 机制只断开连接，并把状态渲染成灰色受限态；
 * 关键点是它的状态对象是 `QSTile.RestrictState`，`isRestricted=true` 时控制中心绘制灰底。
 * WiFi 的 `newTileState()` 返回普通 `BooleanState`，因此就算把 state 改成 1 也只会是普通灰色关闭，
 * 不会呈现蓝牙那种“受限灰”。
 *
 * 本 Hook：
 * 1. Hook `newTileState()` 换成 `RestrictState`；
 * 2. Hook `handleClick()`：点击时保留 WiFi 射频，逐个 `disableNetwork` + `disconnect`，点击第二次恢复；
 * 3. Hook `handleUpdateState()`：断开态强制 `state=1`、`isRestricted=true`、`activeBgColor=3`、关闭图标。
 *
 * 参考磁贴本体：com.android.systemui.qs.tiles.MiuiWifiTile
 */
object WifiTileDisconnectHook : BaseHook() {

    private const val TAG = "HyperIsland[WifiTileDisconnect]"
    private const val PREF_ENABLED = "pref_wifi_tile_disconnect_only"

    /** MiuiWifiTile 在 jadx 中显示为 p055qs，但真实 dex 类名为 qs。按优先级尝试。 */
    private val TILE_CLASS_CANDIDATES = listOf(
        "com.android.systemui.qs.tiles.MiuiWifiTile",
        "com.android.systemui.p055qs.tiles.MiuiWifiTile",
    )
    private const val STATE_INACTIVE = 1
    /** 参考 MiuiBluetoothTile.handleUpdateState：restricted 用 activeBgColor=3 渲染灰底。 */
    private const val ACTIVE_BG_COLOR_RESTRICTED = 3
    private const val WIFI_CONFIG_STATUS_ENABLED = 1
    private const val WIFI_OFF_ICON_NAME = "ic_qs_wifi_off"
    private const val SYSTEMUI_PACKAGE = "com.android.systemui"

    @Volatile private var hooked = false
    @Volatile private var contextField: Field? = null
    @Volatile private var networkControllerField: Field? = null
    @Volatile private var maybeLoadIconMethod: Method? = null
    @Volatile private var tileLabelMethod: Method? = null
    @Volatile private var restrictStateClass: Class<*>? = null
    @Volatile private var restrictStateCtor: java.lang.reflect.Constructor<*>? = null

    private val fieldCache = Collections.synchronizedMap(mutableMapOf<String, Field?>())

    /** 是否处于“仅断开”状态：射频仍开，但已断开且禁用自动连接。 */
    @Volatile private var softDisabled = false
    /** 是否已经观察到链路真正断开；用于精准识别“用户手动重连”，不依赖任何时长判断。 */
    @Volatile private var sawDisconnected = false
    /** Wi-Fi 被外部关闭时，保留断开会话中的网络，待射频恢复后自动连接。 */
    @Volatile private var pendingRadioRestore = false
    @Volatile private var restoreScheduled = false
    private val disabledNetIds = Collections.synchronizedSet(mutableSetOf<Int>())
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (hooked) return
        val classLoader = param.defaultClassLoader

        val tileClass = TILE_CLASS_CANDIDATES.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name, false, classLoader) }.getOrNull()
        }
        if (tileClass == null) {
            logError(module, "MiuiWifiTile not found, candidates=$TILE_CLASS_CANDIDATES")
            return
        }

        contextField = findField(tileClass, "mContext")
        networkControllerField = findField(tileClass, "networkController")
        maybeLoadIconMethod = runCatching {
            tileClass.getMethod("maybeLoadResourceIcon", Int::class.javaPrimitiveType)
        }.getOrNull()
        tileLabelMethod = runCatching { tileClass.getMethod("getTileLabel") }.getOrNull()

        val newStateMethod = tileClass.declaredMethods.firstOrNull {
            it.name == "newTileState" && it.parameterCount == 0
        }
        val clickMethod = tileClass.declaredMethods.firstOrNull {
            it.name == "handleClick" && it.parameterCount == 1
        }
        val updateMethod = tileClass.declaredMethods.firstOrNull {
            it.name == "handleUpdateState" && it.parameterCount == 2
        }
        if (newStateMethod == null || clickMethod == null || updateMethod == null) {
            logError(
                module,
                "MiuiWifiTile methods missing: new=$newStateMethod " +
                    "click=$clickMethod update=$updateMethod",
            )
            return
        }

        // Derive RestrictState from the state type used by this SystemUI build instead
        // of hard-coding an obfuscated plugin package name.
        restrictStateClass = findRestrictStateClass(classLoader, updateMethod.parameterTypes[0])

        restrictStateCtor = restrictStateClass?.declaredConstructors?.firstOrNull()
            ?.apply { isAccessible = true }

        if (restrictStateClass == null || restrictStateCtor == null) {
            logWarn(module, "RestrictState unavailable for ${updateMethod.parameterTypes[0].name}")
        }

        if (restrictStateClass != null) {
            module.hook(newStateMethod).intercept { chain ->
                runCatching { restrictStateCtor?.newInstance() }.getOrNull() ?: chain.proceed()
            }
        }

        module.hook(clickMethod).intercept { chain ->
            val handled = runCatching {
                handleWifiClick(module, chain.thisObject)
            }.getOrElse {
                logError(module, "handleClick failed: ${it.message}")
                false
            }
            if (handled) null else chain.proceed()
        }

        module.hook(updateMethod).intercept { chain ->
            val result = chain.proceed()
            runCatching {
                overrideState(chain.thisObject, chain.args.getOrNull(0))
            }.onFailure {
                logError(module, "overrideState failed: ${it.message}")
            }
            result
        }

        hooked = true
        log(module, "hooked ${tileClass.name} (wifi disconnect-only, restrictState=${restrictStateClass != null})")
    }

    /**
     * @return true 表示已自行处理并跳过原生关射频逻辑；false 交还原生（例如 WiFi 处于关闭状态需要正常开启）。
     */
    private fun handleWifiClick(module: XposedModule, tile: Any?): Boolean {
        if (tile == null) return false

        val prefEnabled = ConfigManager.getBoolean(PREF_ENABLED, false)
        val ctx = tileContext(tile) ?: return false
        val wifiManager = ctx.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false

        val radioOn = runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)
        logWarn(module, "click: pref=$prefEnabled radioOn=$radioOn softDisabled=$softDisabled")

        if (!prefEnabled) return false

        // 射频已完全关闭：通过 SystemUI 的 NetworkController 开启，不能直接调用
        // WifiManager.setWifiEnabled。后者绕过了 SystemUI 的异步控制链，在设置中关闭
        // Wi-Fi 后可能返回失败且磁贴不会收到正确的状态更新。
        if (!radioOn) {
            val ids = synchronized(disabledNetIds) { disabledNetIds.toList() }
            pendingRadioRestore = ids.isNotEmpty()
            softDisabled = false
            sawDisconnected = false
            val enabled = enableWifiThroughSystemUi(tile, wifiManager)
            logWarn(module, "wifi radio re-enabled from off state: requested=$enabled networks=${ids.size}")
            scheduleRadioRestore(ctx, module)
            refreshTile(module, tile)
            return true
        }

        // 点击瞬间就翻转本地的“仅断开”意图，并立刻让磁贴刷新，不依赖任何时长判断。
        if (!softDisabled) {
            softDisabled = true
            sawDisconnected = false
            pendingRadioRestore = false
            startSoftDisconnect(wifiManager)
            log(module, "wifi soft-disconnect: disabled=${disabledNetIds.size} networks")
        } else {
            softDisabled = false
            sawDisconnected = false
            restoreConnections(ctx)
            log(module, "wifi soft-disconnect restored")
        }
        refreshTile(module, tile)
        return true
    }

    private fun startSoftDisconnect(wifiManager: WifiManager) {
        disabledNetIds.clear()
        pendingRadioRestore = false

        runCatching { wifiManager.configuredNetworks }.getOrNull()?.forEach { config ->
            val netId = config.networkId
            if (config.status == WIFI_CONFIG_STATUS_ENABLED && netId >= 0 &&
                runCatching { wifiManager.disableNetwork(netId) }.getOrDefault(false)
            ) {
                disabledNetIds.add(netId)
            }
        }

        val currentNetId = runCatching { wifiManager.connectionInfo?.networkId ?: -1 }
            .getOrDefault(-1)
        if (currentNetId >= 0 && disabledNetIds.add(currentNetId)) {
            runCatching { wifiManager.disableNetwork(currentNetId) }
        }
        runCatching { wifiManager.disconnect() }
    }

    private fun restoreConnections(ctx: Context) {
        val ids = synchronized(disabledNetIds) { disabledNetIds.toList() }
        disabledNetIds.clear()
        val wifiManager = ctx.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        ids.forEach { netId ->
            runCatching { wifiManager.enableNetwork(netId, true) }
        }
        runCatching { wifiManager.reconnect() }
    }

    /** 仅恢复已保存网络的自动连接能力，不主动触发连接。 */
    private fun reenableDisabledNetworks(ctx: Context) {
        val ids = synchronized(disabledNetIds) { disabledNetIds.toList() }
        disabledNetIds.clear()
        val wifiManager = ctx.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        ids.forEach { netId ->
            runCatching { wifiManager.enableNetwork(netId, false) }
        }
    }

    private fun resetSession(clearNetworks: Boolean = true) {
        softDisabled = false
        sawDisconnected = false
        if (clearNetworks) {
            pendingRadioRestore = false
            disabledNetIds.clear()
        }
    }

    /**
     * 在系统刷新出的状态上覆盖为“蓝牙式受限灰”。
     * 只要处于“仅断开”状态就直接覆盖，不依赖 isConnected 的异步回调。
     */
    private fun overrideState(tile: Any?, state: Any?) {
        if (state == null || tile == null) return
        if (restrictStateClass == null || !restrictStateClass!!.isInstance(state)) return

        val prefEnabled = ConfigManager.getBoolean(PREF_ENABLED, false)

        val ctx = tileContext(tile) ?: return
        val wifiManager = ctx.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager

        val radioOn = wifiManager == null ||
            runCatching { wifiManager.isWifiEnabled }.getOrDefault(true)
        if (!radioOn) {
            // 射频被系统/用户关闭时只结束灰色显示，保留网络 ID，待射频恢复后重连。
            if (disabledNetIds.isNotEmpty()) pendingRadioRestore = true
            resetSession(clearNetworks = false)
        } else if (pendingRadioRestore) {
            scheduleRadioRestore(ctx, null)
        }

        // 精准识别用户手动重连：先真实观察到断开，之后又观察到连接，则视为用户操作，退出“仅断开”。
        val connected = readBoolean(tile, "isConnected")
        if (softDisabled && !connected) {
            sawDisconnected = true
        } else if (softDisabled && connected && sawDisconnected) {
            // 用户已自行连上网络：恢复其余网络的可自动连接能力，但不强制重连
            reenableDisabledNetworks(ctx)
            softDisabled = false
            sawDisconnected = false
        }

        val restricted = prefEnabled && softDisabled
        // 每次刷新都显式设置，避免受限标记残留导致恢复后仍显示灰色
        setBooleanField(state, "isRestricted", restricted)
        if (!restricted) {
            setIntField(state, "activeBgColor", 0)
            return
        }

        setIntField(state, "state", STATE_INACTIVE)
        setBooleanField(state, "value", false)
        setBooleanField(state, "isTransient", false)
        setBooleanField(state, "dualTarget", true)
        // 关键：模仿蓝牙 restricted 状态，让控制中心绘制灰底
        setIntField(state, "activeBgColor", ACTIVE_BG_COLOR_RESTRICTED)

        buildOffIcon(ctx, tile)?.let { setObjectField(state, "icon", it) }
        safeTileLabel(tile)?.let { setObjectField(state, "label", it) }
    }

    private fun buildOffIcon(ctx: Context, tile: Any): Any? {
        val resId = runCatching {
            ctx.resources.getIdentifier(WIFI_OFF_ICON_NAME, "drawable", SYSTEMUI_PACKAGE)
                .takeIf { it != 0 }
                ?: ctx.resources.getIdentifier(WIFI_OFF_ICON_NAME, "drawable", ctx.packageName)
        }.getOrDefault(0)
        if (resId == 0) return null
        return runCatching { maybeLoadIconMethod?.invoke(tile, resId) }.getOrNull()
    }

    private fun safeTileLabel(tile: Any): Any? =
        runCatching { tileLabelMethod?.invoke(tile) }.getOrNull()

    private fun refreshTile(module: XposedModule, tile: Any) {
        runCatching {
            tile.javaClass.getMethod("refreshState", Any::class.java).invoke(tile, null)
        }.onFailure {
            logError(module, "refreshState failed: ${it.message}")
        }
    }

    private fun tileContext(tile: Any): Context? =
        runCatching { contextField?.get(tile) as? Context }.getOrNull()

    private fun scheduleRadioRestore(ctx: Context, module: XposedModule?) {
        if (!pendingRadioRestore || restoreScheduled) return
        restoreScheduled = true
        val appContext = ctx.applicationContext
        mainHandler.postDelayed(object : Runnable {
            private var attempts = 0

            override fun run() {
                val wifiManager = appContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifiManager == null) {
                    restoreScheduled = false
                    return
                }
                if (!runCatching { wifiManager.isWifiEnabled }.getOrDefault(false)) {
                    if (++attempts < 10) {
                        mainHandler.postDelayed(this, 500L)
                    } else {
                        restoreScheduled = false
                    }
                    return
                }

                val ids = synchronized(disabledNetIds) { disabledNetIds.toList() }
                ids.forEach { netId ->
                    runCatching { wifiManager.enableNetwork(netId, true) }
                }
                runCatching { wifiManager.reconnect() }

                val connected = runCatching {
                    (wifiManager.connectionInfo?.networkId ?: -1) >= 0
                }.getOrDefault(false)
                if (!connected && ++attempts < 10) {
                    // Wi-Fi being enabled does not mean the supplicant is ready yet.
                    mainHandler.postDelayed(this, 1000L)
                    return
                }

                if (connected) {
                    disabledNetIds.clear()
                    pendingRadioRestore = false
                }
                restoreScheduled = false
                module?.let {
                    logWarn(
                        it,
                        "wifi radio restored, reconnect ${if (connected) "succeeded" else "timed out"}: " +
                            "networks=${ids.size}",
                    )
                }
            }
        }, 500L)
    }

    private fun findRestrictStateClass(classLoader: ClassLoader, stateClass: Class<*>): Class<*>? {
        val stateName = stateClass.name
        val stateSuffix = "\$State"
        if (!stateName.endsWith(stateSuffix)) return null
        return runCatching {
            classLoader.loadClass(stateName.removeSuffix(stateSuffix) + "\$RestrictState")
        }.getOrNull()
    }

    /** Use the same controller as the native tile so state and async callbacks stay in sync. */
    private fun enableWifiThroughSystemUi(tile: Any, wifiManager: WifiManager): Boolean {
        val controller = runCatching { networkControllerField?.get(tile) }.getOrNull()
        if (controller != null) {
            val method = runCatching {
                controller.javaClass.getMethod("setWifiEnabled", Boolean::class.javaPrimitiveType)
            }.getOrNull()
            if (method != null && runCatching { method.invoke(controller, true) }.isSuccess) {
                return true
            }
        }

        // Compatibility fallback for a SystemUI variant without the exposed controller field.
        return runCatching { wifiManager.setWifiEnabled(true) }.getOrDefault(false)
    }

    private fun readBoolean(target: Any, name: String): Boolean =
        runCatching { fieldOf(target.javaClass, name)?.getBoolean(target) ?: false }
            .getOrDefault(false)

    private fun setIntField(target: Any, name: String, value: Int) {
        runCatching { fieldOf(target.javaClass, name)?.setInt(target, value) }
    }

    private fun setBooleanField(target: Any, name: String, value: Boolean) {
        runCatching { fieldOf(target.javaClass, name)?.setBoolean(target, value) }
    }

    private fun setObjectField(target: Any, name: String, value: Any?) {
        runCatching { fieldOf(target.javaClass, name)?.set(target, value) }
    }

    private fun fieldOf(clazz: Class<*>, name: String): Field? {
        val key = "${clazz.name}#$name"
        return fieldCache[key] ?: findField(clazz, name)?.also { fieldCache[key] = it }
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
