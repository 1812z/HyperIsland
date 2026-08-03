package io.github.hyperisland.xposed.hook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

object SettingsHomeEntryHook : BaseHook() {

    private const val MODULE_PACKAGE = "io.github.hyperisland"
    private const val MAIN_ACTIVITY = "io.github.hyperisland.MainActivity"
    private const val HEADER_ID = 0x68797065726C // "hyperl"
    private const val ICON_FAKE_RES_ID = 0x7e00f001
    private var iconHookInstalled = false
    private val moduleIconStates = mutableMapOf<String, Drawable.ConstantState>()

    override fun getTag(): String = "HyperIsland[SettingsHomeEntry]"

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.settings") return

        val miuiSettings = try {
            param.defaultClassLoader.loadClass("com.android.settings.MiuiSettings")
        } catch (e: Throwable) {
            logError(module, "MiuiSettings not found: ${e.message}")
            return
        }

        val method = try {
            HookUtils.findMethod(miuiSettings, "updateHeaderList", List::class.java)
        } catch (e: Throwable) {
            logError(module, "updateHeaderList not found: ${e.message}")
            return
        }

        installIconHook(module)

        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            val headers = chain.args.getOrNull(0) as? MutableList<Any> ?: return@intercept result
            val activity = chain.thisObject as? Activity ?: return@intercept result

            try {
                insertEntry(module, activity, param.defaultClassLoader, headers)
            } catch (e: Throwable) {
                logError(module, "insert entry failed: ${e.message}")
            }

            result
        }

        log(module, "hooked MiuiSettings.updateHeaderList")
    }

    private fun insertEntry(
        module: XposedModule,
        activity: Activity,
        classLoader: ClassLoader,
        headers: MutableList<Any>
    ) {
        if (headers.any { readLongField(it, "id") == HEADER_ID }) return

        val context = activity.baseContext ?: activity
        val headerClass = try {
            classLoader.loadClass("com.android.settingslib.miuisettings.preference.PreferenceActivity\$Header")
        } catch (e: Throwable) {
            logError(module, "Header class not found: ${e.message}")
            return
        }

        val moduleContext = try {
            context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
        } catch (e: Throwable) {
            logError(module, "module context unavailable: ${e.message}")
            return
        }

        val header = headerClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        setLongField(header, "id", HEADER_ID)
        setObjectField(header, "title", context.packageManager.getApplicationLabel(moduleContext.applicationInfo))
        setIntField(header, "iconRes", ICON_FAKE_RES_ID)
        setObjectField(header, "intent", createLaunchIntent())

        headers.add(findInsertPosition(context, headers).coerceIn(0, headers.size), header)
        log(module, "inserted HyperIsland settings entry")
    }

    private fun installIconHook(module: XposedModule) {
        if (iconHookInstalled) return

        val methods = Resources::class.java.declaredMethods.filter { method ->
            method.name == "getDrawable" &&
                method.parameterTypes.isNotEmpty() &&
                method.parameterTypes[0] == Int::class.javaPrimitiveType
        }

        methods.forEach { method ->
            module.hook(method).intercept { chain ->
                val resId = chain.args.getOrNull(0) as? Int
                if (resId == ICON_FAKE_RES_ID) {
                    loadModuleIcon(chain.thisObject as? Resources)
                } else {
                    chain.proceed()
                }
            }
        }

        iconHookInstalled = methods.isNotEmpty()
        log(module, "hooked Resources.getDrawable for settings entry icon")
    }

    private fun loadModuleIcon(resources: Resources?): Drawable? = try {
        val iconStyle = ConfigManager.getString(PREF_ICON_STYLE, ICON_STYLE_DEFAULT)
        moduleIconStates[iconStyle]?.newDrawable() ?: run {
            val context = HookUtils.getContext(ClassLoader.getSystemClassLoader())
                ?.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
            val iconName = if (iconStyle == ICON_STYLE_OUTLINE) {
                "ic_settings_entry_outline"
            } else {
                "ic_settings_entry"
            }
            val iconId = context?.resources?.getIdentifier(iconName, "drawable", MODULE_PACKAGE) ?: 0
            if (context != null && iconId != 0) {
                context.resources.getDrawable(iconId, null)?.also {
                    it.constantState?.let { state -> moduleIconStates[iconStyle] = state }
                }
            } else {
                resources?.getDrawable(android.R.drawable.ic_dialog_info, null)
            }
        }
    } catch (_: Throwable) {
        resources?.getDrawable(android.R.drawable.ic_dialog_info, null)
    }

    private const val PREF_ICON_STYLE = "pref_settings_home_entry_icon_style"
    private const val ICON_STYLE_DEFAULT = "default"
    private const val ICON_STYLE_OUTLINE = "outline"

    private fun createLaunchIntent(): Intent = Intent().apply {
        setClassName(MODULE_PACKAGE, MAIN_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra("isDisplayHomeAsUpEnabled", true)
    }

    private fun findInsertPosition(context: Context, headers: List<Any>): Int {
        val res = context.resources
        val packageName = context.packageName
        val anchors = intArrayOf(
            res.getIdentifier("my_device", "id", packageName),
            res.getIdentifier("launcher_settings", "id", packageName),
            res.getIdentifier("other_special_feature_settings", "id", packageName),
            res.getIdentifier("app_timer", "id", packageName)
        ).filter { it != 0 }.map { it.toLong() }.toSet()

        headers.forEachIndexed { index, header ->
            if (readLongField(header, "id") in anchors) return index + 1
        }

        return if (headers.size > 25) 25 else headers.size
    }

    private fun readLongField(target: Any, name: String): Long = try {
        target.javaClass.getFieldInHierarchy(name).getLong(target)
    } catch (_: Throwable) {
        Long.MIN_VALUE
    }

    private fun setLongField(target: Any, name: String, value: Long) {
        target.javaClass.getFieldInHierarchy(name).setLong(target, value)
    }

    private fun setIntField(target: Any, name: String, value: Int) {
        target.javaClass.getFieldInHierarchy(name).setInt(target, value)
    }

    private fun setObjectField(target: Any, name: String, value: Any?) {
        target.javaClass.getFieldInHierarchy(name).set(target, value)
    }

    private fun Class<*>.getFieldInHierarchy(name: String): java.lang.reflect.Field {
        var cls: Class<*>? = this
        while (cls != null) {
            try {
                return cls.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw NoSuchFieldException(name)
    }
}
