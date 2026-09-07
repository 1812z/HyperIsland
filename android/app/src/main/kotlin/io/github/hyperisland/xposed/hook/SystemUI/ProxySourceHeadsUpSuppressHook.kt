package io.github.hyperisland.xposed.hook.SystemUI

import android.content.Context
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field

/**
 * 阻止已经由 SystemUI 代发超级岛的源通知再次显示悬浮通知。
 *
 * 专用 extras 标记只会在通知/AI 通知模板成功代发且岛开启时写入，
 * 因此不会按包名、渠道或通知类型扩大拦截范围。全屏通知也始终交还系统处理。
 * OS4 使用 VisualInterruptionDecisionProviderImplInjector.shouldPeek，
 * OS3 则回退到 NotificationInterruptStateProviderImpl.checkHeadsUp。
 */
object ProxySourceHeadsUpSuppressHook : BaseHook() {

    private const val TAG = "HyperIsland[SourceHeadsUp]"
    private const val PREF_SUPPRESS_HEADS_UP = "pref_default_suppress_heads_up"
    private const val ENTRY_CLASS =
        "com.android.systemui.statusbar.notification.collection.NotificationEntry"
    private const val OS4_DECISION_INJECTOR_CLASS =
        "com.android.systemui.statusbar.notification.interruption." +
            "VisualInterruptionDecisionProviderImplInjector"
    private const val OS4_OLD_INJECTOR_CLASS =
        "com.android.systemui.statusbar.notification.interruption." +
            "NotificationInterruptStateProviderImplInjectorImpl"
    private const val OS3_STATE_PROVIDER_CLASS =
        "com.android.systemui.statusbar.notification.interruption." +
            "NotificationInterruptStateProviderImpl"

    @Volatile private var hooked = false
    @Volatile private var sbnField: Field? = null
    @Volatile private var os4OldInjectorField: Field? = null
    @Volatile private var os4ContextField: Field? = null

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (hooked) return

        val classLoader = param.defaultClassLoader
        val entryClass = try {
            classLoader.loadClass(ENTRY_CLASS).also {
                sbnField = findField(it, "mSbn")
            }
        } catch (e: Throwable) {
            logError(module, "NotificationEntry lookup failed: ${e.message}")
            return
        }

        val os4Failure = runCatching {
            hookOs4(module, classLoader, entryClass)
        }.exceptionOrNull()
        if (os4Failure == null) {
            hooked = true
            return
        }

        os4OldInjectorField = null
        os4ContextField = null
        val os3Failure = runCatching {
            hookOs3(module, classLoader, entryClass)
        }.exceptionOrNull()
        if (os3Failure == null) {
            hooked = true
            return
        }

        sbnField = null
        logError(
            module,
            "no compatible heads-up decision hook: " +
                "OS4=${os4Failure.message}; OS3=${os3Failure.message}",
        )
    }

    private fun hookOs4(
        module: XposedModule,
        classLoader: ClassLoader,
        entryClass: Class<*>,
    ) {
        val injectorClass = classLoader.loadClass(OS4_DECISION_INJECTOR_CLASS)
        val oldInjectorClass = classLoader.loadClass(OS4_OLD_INJECTOR_CLASS)
        val method = injectorClass.getDeclaredMethod("shouldPeek", entryClass)
        os4OldInjectorField = findField(injectorClass, "oldInjector")
        os4ContextField = findField(oldInjectorClass, "context")

        module.hook(method).intercept { chain ->
            val sbn = suppressibleSource(chain.args.firstOrNull())
            if (sbn != null && isOs4Interactive(chain.thisObject) == true) {
                logSuppressed(module, sbn, "OS4")
                false
            } else {
                chain.proceed()
            }
        }
        log(module, "hooked OS4 ${injectorClass.name}.shouldPeek(NotificationEntry)")
    }

    private fun hookOs3(
        module: XposedModule,
        classLoader: ClassLoader,
        entryClass: Class<*>,
    ) {
        val providerClass = classLoader.loadClass(OS3_STATE_PROVIDER_CLASS)
        val method = providerClass.getDeclaredMethod(
            "checkHeadsUp",
            entryClass,
            Boolean::class.javaPrimitiveType!!,
        )

        module.hook(method).intercept { chain ->
            val sbn = suppressibleSource(chain.args.firstOrNull())
            if (sbn != null) {
                logSuppressed(module, sbn, "OS3")
                false
            } else {
                chain.proceed()
            }
        }
        log(module, "hooked OS3 ${providerClass.name}.checkHeadsUp(NotificationEntry, boolean)")
    }

    private fun suppressibleSource(entry: Any?): StatusBarNotification? {
        if (!ConfigManager.getBoolean(PREF_SUPPRESS_HEADS_UP, true)) return null
        val sbn = runCatching {
            sbnField?.get(entry) as? StatusBarNotification
        }.getOrNull() ?: return null
        val notification = sbn.notification ?: return null
        if (sbn.packageName == "com.android.systemui") return null
        if (notification.fullScreenIntent != null) return null
        if (!notification.extras.getBoolean(
                IslandDispatchContract.EXTRA_SUPPRESS_SOURCE_HEADS_UP,
                false,
            )
        ) return null
        return sbn
    }

    /** 只处理亮屏 heads-up；息屏 PULSE/AOD 决策保持系统原样。 */
    private fun isOs4Interactive(injector: Any?): Boolean? = runCatching {
        val oldInjector = os4OldInjectorField?.get(injector) ?: return@runCatching null
        val context = os4ContextField?.get(oldInjector) as? Context ?: return@runCatching null
        context.getSystemService(PowerManager::class.java)?.isInteractive
    }.getOrNull()

    private fun logSuppressed(
        module: XposedModule,
        sbn: StatusBarNotification,
        platform: String,
    ) {
        log(
            module,
            "suppressed source heads-up: platform=$platform pkg=${sbn.packageName} " +
                "id=${sbn.id} key=${sbn.key}",
        )
    }

    private fun findField(clazz: Class<*>, name: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("$name not found in ${clazz.name} hierarchy")
    }
}
