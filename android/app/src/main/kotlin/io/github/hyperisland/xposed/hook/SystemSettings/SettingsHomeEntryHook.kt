package io.github.hyperisland.xposed.hook

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import android.os.ParcelFileDescriptor
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object SettingsHomeEntryHook : BaseHook() {

    private const val MODULE_PACKAGE = "io.github.hyperisland"
    private const val MAIN_ACTIVITY = "io.github.hyperisland.MainActivity"
    private const val HEADER_ID = 0x68797065726C // "hyperl"
    private val resourceLoaderInstalled = AtomicBoolean(false)

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

        installModuleResources(context, moduleContext, module)

        val header = headerClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        setLongField(header, "id", HEADER_ID)
        setObjectField(header, "title", context.packageManager.getApplicationLabel(moduleContext.applicationInfo))
        setIntField(header, "iconRes", moduleContext.resources.getIdentifier("ic_settings_entry", "drawable", MODULE_PACKAGE))
        setObjectField(header, "intent", createLaunchIntent())

        headers.add(findInsertPosition(context, headers).coerceIn(0, headers.size), header)
        log(module, "inserted HyperIsland settings entry")
    }

    private fun installModuleResources(context: Context, moduleContext: Context, module: XposedModule) {
        if (!resourceLoaderInstalled.compareAndSet(false, true)) return

        try {
            val sourceDir = moduleContext.applicationInfo.sourceDir ?: return
            val apk = ParcelFileDescriptor.open(File(sourceDir), ParcelFileDescriptor.MODE_READ_ONLY)
            val provider = ResourcesProvider.loadFromApk(apk)
            val loader = ResourcesLoader().apply { addProvider(provider) }
            context.resources.addLoaders(loader)
            log(module, "installed module ResourcesLoader")
        } catch (e: Throwable) {
            resourceLoaderInstalled.set(false)
            logError(module, "install module resources failed: ${e.message}")
        }
    }

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
