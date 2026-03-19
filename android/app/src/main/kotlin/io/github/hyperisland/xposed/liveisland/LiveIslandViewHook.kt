package io.github.hyperisland.xposed.liveisland

import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 活跃岛视图增强 Hook（SystemUI 进程）。
 *
 * 在 SystemUI 创建超级岛大岛视图时注入，将文本区域替换为支持平滑滚动和切换动画的
 * 自定义实现。
 *
 * ## 工作原理
 * 1. 在 SystemUI 启动时 Hook 岛模板工厂的视图创建方法
 * 2. 视图创建完成后（afterHookedMethod）检查 [LiveIslandState] 中是否有待展示内容
 * 3. 若有，则遍历视图树定位文本视图并注入 [ScrollingTextEngine] 与 [ContentSwitchAnimator]
 *
 * ## 容错策略
 * - 优先 Hook 主要目标方法，失败后依次尝试备用 Hook 点
 * - 任意步骤出错均不影响正常岛通知展示（仅静默降级）
 *
 * ## 注意
 * 需要在 assets/xposed_init 中注册：
 * `io.github.hyperisland.xposed.liveisland.LiveIslandViewHook`
 */
class LiveIslandViewHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HyperIsland[LiveIslandHook]"

        /**
         * 当前正在显示的岛对应的滚动引擎，用于内容更新时执行切换动画。
         * WeakHashMap 避免持有 View 强引用导致内存泄漏。
         */
        private val activeEngines = java.util.WeakHashMap<TextView, ScrollingTextEngine>()

        /** 已尝试注册但尚未找到 Hook 目标的次数，避免无限重试 */
        @Volatile private var hookAttempted = false
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        if (hookAttempted) return
        hookAttempted = true

        // 依次尝试多个已知的岛视图创建入口，找到一个成功即止
        val hooked = tryHookPrimaryTarget(lpparam)
                  || tryHookFallbackTarget(lpparam)
        if (!hooked) {
            XposedBridge.log("$TAG: all hook attempts failed, live island unavailable")
        }
    }

    // ── Hook 目标（按优先级） ──────────────────────────────────────────────────

    /**
     * 主要目标：岛模板工厂的大岛视图创建方法。
     * 类名参考 HyperLyric 项目对 MIUI/HyperOS SystemUI 的分析。
     */
    private fun tryHookPrimaryTarget(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        return hookMethod(
            lpparam        = lpparam,
            className      = "com.miui.systemui.notification.focus.IslandTemplateFactory",
            methodName     = "createBigIslandTemplateView",
            parameterTypes = arrayOf(Context::class.java),
            tag            = "IslandTemplateFactory.createBigIslandTemplateView",
        )
    }

    /**
     * 备用目标：大岛区域视图的内容设置方法。
     * 不同 MIUI/HyperOS 版本的类名可能有差异，此处枚举多个候选。
     */
    private fun tryHookFallbackTarget(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        val candidates = listOf(
            "com.miui.systemui.notification.focus.BigIslandAreaView"  to "updateContent",
            "com.miui.systemui.notification.focus.IslandBigAreaView"  to "setContent",
            "com.miui.systemui.notification.focus.FocusNotifBigView"  to "bindData",
        )
        return candidates.any { (className, methodName) ->
            hookMethod(
                lpparam        = lpparam,
                className      = className,
                methodName     = methodName,
                parameterTypes = emptyArray(),
                tag            = "$className.$methodName",
            )
        }
    }

    // ── Hook 注册工具 ─────────────────────────────────────────────────────────

    private fun hookMethod(
        lpparam: XC_LoadPackage.LoadPackageParam,
        className: String,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        tag: String,
    ): Boolean {
        return try {
            XposedHelpers.findAndHookMethod(
                className,
                lpparam.classLoader,
                methodName,
                *parameterTypes,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        onIslandViewReady(param.result ?: param.thisObject)
                    }
                }
            )
            XposedBridge.log("$TAG: hooked $tag")
            true
        } catch (e: Throwable) {
            XposedBridge.log("$TAG: hook failed for $tag: ${e.message}")
            false
        }
    }

    // ── 视图注入核心逻辑 ──────────────────────────────────────────────────────

    /**
     * Hook 触发后的入口：检查是否有待注入内容，若有则注入滚动与动画。
     * @param viewObj afterHookedMethod 的返回值或 thisObject（视 Hook 点而定）
     */
    private fun onIslandViewReady(viewObj: Any?) {
        val root = viewObj as? ViewGroup ?: return

        val content = LiveIslandState.consume() ?: return

        try {
            injectIntoView(root, content)
        } catch (e: Exception) {
            XposedBridge.log("$TAG: injection error: ${e.message}")
        }
    }

    /**
     * 在岛视图树中定位并增强文本区域。
     *
     * 优先按文本内容匹配（精准），其次按位置顺序取（兜底）。
     * 最多处理两个文本视图：主标题（title）和副标题（subtitle）。
     */
    private fun injectIntoView(
        root: ViewGroup,
        content: LiveIslandState.DisplayContent,
    ) {
        val allTextViews = collectTextViews(root)
        if (allTextViews.isEmpty()) {
            XposedBridge.log("$TAG: no TextViews found in island view")
            return
        }

        // 优先找到已含有目标文本的 TextView（IslandDispatcher 已写入 title/content）
        val titleView = allTextViews.firstOrNull { it.text.toString() == content.title }
            ?: allTextViews.getOrNull(0)
        val subtitleView = if (content.subtitle.isNotEmpty() && content.subtitle != content.title) {
            allTextViews.firstOrNull { it.text.toString() == content.subtitle }
                ?: allTextViews.getOrNull(1)
        } else {
            null
        }

        titleView?.let { setupScrollingView(it, content.title) }
        subtitleView?.let { setupScrollingView(it, content.subtitle) }

        XposedBridge.log(
            "$TAG: injected — title='${content.title}' titleView=${titleView != null}" +
            " subtitleView=${subtitleView != null}"
        )
    }

    /**
     * 为单个 [TextView] 配置滚动引擎。
     * 若该 View 已有活跃引擎（内容更新场景），执行切换动画后重新滚动。
     */
    private fun setupScrollingView(view: TextView, text: String) {
        val existingEngine = activeEngines[view]

        if (existingEngine != null && view.text.toString() != text) {
            // 内容更新：动画切换后重新滚动
            ContentSwitchAnimator.animateChange(
                view         = view,
                newText      = text,
                scrollEngine = existingEngine,
            )
        } else {
            // 首次注入：直接设置并启动滚动
            view.text = text
            val engine = ScrollingTextEngine(view)
            activeEngines[view] = engine
            engine.start()
        }
    }

    // ── 视图树工具 ────────────────────────────────────────────────────────────

    /**
     * 深度优先遍历 [root] 的视图树，收集所有 [TextView]（排除空文本）。
     */
    private fun collectTextViews(root: ViewGroup): List<TextView> {
        val result = mutableListOf<TextView>()
        collectTextViewsRecursive(root, result)
        return result
    }

    private fun collectTextViewsRecursive(group: ViewGroup, out: MutableList<TextView>) {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is TextView  -> if (child.text.isNotEmpty()) out.add(child)
                is ViewGroup -> collectTextViewsRecursive(child, out)
            }
        }
    }
}
