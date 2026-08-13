package io.github.hyperisland.xposed.utils

import android.content.Context
import android.graphics.drawable.Icon

/**
 * 在 Xposed 进程（如 SystemUI）中，[Context] 属于宿主进程，不包含本模块的资源。
 * 此函数通过 [Context.createPackageContext] 创建一个包含模块资源的上下文，
 * 使 [Context.getString] 能正确按设备语言加载模块的字符串资源。
 * 在普通 App 进程中 context 已经是模块自身，无需特殊处理，异常时直接返回 this。
 */
internal fun Context.moduleContext(): Context = try {
    createPackageContext("io.github.hyperisland", Context.CONTEXT_IGNORE_SECURITY)
} catch (_: Exception) {
    this
}

/** 圆角由 SystemUI 的 island_icon_radius 和 ViewOutlineProvider 统一处理。 */
fun Icon.toRounded(@Suppress("UNUSED_PARAMETER") context: Context): Icon = this
