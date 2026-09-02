package io.github.hyperisland.xposed.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Bridges HyperOS' custom clipboard-read prompt into the standard Toast pipeline.
 *
 * Xiaomi creates this prompt as a touchable custom View in the LBE permission process,
 * so SystemUI's normal Toast hook cannot observe it. The bridge emits an ordinary Toast
 * with an invisible marker; [ToastUiInterceptHook] consumes that marker in SystemUI,
 * forwards the text through the existing IslandDispatcher path, and blocks the bridge Toast.
 */
object LbeClipboardToastHook : BaseHook() {

    private const val TAG = "HyperIsland[LbeClipboardToast]"
    private const val TARGET_CLASS = "com.lbe.security.utility.ToastUtil"
    private const val TARGET_METHOD = "initToastView"
    private const val PREF_ENABLED = "pref_lbe_clipboard_toast_island"

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (!ConfigManager.getBoolean(PREF_ENABLED, false)) return

        val clazz = try {
            param.defaultClassLoader.loadClass(TARGET_CLASS)
        } catch (e: Throwable) {
            logWarn(module, "$TARGET_CLASS not found: ${e.message}")
            return
        }

        val methods = clazz.declaredMethods.filter { method ->
            method.name == TARGET_METHOD &&
                method.parameterCount == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType
        }
        if (methods.isEmpty()) {
            logWarn(module, "$TARGET_CLASS#$TARGET_METHOD(String, int) not found")
            return
        }

        methods.forEach { method ->
            try {
                module.hook(method).intercept { chain ->
                    val original = chain.proceed()
                    val type = chain.args.getOrNull(1) as? Int ?: return@intercept original
                    if (type != LbeClipboardToastBridge.CLIPBOARD_TYPE) {
                        return@intercept original
                    }

                    val originalView = original as? View
                    val context = originalView?.context
                        ?: HookUtils.getContext(param.defaultClassLoader)
                        ?: return@intercept original
                    val text = resolvePromptText(
                        rawText = chain.args.getOrNull(0) as? String,
                        root = originalView,
                    )
                    if (text.isBlank()) return@intercept original

                    postBridgeToast(context, text, module)
                    suppressOriginalView(originalView)
                    createZeroSizedReplacement(context)
                }
                log(module, "hooked $TARGET_CLASS#$TARGET_METHOD")
            } catch (e: Throwable) {
                logError(module, "hook $TARGET_METHOD failed: ${e.message}")
            }
        }
    }

    private fun postBridgeToast(context: Context, text: String, module: XposedModule) {
        val appContext = context.applicationContext ?: context
        Handler(Looper.getMainLooper()).post {
            runCatching {
                Toast.makeText(
                    appContext,
                    LbeClipboardToastBridge.MARKER + text,
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { error ->
                logError(module, "emit bridge Toast failed: ${error.message}")
            }
        }
    }

    private fun resolvePromptText(rawText: String?, root: View?): String {
        val closeButtonId = root?.context?.resources
            ?.getIdentifier("closeButton", "id", "android")
            ?: View.NO_ID
        val candidates = buildList {
            collectText(root, closeButtonId, this)
            rawText?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        }
        return candidates
            .distinct()
            .maxByOrNull { it.length }
            .orEmpty()
    }

    private fun collectText(view: View?, closeButtonId: Int, output: MutableList<String>) {
        when (view) {
            is TextView -> if (view.id != closeButtonId) {
                view.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(output::add)
            }
            is ViewGroup -> for (index in 0 until view.childCount) {
                collectText(view.getChildAt(index), closeButtonId, output)
            }
        }
    }

    private fun suppressOriginalView(view: View?) {
        view ?: return
        view.alpha = 0f
        view.visibility = View.GONE
        view.isClickable = false
        view.isFocusable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun createZeroSizedReplacement(context: Context): View = View(context).apply {
        alpha = 0f
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        layoutParams = ViewGroup.LayoutParams(0, 0)
    }
}

internal object LbeClipboardToastBridge {
    const val LBE_PACKAGE = "com.lbe.security.miui"
    const val CLIPBOARD_TYPE = 1

    // Unicode formatting characters: invisible when the SystemUI scope is missing,
    // but stable through Toast's Binder transport and unlikely to collide with app text.
    const val MARKER = "\u2060\u2061\u2062\u2063"
}
