package io.github.hyperisland.data

import android.content.Context
import org.luckypray.dexkit.DexKitBridge

/**
 * 扫描本 APK 里所有 `BaseHook` 子类，供「Hook 管理」页面列出。
 *
 * 这样新增 Hook 不需要在任何名单里登记：只要它还继承 `BaseHook`，就会自动出现。
 * 用 DexKit 读 dex 而不是 `Class.forName`，因为 libxposed api 是 `compileOnly`，
 * App 进程并没有那些类，真正加载 hook 类只会白白触发类解析。
 *
 * 结果按 APK 缓存（同一安装包内不会变）。
 */
object HookCatalog {

    private const val BASE_HOOK = "io.github.hyperisland.xposed.hook.BaseHook"
    private const val BASE_HOOK_SIMPLE_NAME = "BaseHook"
    private const val DEXKIT_LIBRARY = "dexkit"

    @Volatile
    private var cached: List<String>? = null

    /** 可能耗时数百毫秒，务必在 IO 线程调用。 */
    fun load(context: Context): List<String> {
        cached?.let { return it }
        val names = scan(context)
        if (names.isNotEmpty()) cached = names
        return names
    }

    private fun scan(context: Context): List<String> = runCatching {
        System.loadLibrary(DEXKIT_LIBRARY)
        val apkPath = context.applicationInfo.sourceDir
        DexKitBridge.create(apkPath).use { bridge ->
            bridge.findClass {
                matcher { superClass(BASE_HOOK) }
            }.mapNotNull { it.simpleName }
                .filter { it.isNotBlank() && it != BASE_HOOK_SIMPLE_NAME }
                .distinct()
                .sorted()
        }
    }.getOrDefault(emptyList())
}
