package io.github.hyperisland.xposed.hook.SystemUI

import io.github.hyperisland.xposed.diagnostics.profiledHook

import android.content.Context
import android.widget.Toast
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

/**
 * 屏蔽「已通过蓝牙设备解锁」Toast。
 *
 * 来源（jadx 核实，两处调用点，同一个字符串资源）：
 * 1. `com.android.keyguard.MiuiBleUnlockHelper#tryUnlockByBle()`
 * 2. `com.android.keyguard.KeyguardSecurityContainerController#showNextSecurityScreenOrFinish()`
 * 两处都是 `Toast.makeText(context, R.string.miui_keyguard_ble_unlock_succeed_msg, 0).show()`。
 * `tryUnlockByBle` 里还有 `notifyKeyguardAuthenticated(false)` 真正解锁，因此不能整体吞掉方法调用，
 * 只屏蔽 Toast 本身；又因为存在第二处调用点，判据不能绑定方法名，只能按资源定位。
 *
 * 判据：`Toast.makeText(Context, int, int)` 的 resId 经 `Resources#getResourceName` 解析后
 * 比对 `:string/miui_keyguard_ble_unlock_succeed_msg`，与系统语言无关；首次命中后缓存 resId。
 *
 * 屏蔽方式：命中的 Toast 实例登记到弱集合，`Toast#show()` 命中即跳过原方法 ——
 * 不猜 Toast 内部字段，也不会误伤 SystemUI 自己的其他 Toast。
 *
 * 开关 `pref_disable_bluetooth_unlock_toast` 关闭时本 Hook 完全不 init（见 HyperIslandModule），
 * 因此一个方法都不注入；改动开关需重启系统界面。
 */
object BluetoothUnlockToastHook : BaseHook() {

    private const val TAG = "HyperIsland[BleUnlockToast]"

    /** SystemUI 资源名后缀；各语言共用同一份资源，因此不依赖文案。 */
    private const val RES_SUFFIX = ":string/miui_keyguard_ble_unlock_succeed_msg"

    /** 待屏蔽 Toast 的登记上限，防止异常场景下无限堆积。 */
    private const val MAX_PENDING = 32

    /**
     * 弱集合 + 命中即删：Toast 在 makeText 与 show 之间由调用方强引用持有，
     * 不会中途被回收；万一被回收也只是漏拦一次，不会泄漏。
     */
    private val pending = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>()),
    )

    @Volatile private var hooked = false
    @Volatile private var bleUnlockResId = 0

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (hooked) return
        val makeTextCount = hookMakeText(module)
        if (makeTextCount == 0 || !hookShow(module)) {
            logWarn(module, "install aborted: makeText=$makeTextCount")
            return
        }
        hooked = true
        log(module) { "hooked Toast.makeText(int) x$makeTextCount + Toast#show" }
    }

    // ── Hook 目标 ──────────────────────────────────────────────────────────────

    private fun hookMakeText(module: XposedModule): Int {
        var count = 0
        Toast::class.java.declaredMethods
            .filter { it.name == "makeText" && Modifier.isStatic(it.modifiers) && isResIdOverload(it) }
            .forEach { method ->
                runCatching {
                    module.profiledHook("BluetoothUnlockToastHook", method).intercept { chain ->
                        val result = chain.proceed()
                        runCatching {
                            val context = chain.args.getOrNull(0) as? Context
                            val resId = chain.args.getOrNull(1) as? Int ?: 0
                            if (result != null && context != null &&
                                isBleUnlockMessage(context, resId)
                            ) {
                                markForBlocking(result)
                                log(module) { "detected bluetooth unlock toast" }
                            }
                        }.onFailure {
                            logError(module, "inspect makeText failed: ${it.message}")
                        }
                        result
                    }
                    count++
                }.onFailure {
                    logError(module, "hook makeText failed: ${it.message}")
                }
            }
        return count
    }

    /** 只认 `makeText(Context, int, int)`；`makeText(Context, CharSequence, int)` 的最后一位也是 int，必须排除。 */
    private fun isResIdOverload(method: Method): Boolean {
        val types = method.parameterTypes
        return types.size == 3 &&
            Context::class.java.isAssignableFrom(types[0]) &&
            types[1] == Int::class.javaPrimitiveType
    }

    private fun hookShow(module: XposedModule): Boolean {
        val show = runCatching { Toast::class.java.getDeclaredMethod("show") }.getOrNull()
        if (show == null) {
            logWarn(module, "Toast#show not found")
            return false
        }
        return runCatching {
            module.profiledHook("BluetoothUnlockToastHook", show).intercept { chain ->
                val toast = chain.thisObject
                if (toast != null && pending.remove(toast)) {
                    log(module) { "suppressed bluetooth unlock toast" }
                    null
                } else {
                    chain.proceed()
                }
            }
            true
        }.getOrElse {
            logError(module, "hook Toast#show failed: ${it.message}")
            false
        }
    }

    // ── 判据 ──────────────────────────────────────────────────────────────────

    private fun markForBlocking(toast: Any) {
        if (pending.size >= MAX_PENDING) pending.clear()
        pending.add(toast)
    }

    private fun isBleUnlockMessage(context: Context, resId: Int): Boolean {
        if (resId == 0) return false
        val cached = bleUnlockResId
        if (cached != 0) return resId == cached
        val name = runCatching { context.resources.getResourceName(resId) }.getOrNull()
            ?: return false
        if (!name.endsWith(RES_SUFFIX)) return false
        bleUnlockResId = resId
        return true
    }
}
